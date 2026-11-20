import { useState } from 'react'

// 고정 공지 배너 접기 상태 (NTC-04). 공지마다 키가 달라서 고정 공지가 바뀌면 다시 펼쳐진다.
// localStorage를 못 쓰는 환경(사생활 보호 모드 등)에서는 펼친 채로 두고, 이번 화면에서만 기억한다.
function collapseKey(teamId: number, noticeId: number) {
  return `workspace:pinnedCollapsed:${teamId}:${noticeId}`
}

export function readPinnedCollapsed(teamId: number, noticeId: number) {
  try {
    return window.localStorage.getItem(collapseKey(teamId, noticeId)) === 'true'
  } catch {
    return false
  }
}

export function writePinnedCollapsed(teamId: number, noticeId: number, collapsed: boolean) {
  try {
    const key = collapseKey(teamId, noticeId)
    if (collapsed) window.localStorage.setItem(key, 'true')
    else window.localStorage.removeItem(key)
  } catch {
    // 저장하지 못해도 이번 화면에서는 접힌 상태가 유지된다 (아래 overrides)
  }
}

// 배너(펼침)와 탭 바(접힘 시 「고정 공지 펼치기」 버튼)가 같은 값을 쓰도록 레이아웃에서 한 번 부른다.
// noticeId가 null(고정 공지 없음)이면 항상 false.
export function usePinnedCollapsed(teamId: number, noticeId: number | null) {
  // 이번 화면에서 바꾼 값. 키가 공지마다 달라 새 공지는 저장된 값(없으면 펼침)부터 시작한다
  const [overrides, setOverrides] = useState<Record<string, boolean>>({})
  const key = noticeId === null ? null : collapseKey(teamId, noticeId)
  const collapsed =
    key !== null && noticeId !== null && (overrides[key] ?? readPinnedCollapsed(teamId, noticeId))

  const setCollapsed = (next: boolean) => {
    if (key === null || noticeId === null) return
    setOverrides((prev) => ({ ...prev, [key]: next }))
    writePinnedCollapsed(teamId, noticeId, next)
  }

  return [collapsed, setCollapsed] as const
}
