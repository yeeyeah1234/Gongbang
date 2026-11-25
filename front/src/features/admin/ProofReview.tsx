import { MobileActionBar } from '../../components/layout/MobileActionBar'
import { useEffect, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { apiClient } from '../../api/client'
import type { ApiResponse } from '../../api/types'
import { getErrorMessage } from '../../api/errors'
import { Button } from '../../components/ui/Button'
import { Modal } from '../../components/ui/Modal'
import { reviewVerification, type Verification } from './api'

export function ProofReview({ item }: { item: Verification }) {
  const [url, setUrl] = useState('')
  const [mime, setMime] = useState('')
  const [error, setError] = useState('')
  const [loading, setLoading] = useState(false)
  const [checked, setChecked] = useState(false)
  const [confirm, setConfirm] = useState(false)
  const client = useQueryClient()
  const detail = useQuery({
    queryKey: ['admin-verification-detail', item.id],
    queryFn: async () =>
      (
        await apiClient.get<ApiResponse<{ realName: string | null; nameAvailable: boolean }>>(
          `/api/admin/verifications/${item.id}`,
        )
      ).data.data,
    gcTime: 0,
    staleTime: 0,
    retry: false,
  })
  useEffect(
    () => () => {
      if (url) URL.revokeObjectURL(url)
    },
    [url],
  )
  const approve = useMutation({
    mutationFn: () => reviewVerification(item.id, 'VERIFIED'),
    onSuccess: async () => {
      setConfirm(false)
      await client.invalidateQueries({ queryKey: ['admin-verifications'] })
      client.removeQueries({ queryKey: ['admin-verification-detail', item.id] })
      await client.invalidateQueries({ queryKey: ['members'] })
    },
  })
  async function openProof() {
    setError('')
    setLoading(true)
    try {
      const response = await apiClient.get<Blob>(`/api/files/${item.proofFileId}`, {
        responseType: 'blob',
      })
      if (!['image/png', 'image/jpeg', 'application/pdf'].includes(response.data.type))
        throw new Error('unexpected type')
      setMime(response.data.type)
      setUrl(URL.createObjectURL(response.data))
      setChecked(false)
    } catch {
      setError('증빙 파일을 불러오지 못했어요. 다시 업로드한 증빙인지 확인해 주세요.')
    } finally {
      setLoading(false)
    }
  }
  return (
    <div className="rounded-input border border-line bg-bg p-4">
      <h3 className="text-body font-bold">증빙 확인</h3>
      <p className="my-3 text-small">
        신청 실명:{' '}
        {detail.isPending ? '확인 중…' : (detail.data?.realName ?? '확인 불가 — 재제출 필요')}
      </p>
      {detail.isError && (
        <p role="alert" className="mb-3 text-small text-danger">
          {getErrorMessage(detail.error)}
        </p>
      )}
      <Button
        size="sm"
        onClick={() => void openProof()}
        loading={loading}
        disabled={!item.proofAvailable}
      >
        증빙 열기
      </Button>
      {!item.proofAvailable && (
        <p className="mt-2 text-small text-danger">
          등록된 증빙을 찾을 수 없습니다. 새 파일로 재제출이 필요합니다.
        </p>
      )}
      {error && (
        <p role="alert" className="mt-2 text-small text-danger">
          {error}
        </p>
      )}
      {url && (
        <div className="mt-4">
          {mime === 'application/pdf' ? (
            <iframe
              title="증빙 PDF"
              src={url}
              className="h-80 w-full rounded-input border border-line"
            />
          ) : (
            <img
              alt="제출된 증빙"
              src={url}
              className="max-h-80 w-full rounded-input object-contain"
            />
          )}
          <a
            href={url}
            download={`proof-${item.proofFileId}.${mime === 'application/pdf' ? 'pdf' : mime === 'image/png' ? 'png' : 'jpg'}`}
            className="mt-2 inline-block text-small text-primary underline"
          >
            증빙 다운로드
          </a>
          <label className="mt-4 flex items-start gap-2 text-small">
            <input
              type="checkbox"
              checked={checked}
              onChange={(e) => setChecked(e.target.checked)}
            />
            신청 실명과 이력 내용이 증빙과 일치함을 확인했습니다.
          </label>
        </div>
      )}
      <MobileActionBar>
        <Button
          variant="primary"
          disabled={!checked || !url || !detail.data?.nameAvailable}
          onClick={() => {
            approve.reset()
            setConfirm(true)
          }}
        >
          승인
        </Button>
      </MobileActionBar>
      <Modal
        open={confirm}
        title="인증 요청을 승인할까요?"
        description="승인 결과를 회원에게 알리고 심사용 실명을 삭제합니다."
        onClose={() => {
          if (!approve.isPending) setConfirm(false)
        }}
        footer={
          <>
            <Button disabled={approve.isPending} onClick={() => setConfirm(false)}>
              취소
            </Button>
            <Button variant="primary" loading={approve.isPending} onClick={() => approve.mutate()}>
              승인 확정
            </Button>
          </>
        }
      >
        {approve.isError && (
          <p role="alert" className="text-small text-danger">
            {getErrorMessage(approve.error)}
          </p>
        )}
      </Modal>
    </div>
  )
}
