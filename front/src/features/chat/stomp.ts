import { Client, type IMessage, type StompSubscription } from '@stomp/stompjs'
import { refreshAccessToken } from '../../api/client'
import { useAuthStore } from '../auth/store'

export function resolveStompBrokerUrl(): string {
  const apiBase = import.meta.env.VITE_API_BASE_URL?.trim()
  if (apiBase) {
    const normalized = apiBase.replace(/\/+$/, '')
    const wsBase = normalized.startsWith('https://')
      ? `wss://${normalized.slice('https://'.length)}`
      : normalized.startsWith('http://')
        ? `ws://${normalized.slice('http://'.length)}`
        : normalized
    return `${wsBase}/ws`
  }

  const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:'
  return `${protocol}//${window.location.host}/ws`
}

export function createStompClient(options: {
  onConnect: (client: Client) => void
  onDisconnect?: () => void
  onAuthError?: () => void
}): Client {
  let refreshedOnce = false

  const client = new Client({
    brokerURL: resolveStompBrokerUrl(),
    reconnectDelay: 3000,
    heartbeatIncoming: 10000,
    heartbeatOutgoing: 10000,
    beforeConnect: async () => {
      let token = useAuthStore.getState().accessToken
      if (!token) {
        token = await refreshAccessToken()
      }
      client.connectHeaders = token ? { Authorization: `Bearer ${token}` } : {}
    },
    onConnect: () => {
      refreshedOnce = false
      options.onConnect(client)
    },
    onWebSocketClose: () => {
      options.onDisconnect?.()
    },
    onStompError: async () => {
      if (!refreshedOnce) {
        refreshedOnce = true
        const refreshed = await refreshAccessToken()
        if (refreshed) return
      }
      options.onAuthError?.()
    },
  })

  return client
}

export function safeJsonParse<T>(message: IMessage): T | null {
  try {
    return JSON.parse(message.body) as T
  } catch {
    return null
  }
}

export function unsubscribeSafely(sub: StompSubscription | null) {
  try {
    sub?.unsubscribe()
  } catch {
    // 연결이 이미 닫힌 경우 무시
  }
}
