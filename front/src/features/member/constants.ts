// AUTH-07 ① 희망 역할 (DB 명세서 member_desired_role.role_code)
export const DESIRED_ROLES = [
  { code: 'PLANNING', label: '기획' },
  { code: 'DESIGN', label: '디자인' },
  { code: 'FRONTEND', label: '프론트엔드' },
  { code: 'BACKEND', label: '백엔드' },
  { code: 'APP', label: '앱' },
  { code: 'DATA_AI', label: '데이터·AI' },
  { code: 'VIDEO', label: '영상·콘텐츠' },
  { code: 'MARKETING', label: '마케팅' },
  { code: 'PRESENTATION', label: '발표·문서' },
] as const

// AUTH-07 ① 학년 (member.grade)
export const GRADES = [
  { code: 'GRADE_1', label: '1학년' },
  { code: 'GRADE_2', label: '2학년' },
  { code: 'GRADE_3', label: '3학년' },
  { code: 'GRADE_4', label: '4학년' },
  { code: 'GRADUATED', label: '졸업' },
  { code: 'NONE', label: '해당 없음' },
] as const

// MY-01 ③ 기술 태그 최대 10개, 한 줄 소개 50자
export const SKILL_TAG_LIMIT = 10
export const BIO_MAX = 50

export function roleLabel(code: string) {
  return DESIRED_ROLES.find((r) => r.code === code)?.label ?? code
}

export function gradeLabel(code: string | null) {
  return GRADES.find((g) => g.code === code)?.label ?? ''
}

// 2026-03-02T.. → 2026.03.02
export function formatDate(value: string | null | undefined) {
  if (!value) return ''
  return value.slice(0, 10).replaceAll('-', '.')
}

// 2026-03-02 → 2026.03
export function formatMonth(value: string | null | undefined) {
  if (!value) return ''
  return value.slice(0, 7).replace('-', '.')
}
