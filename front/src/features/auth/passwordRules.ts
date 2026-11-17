// AUTH-06 ①: 8~20자, 영문·숫자·특수문자 각 1개 이상 (입력하는 동안 바로 표시)
export function passwordChecks(password: string, current?: string) {
  const checks = [
    { label: '8~20자', ok: password.length >= 8 && password.length <= 20 },
    { label: '영문, 숫자 포함', ok: /[A-Za-z]/.test(password) && /[0-9]/.test(password) },
    { label: '특수문자 포함', ok: /[^A-Za-z0-9\s]/.test(password) },
  ]
  if (current !== undefined) {
    checks.push({ label: '현재 비밀번호와 다름', ok: password.length > 0 && password !== current })
  }
  return checks
}

export function isPasswordValid(password: string) {
  return passwordChecks(password).every((c) => c.ok)
}

// AUTH-06 ③: 닉네임 2~10자 한글·영문·숫자
export function isNicknameFormatValid(nickname: string) {
  return /^[가-힣A-Za-z0-9]{2,10}$/.test(nickname)
}

export function isEmailFormatValid(email: string) {
  return /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email.trim())
}
