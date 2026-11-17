// AUTH-04 ④ / Figma 04~07: 1~4단계 진행 표시 (약관 → 이메일 인증 → 계정 정보 → 프로필 입력)
const STEPS = ['약관 동의', '이메일 인증', '계정 정보', '프로필 입력']

export function StepIndicator({ current }: { current: number }) {
  return (
    <ol className="flex items-center" aria-label="회원가입 단계">
      {STEPS.map((label, index) => {
        const step = index + 1
        const done = step < current
        const active = step === current
        return (
          <li key={label} className="flex flex-1 items-center last:flex-none">
            <span className="flex items-center gap-2" aria-current={active ? 'step' : undefined}>
              <span
                className={[
                  'flex size-5 items-center justify-center rounded-full text-caption font-bold',
                  done || active ? 'bg-primary text-surface' : 'bg-surface-sub text-text-sub',
                ].join(' ')}
              >
                {done ? '✓' : step}
              </span>
              <span
                className={[
                  'text-caption whitespace-nowrap',
                  active ? 'font-bold text-text' : 'text-text-sub',
                ].join(' ')}
              >
                {label}
              </span>
            </span>
            {step < STEPS.length && (
              <span className="mx-3 h-px flex-1 bg-line" aria-hidden="true" />
            )}
          </li>
        )
      })}
    </ol>
  )
}
