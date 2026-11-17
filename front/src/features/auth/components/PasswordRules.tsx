import { passwordChecks } from '../passwordRules'

// Figma 03-2·06·11-1: 입력 칸 아래 회색 상자에 ✓ 조건 목록 (충족하면 초록, 아니면 회색 점)
export function PasswordRules({ password, current }: { password: string; current?: string }) {
  return (
    <ul
      className="mt-3 flex flex-col gap-1 rounded-input bg-bg px-4 py-3"
      aria-label="비밀번호 조건"
    >
      {passwordChecks(password, current).map((check) => (
        <li
          key={check.label}
          className={[
            'flex items-center gap-2 text-small',
            check.ok ? 'text-success' : 'text-text-sub',
          ].join(' ')}
        >
          <span aria-hidden="true">{check.ok ? '✓' : '•'}</span>
          {check.label}
        </li>
      ))}
    </ul>
  )
}
