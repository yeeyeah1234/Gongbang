import { useLayoutEffect, useState } from 'react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { useParams } from 'react-router'
import { apiClient } from '../../api/client'
import { AdminShell } from '../../features/admin/AdminLayout'
import { useLoginMember } from '../../features/auth/store'
import type { Verification } from '../../features/admin/api'
import { VerificationWorkspace } from '../admin/AdminVerificationPage'
import AdminMembersPage from '../admin/AdminMembersPage'
import AdminNoticeManagementPage from '../admin/AdminNoticeManagementPage'
import AdminReportManagementPage from '../admin/AdminReportManagementPage'
import AdminMemberManagementPage from '../admin/AdminMemberManagementPage'
import AdminPostManagementPage from '../admin/AdminPostManagementPage'
import AdminContestManagementPage from '../admin/AdminContestManagementPage'
import { operationFixtures } from './adminOperationFixtures'

// DEV 경로에서만 lazy import한다. 인증 상태를 바꾸지 않고 API 결과를 메모리에서 처리한다.
function installPreview() {
  const operations = operationFixtures()
  const rows: Verification[] = [
    {
      id: 103,
      memberId: 11,
      nickname: '샘플하늘',
      targetType: 'CERTIFICATE',
      targetId: 1,
      title: '정보처리기사',
      description: '한국산업인력공단 · 2026-10-20',
      proofFileId: 1,
      proofAvailable: true,
      status: 'PENDING',
      rejectReason: null,
      reviewerId: null,
      requestedAt: '2026-11-23T10:30:00',
      reviewedAt: null,
    },
    {
      id: 102,
      memberId: 12,
      nickname: '샘플지민',
      targetType: 'AWARD',
      targetId: 2,
      title: '캠퍼스 아이디어 공모전',
      description: '최우수상 · 2026-10-15',
      proofFileId: 2,
      proofAvailable: true,
      status: 'PENDING',
      rejectReason: null,
      reviewerId: null,
      requestedAt: '2026-11-22T14:00:00',
      reviewedAt: null,
    },
    {
      id: 101,
      memberId: 13,
      nickname: '샘플민서',
      targetType: 'CERTIFICATE',
      targetId: 3,
      title: '컴퓨터활용능력 1급',
      description: '대한상공회의소 · 2026-09-10',
      proofFileId: 3,
      proofAvailable: true,
      status: 'VERIFIED',
      rejectReason: null,
      reviewerId: 1,
      requestedAt: '2026-11-20T09:00:00',
      reviewedAt: '2026-11-21T11:00:00',
    },
    {
      id: 100,
      memberId: 14,
      nickname: '샘플도윤',
      targetType: 'AWARD',
      targetId: 4,
      title: '지역 디자인 공모전',
      description: '우수상 · 2026-08-10',
      proofFileId: 4,
      proofAvailable: false,
      status: 'REJECTED',
      rejectReason: '이름과 수상일이 보이는 증빙을 다시 제출해 주세요.',
      reviewerId: 1,
      requestedAt: '2026-11-19T09:00:00',
      reviewedAt: '2026-11-20T11:00:00',
    },
  ]
  const members = [
    { memberId: 1, nickname: '샘플관리자', email: 'admin@example.com', role: 'ADMIN' },
    ...rows.map((row) => ({
      memberId: row.memberId,
      nickname: row.nickname,
      email: `sample${row.memberId}@example.com`,
      role: 'USER',
    })),
  ]
  const id = apiClient.interceptors.request.use((config) => {
    if (!config.url?.startsWith('/api/admin/') && !config.url?.startsWith('/api/files/'))
      return config
    config.adapter = async (request) => {
      const url = request.url ?? ''
      const body = typeof request.data === 'string' ? JSON.parse(request.data) : request.data
      let data: unknown = null
      if (url === '/api/admin/verifications') {
        const content = rows.filter((row) => row.status === request.params?.status)
        data = {
          content: content.map((row) => ({ ...row })),
          totalElements: content.length,
          totalPages: 1,
          number: 0,
        }
      } else if (url.startsWith('/api/admin/verifications/')) {
        const row = rows.find((item) => item.id === Number(url.split('/').pop()))
        if (!row) throw new Error('Missing preview request')
        if (request.method === 'patch') {
          row.status = body.status
          row.rejectReason = body.rejectReason ?? null
          row.reviewerId = 1
          row.reviewedAt = '2026-11-23T15:00:00'
        } else
          data = {
            item: { ...row },
            realName: row.status === 'PENDING' ? '샘플 이름' : null,
            nameAvailable: row.status === 'PENDING',
          }
      } else if (url === '/api/admin/members') {
        const keyword = String(request.params?.keyword ?? '')
        const content = members.filter(
          (m) => m.nickname.includes(keyword) || m.email.includes(keyword),
        )
        data = {
          content: content.map((m) => ({ ...m })),
          totalElements: content.length,
          totalPages: 1,
          number: 0,
        }
      } else if (url.endsWith('/admin-role')) {
        data = operations(url, request.method ?? 'post', body, request.params)
      } else if (url.startsWith('/api/files/')) {
        return {
          data: samplePdf(),
          status: 200,
          statusText: 'OK',
          headers: { 'content-type': 'application/pdf' },
          config: request,
        }
      } else {
        data = operations(url, request.method ?? 'get', body, request.params)
      }
      return {
        data: { success: true, data, error: null },
        status: 200,
        statusText: 'OK',
        headers: {},
        config: request,
      }
    }
    return config
  })
  return () => apiClient.interceptors.request.eject(id)
}

