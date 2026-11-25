import { HocuspocusProvider, type HocuspocusProviderWebsocket } from '@hocuspocus/provider'
import { useEffect, useMemo, useRef, useState } from 'react'
import * as Y from 'yjs'
import { refreshAccessToken } from '../../api/client'
import type { MemberColorIndex } from '../../components/ui/Avatar'
import { useAuthStore } from '../auth/store'
import { buildSyncTeamName } from './document/syncUrl'
import { extractOnlineMembers } from './onlineMembers'
import type { WorkspaceOnlineMember, WorkspaceTabKey } from './types'
import {
  CLOSE_CODE_TEAM_MEMBER_REMOVED,
  connectSyncSocket,
  isSyncSocketOpen,
  stopSyncSocket,
  suppressSyncSocketReconnect,
} from './useWorkspaceSyncSocket'

const EMPTY_MEMBERS: WorkspaceOnlineMember[] = []

interface PresenceLocalState {
  memberId: number
  nickname: string
  colorIndex: MemberColorIndex
  leader: boolean
  tab: WorkspaceTabKey | null
  docTitle: string | null
}

interface UseWorkspacePresenceOptions {
  teamId: number
  enabled: boolean
  syncSocket: HocuspocusProviderWebsocket | null
  memberId: number | null
  nickname: string | null
  colorIndex: MemberColorIndex | null
  leader: boolean
  tab: WorkspaceTabKey | null
  docTitle: string | null
  onForbidden?: () => void
}

function isSameLocalState(a: PresenceLocalState | null, b: PresenceLocalState | null): boolean {
  if (a === b) return true
  if (!a || !b) return false
  return (
    a.memberId === b.memberId &&
    a.nickname === b.nickname &&
    a.colorIndex === b.colorIndex &&
    a.leader === b.leader &&
    a.tab === b.tab &&
    a.docTitle === b.docTitle
  )
}

function isSameMemberList(a: WorkspaceOnlineMember[], b: WorkspaceOnlineMember[]): boolean {
  if (a.length !== b.length) return false
  for (let i = 0; i < a.length; i += 1) {
    const left = a[i]
    const right = b[i]
    if (
      left.memberId !== right.memberId ||
      left.nickname !== right.nickname ||
      left.colorIndex !== right.colorIndex ||
      left.isLeader !== right.isLeader ||
      left.tab !== right.tab ||
      left.docTitle !== right.docTitle
    ) {
      return false
    }
  }
  return true
}

/**
 * WS-03 워크스페이스 접속자 실시간 동기화 훅.
 *
 * - WorkspaceLayout에서 팀당 HocuspocusProvider("team:{teamId}") 1개만 연다.
 * - 문서 동기화(useDocumentSync.ts)와 같은 서버 주소·preserveTrailingSlash·토큰 갱신 방식을 쓴다.
 * - 연결 전이거나 실패하면 빈 배열을 돌려줘 헤더가 기존처럼 나만 표시하게 한다.
 * - WS-03 ⑤ 끊김 시간:
 *   - 정상 이탈(탭 닫기·라우트 이동·팀 변경·강퇴)은 pagehide/cleanup의 setLocalState(null)과
 *     WebSocket close를 통해 즉시 제거된다.
 *   - 비정상 단절(네트워크 갑작스런 끊김 등)은 명세(10초)와 달리 라이브러리 기본값(y-protocols
 *     outdatedTimeout 30초, Hocuspocus 서버 timeout 60초)을 유지한다. 서버 timeout을 10초로 줄이면
 *     15초마다 awareness를 갱신하는 정상 유휴 클라이언트까지 끊기기 때문이다.
 */
