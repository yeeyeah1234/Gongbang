import { useSyncExternalStore } from 'react'

const PHONE_QUERY = '(max-width: 639px)'

function subscribe(listener: () => void) {
  const media = window.matchMedia(PHONE_QUERY)
  media.addEventListener('change', listener)
  return () => media.removeEventListener('change', listener)
}

/** STYLE_GUIDE 8: 휴대폰(<640px) 전용 분기 (헤더 접속자 축소, 화이트보드 보기 전용) */
export function useIsPhone() {
  return useSyncExternalStore(
    subscribe,
    () => window.matchMedia(PHONE_QUERY).matches,
    () => false,
  )
}