function samplePdf() {
  const stream =
    'BT /F1 20 Tf 40 150 Td (PREVIEW SAMPLE - NOT A REAL CERTIFICATE) Tj 0 -40 Td /F1 12 Tf (Check the review UI. No personal data is stored.) Tj ET'
  const objects = [
    '<< /Type /Catalog /Pages 2 0 R >>',
    '<< /Type /Pages /Kids [3 0 R] /Count 1 >>',
    '<< /Type /Page /Parent 2 0 R /MediaBox [0 0 640 240] /Resources << /Font << /F1 4 0 R >> >> /Contents 5 0 R >>',
    '<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>',
    `<< /Length ${stream.length} >>\nstream\n${stream}\nendstream`,
  ]
  let pdf = '%PDF-1.4\n'
  const offsets = [0]
  objects.forEach((object, index) => {
    offsets.push(pdf.length)
    pdf += `${index + 1} 0 obj\n${object}\nendobj\n`
  })
  const xref = pdf.length
  pdf += `xref\n0 6\n0000000000 65535 f \n${offsets
    .slice(1)
    .map((offset) => `${String(offset).padStart(10, '0')} 00000 n \n`)
    .join('')}trailer\n<< /Size 6 /Root 1 0 R >>\nstartxref\n${xref}\n%%EOF`
  return new Blob([pdf], { type: 'application/pdf' })
}

export default function AdminPreviewPage() {
  const member = useLoginMember()
  const { tab } = useParams()
  const [client] = useState(() => new QueryClient())
  useLayoutEffect(() => installPreview(), [])
  return (
    <QueryClientProvider client={client}>
      <div className="bg-primary-soft px-6 py-3 text-small text-primary">
        관리자 화면 미리보기 · 샘플 데이터입니다. 모든 변경은 브라우저 안에서만 반영되며
        새로고침하면 초기화됩니다.
      </div>
      <AdminShell
        nickname={member?.role === 'ADMIN' ? member.nickname : '관리자'}
        basePath="/dev/admin-preview"
      >
        {tab === 'notices' ? (
          <AdminNoticeManagementPage />
        ) : tab === 'reports' ? (
          <AdminReportManagementPage />
        ) : tab === 'members' ? (
          <AdminMemberManagementPage />
        ) : tab === 'posts' ? (
          <AdminPostManagementPage />
        ) : tab === 'contests' ? (
          <AdminContestManagementPage />
        ) : tab === 'permissions' ? (
          <AdminMembersPage />
        ) : (
          <VerificationWorkspace />
        )}
      </AdminShell>
    </QueryClientProvider>
  )
}
