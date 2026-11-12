import { useCallback, useEffect, useRef, useState } from 'react'

// STYLE_GUIDE 5-8: 토스트는 2.5초 뒤 사라진다.
// 공통 Toast(components/ui/Toast)는 문구만 받아서 보여 주므로, 띄우고 지우는 시간은 여기서 관리한다.
const TOAST_DURATION_MS = 2500

export function useTimedToast() {
  const [message, setMessage] = useState<string | null>(null)
  const timerRef = useRef<number | null>(null)

  useEffect(
    () => () => {
      if (timerRef.current !== null) window.clearTimeout(timerRef.current)
    },
    [],
  )

  const show = useCallback((text: string) => {
    setMessage(text)
    if (timerRef.current !== null) window.clearTimeout(timerRef.current)
    timerRef.current = window.setTimeout(() => setMessage(null), TOAST_DURATION_MS)
  }, [])

  return { message, show }
}
