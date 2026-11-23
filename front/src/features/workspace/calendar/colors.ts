import type { CalendarEventData, EventColor } from './types'

// STYLE_GUIDE 1-3: 일정 막대는 그 색을 왼쪽 막대에, 같은 색 22% 배경을 막대 배경에 쓴다.
// 글자는 배경 위에서 읽히게 그 색을 20% 어둡게 (토큰은 그대로 두고 여기서만 섞는다).
// 노랑은 글자만 yellow-text. 자동 일정(CAL-06)은 danger 배경 + 흰 글자.
// Tailwind가 찾을 수 있게 클래스는 모두 정적 문자열로 적는다.
interface ColorClasses {
  label: string
  // 막대·카드 배경과 글자
  soft: string
  // 왼쪽 막대, 색 고르기 원
  solid: string
}

export const EVENT_COLORS: ReadonlyArray<{ value: EventColor } & ColorClasses> = [
  {
    value: 'BLUE',
    label: '파랑',
    soft: 'bg-event-blue/22 text-[color-mix(in_oklab,var(--color-event-blue),black_20%)]',
    solid: 'bg-event-blue',
  },
  {
    value: 'GREEN',
    label: '초록',
    soft: 'bg-event-green/22 text-[color-mix(in_oklab,var(--color-event-green),black_20%)]',
    solid: 'bg-event-green',
  },
  {
    value: 'YELLOW',
    label: '노랑',
    soft: 'bg-event-yellow/22 text-event-yellow-text',
    solid: 'bg-event-yellow',
  },
  {
    value: 'PURPLE',
    label: '보라',
    soft: 'bg-event-purple/22 text-[color-mix(in_oklab,var(--color-event-purple),black_20%)]',
    solid: 'bg-event-purple',
  },
  {
    value: 'GRAY',
    label: '회색',
    soft: 'bg-event-gray/22 text-[color-mix(in_oklab,var(--color-event-gray),black_20%)]',
    solid: 'bg-event-gray',
  },
]

const AUTO_CLASSES: ColorClasses = {
  label: '자동',
  soft: 'bg-danger text-surface',
  solid: 'bg-danger',
}

export function isAutoEvent(event: CalendarEventData) {
  return event.autoType !== null
}

// 모르는 색은 기본 파랑으로 그린다
export function eventColorClasses(event: CalendarEventData): ColorClasses {
  if (isAutoEvent(event)) return AUTO_CLASSES
  return EVENT_COLORS.find((color) => color.value === event.color) ?? EVENT_COLORS[0]
}