export function useWorkspacePresence({
  teamId,
  enabled,
  syncSocket,
  memberId,
  nickname,
  colorIndex,
  leader,
  tab,
  docTitle,
  onForbidden,
}: UseWorkspacePresenceOptions): WorkspaceOnlineMember[] {
  const [presenceState, setPresenceState] = useState<{
    teamId: number
    members: WorkspaceOnlineMember[]
  }>({ teamId, members: EMPTY_MEMBERS })

  const providerRef = useRef<HocuspocusProvider | null>(null)
  const onForbiddenRef = useRef(onForbidden)
  const lastSentStateRef = useRef<PresenceLocalState | null>(null)

  const desiredLocalState = useMemo<PresenceLocalState | null>(
    () =>
      memberId !== null && nickname !== null && colorIndex !== null
        ? {
            memberId,
            nickname,
            colorIndex,
            leader,
            tab,
            docTitle: tab === 'docs' || tab === 'board' ? docTitle : null,
          }
        : null,
    [memberId, nickname, colorIndex, leader, tab, docTitle],
  )

  const desiredLocalStateRef = useRef<PresenceLocalState | null>(desiredLocalState)
  useEffect(() => {
    desiredLocalStateRef.current = desiredLocalState
  }, [desiredLocalState])

  useEffect(() => {
    onForbiddenRef.current = onForbidden
  }, [onForbidden])

  // 팀당 1개의 공유 Hocuspocus 소켓에 team:{teamId} provider만 붙이고 정리한다
  useEffect(() => {
    if (!enabled || !syncSocket || !Number.isSafeInteger(teamId) || teamId <= 0) {
      return
    }

    const ydoc = new Y.Doc()
    const provider = new HocuspocusProvider({
      websocketProvider: syncSocket,
      name: buildSyncTeamName(teamId),
      document: ydoc,
      token: async () => {
        const current = useAuthStore.getState().accessToken
        if (current) return current
        return (await refreshAccessToken()) ?? ''
      },
    })
    providerRef.current = provider
    lastSentStateRef.current = null

    let stopped = false
    let refreshedOnce = false
    let authHandling = false

    const clearMembers = () => {
      setPresenceState((prev) =>
        prev.teamId === teamId && prev.members.length === 0
          ? prev
          : { teamId, members: EMPTY_MEMBERS },
      )
    }

    const stopPermanently = () => {
      stopped = true
      clearMembers()
      if (provider.awareness && provider.awareness.getLocalState() !== null) {
        provider.awareness.setLocalState(null)
      }
      if (provider.isAttached) {
        provider.detach()
      }
      stopSyncSocket(syncSocket)
    }

    const pushLocalState = (force = false) => {
      const awareness = provider.awareness
      const next = desiredLocalStateRef.current
      if (!awareness || !next) return
      if (!force && isSameLocalState(lastSentStateRef.current, next)) return
      lastSentStateRef.current = next
      awareness.setLocalState({
        ...next,
        updatedAt: Date.now(),
      })
    }

    const syncOnlineMembers = () => {
      if (stopped || !provider.awareness) return
      const nextMembers = extractOnlineMembers(
        provider.awareness.getStates() as Map<number, Record<string, unknown>>,
      )
      setPresenceState((prev) => {
        if (prev.teamId === teamId && isSameMemberList(prev.members, nextMembers)) {
          return prev
        }
        return { teamId, members: nextMembers }
      })
    }

    const handleAuthenticated = () => {
      if (stopped) return
      refreshedOnce = false
      pushLocalState(true)
      syncOnlineMembers()
    }

    const handleClose = ({ event }: { event: { code?: number; reason?: string } }) => {
      if (stopped) return
      if (event?.code === CLOSE_CODE_TEAM_MEMBER_REMOVED) {
        stopPermanently()
        onForbiddenRef.current?.()
      }
    }

    const handleAuthFailed = ({ reason }: { reason: string }) => {
      if (stopped || authHandling) return
      authHandling = true

      void (async () => {
        try {
          const upperReason = (reason ?? '').toUpperCase()
          const isExplicit403 = upperReason.includes('HTTP 403') || upperReason.includes('403')
          const isExplicit404 = upperReason.includes('HTTP 404') || upperReason.includes('404')

          if (isExplicit403) {
            stopPermanently()
            onForbiddenRef.current?.()
            return
          }

          if (isExplicit404) {
            stopPermanently()
            return
          }

          // 토큰 재발급 대기 중 onClose가 1초 뒤 미갱신 토큰으로 재연결하지 않도록 먼저 멈춘다
          if (provider.isAttached) {
            provider.detach()
          }
          suppressSyncSocketReconnect(syncSocket)

          if (!refreshedOnce) {
            refreshedOnce = true
            const newToken = await refreshAccessToken()
            if (newToken && !stopped) {
              if (!provider.isAttached) {
                provider.attach()
              }
              if (!isSyncSocketOpen(syncSocket)) {
                connectSyncSocket(syncSocket)
              }
              return
            }
          }

          stopPermanently()
        } finally {
          authHandling = false
        }
      })()
    }

    const handlePageHide = () => {
      provider.awareness?.setLocalState(null)
    }

    provider.on('authenticated', handleAuthenticated)
    provider.on('close', handleClose)
    provider.on('authenticationFailed', handleAuthFailed)
    provider.awareness?.on('change', syncOnlineMembers)
    window.addEventListener('pagehide', handlePageHide)

    pushLocalState(true)
    if (!provider.isAttached) {
      provider.attach()
    }
    connectSyncSocket(syncSocket)

    return () => {
      stopped = true
      window.removeEventListener('pagehide', handlePageHide)
      provider.off('authenticated', handleAuthenticated)
      provider.off('close', handleClose)
      provider.off('authenticationFailed', handleAuthFailed)
      provider.awareness?.off('change', syncOnlineMembers)
      // 팀 전환·이탈 시 다른 팀원 화면에서 내 아바타가 즉시 사라지게 정리한다 (공유 소켓 정리는 useWorkspaceSyncSocket이 담당)
      if (provider.awareness && provider.awareness.getLocalState() !== null) {
        provider.awareness.setLocalState(null)
      }
      provider.destroy()
      ydoc.destroy()
      if (providerRef.current === provider) {
        providerRef.current = null
      }
      lastSentStateRef.current = null
    }
  }, [teamId, enabled, syncSocket])

  // 탭·열린 문서·팀원 색/역할이 실제로 바뀔 때만 awareness 갱신 (입력할 때마다 갱신하지 않는다)
  useEffect(() => {
    const awareness = providerRef.current?.awareness
    if (!awareness || !desiredLocalState) return
    if (isSameLocalState(lastSentStateRef.current, desiredLocalState)) return
    lastSentStateRef.current = desiredLocalState
    awareness.setLocalState({
      ...desiredLocalState,
      updatedAt: Date.now(),
    })
  }, [desiredLocalState])

  if (!enabled || presenceState.teamId !== teamId) {
    return EMPTY_MEMBERS
  }
  return presenceState.members
}
