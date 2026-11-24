import type { AdminItem, Category, Job } from '../../features/admin/operations'

// 이 모듈은 DEV 미리보기에서만 불러온다. 실제 API로 전달하지 않는다.
export function operationFixtures() {
  const rows: Record<string, AdminItem[]> = {
    'service-notices': [
      {
        id: 14,
        title: '[안내] 공방 서비스 운영 공지',
        content: '서비스 점검과 인증 심사 기준을 안내합니다.\n이 화면은 샘플 데이터입니다.',
        createdAt: '2026-11-23T09:00:00',
        deletedAt: null,
      },
      {
        id: 13,
        title: '자격증·수상 인증 안내',
        content: '증빙파일에서 이름과 취득일을 확인할 수 있어야 합니다.',
        createdAt: '2026-11-22T09:00:00',
      },
    ],
    'member-management': [
      {
        id: 501,
        nickname: '샘플회원',
        email: 'sample@example.com',
        role: 'USER',
        status: 'ACTIVE',
        loginType: 'EMAIL',
        loginFailCount: 10,
        lockedUntil: '2026-11-23T23:30:00',
        warningCount: 0,
        createdAt: '2026-10-01T10:00:00',
      },
      {
        id: 502,
        nickname: '샘플하늘',
        email: 'sky@example.com',
        role: 'USER',
        status: 'ACTIVE',
        loginFailCount: 0,
        warningCount: 1,
        createdAt: '2026-09-01T10:00:00',
      },
    ],
    reports: [
      {
        id: 42,
        reporterId: 501,
        targetType: 'MEMBER',
        targetId: 502,
        reason: '부적절한 닉네임 신고입니다. 내용을 확인해 주세요.',
        status: 'RECEIVED',
        createdAt: '2026-11-23T14:30:00',
      },
      {
        id: 41,
        reporterId: 502,
        targetType: 'POST',
        targetId: 71,
        reason: '모집 내용과 무관한 광고가 포함되어 있습니다.',
        status: 'RECEIVED',
        createdAt: '2026-11-23T13:00:00',
      },
    ],
    posts: [
      {
        id: 71,
        title: '공모전 팀원을 모집합니다',
        content: '기획·개발 분야 팀원을 모집합니다.\n팀원들과 일정을 조율해 프로젝트를 진행합니다.',
        authorId: 501,
        status: 'RECRUITING',
        reportCount: 2,
        createdAt: '2026-11-21T10:00:00',
      },
      {
        id: 70,
        title: '디자인 공모전 함께할 팀원',
        content: '함께 성장할 팀원을 찾습니다.',
        authorId: 502,
        status: 'COMPLETED',
        reportCount: 0,
        createdAt: '2026-11-20T10:00:00',
      },
    ],
    contests: [
      {
        id: 91,
        title: '2026 공방 아이디어 공모전',
        endDate: '2026-12-20',
        categoryId: 1,
        categoryName: '기획·아이디어',
        sourceUrl: 'https://example.com',
      },
    ],
  }
  const categories: Category[] = [
    { id: 1, code: 'PLANNING', name: '기획·아이디어', sortOrder: 1 },
    { id: 2, code: 'IT', name: 'IT·소프트웨어', sortOrder: 2 },
  ]
  const jobs: Job[] = [
    {
      id: 5,
      status: 'SUCCEEDED',
      createdAt: '2026-11-23T04:00:00',
      startedAt: '2026-11-23T04:00:10',
      finishedAt: '2026-11-23T04:02:10',
    },
  ]
  return (
    url: string,
    method: string,
    body: Record<string, unknown> | undefined,
    params?: Record<string, unknown>,
  ): unknown => {
    const [, , , resource, idText, action] = url.split('/')
    const id = Number(idText)
    if (resource === 'members' && action === 'admin-role') {
      const member = rows['member-management'].find((row) => row.id === id)
      if (!member || member.status !== 'ACTIVE')
        throw new Error('활성 회원만 관리자로 지정할 수 있습니다.')
      member.role = 'ADMIN'
      return { ...member }
    }
    if (resource === 'categories') {
      if (method === 'patch') Object.assign(categories.find((c) => c.id === id) ?? {}, body)
      return categories.map((c) => ({ ...c }))
    }
    if (resource === 'crawler-jobs') {
      if (method === 'post')
        jobs.unshift({
          id: jobs.length + 6,
          status: 'PENDING',
          createdAt: new Date().toISOString(),
          startedAt: null,
          finishedAt: null,
        })
      return jobs.map((j) => ({ ...j }))
    }
    if (resource === 'contests' && action === 'teams')
      return Array.from({ length: 6 }, (_, i) => ({ id: i + 1, name: `샘플 팀 ${i + 1}` }))
    if (resource === 'posts' && action === 'comments') return []
    const list = rows[resource]
    if (!list) throw new Error('지원하지 않는 미리보기 요청입니다.')
    const row = list.find((r) => r.id === id)
    if (row && method === 'get')
      return resource === 'reports'
        ? { report: { ...row }, memberId: 502, content: '신고 대상 원문 미리보기입니다.' }
        : { ...row }
    if (method !== 'get') {
      if (resource === 'service-notices' && method === 'post')
        list.unshift({
          id: Math.max(...list.map((r) => r.id)) + 1,
          title: String(body?.title),
          content: String(body?.content),
          createdAt: new Date().toISOString(),
        })
      else if (row) {
        if (method === 'delete' || action === 'hide') {
          row.deletedAt = new Date().toISOString()
          if (resource === 'posts') row.status = 'CLOSED'
        } else if (action === 'review') {
          row.status = body?.action === 'DISMISS' ? 'DISMISSED' : 'RESOLVED'
          row.reviewNote = String(body?.reason)
          row.reviewerId = 1
          row.reviewedAt = new Date().toISOString()
        } else if (action === 'actions') {
          if (body?.action === 'GRANT_ADMIN')
            throw new Error('관리자 권한 부여는 허용되지 않습니다.')
          if (body?.action === 'UNLOCK') {
            row.loginFailCount = 0
            row.lockedUntil = null
          }
          if (body?.action === 'WARN') row.warningCount = (row.warningCount ?? 0) + 1
          if (body?.action === 'SUSPEND') row.suspendedUntil = String(body.until)
          if (body?.action === 'UNSUSPEND') row.suspendedUntil = null
          if (body?.action === 'REQUIRE_NICKNAME') row.nicknameChangeRequired = true
        } else Object.assign(row, body)
      }
      return row ? { ...row } : null
    }
    const keyword = String(params?.keyword ?? '').toLowerCase()
    const content = list.filter(
      (r) =>
        (!params?.status || r.status === params.status) &&
        (!params?.type || r.targetType === params.type) &&
        `${r.title ?? ''} ${r.email ?? ''} ${r.nickname ?? ''}`.toLowerCase().includes(keyword),
    )
    return {
      content: content.map((r) => ({ ...r })),
      totalElements: content.length,
      totalPages: 1,
      number: 0,
    }
  }
}
