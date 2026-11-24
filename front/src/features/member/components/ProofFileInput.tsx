import { useId, useState } from 'react'
import { apiClient } from '../../../api/client'
import type { ApiResponse } from '../../../api/types'
import { getErrorMessage } from '../../../api/errors'

export function ProofFileInput({
  value,
  onChange,
  onBusy,
}: {
  value: number | null
  onChange: (id: number) => void
  onBusy: (busy: boolean) => void
}) {
  const id = useId()
  const [name, setName] = useState('')
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)
  async function upload(file: File) {
    setError('')
    if (file.size > 5 * 1024 * 1024 || !/\.(jpe?g|png|pdf)$/i.test(file.name)) {
      setError('JPG·PNG·PDF, 5MB 이하 파일을 선택해 주세요.')
      return
    }
    setBusy(true)
    onBusy(true)
    try {
      const form = new FormData()
      form.append('file', file)
      form.append('purpose', 'PROOF')
      const result = await apiClient.post<ApiResponse<{ fileId: number }>>('/api/files', form, {
        headers: { 'Content-Type': 'multipart/form-data' },
      })
      onChange(result.data.data.fileId)
      setName(file.name)
    } catch (e) {
      setError(getErrorMessage(e))
    } finally {
      setBusy(false)
      onBusy(false)
    }
  }
  return (
    <div className="rounded-input border border-dashed border-line-dashed bg-bg p-4">
      <label htmlFor={id} className="mb-2 block text-small font-bold">
        증빙 파일 (JPG·PNG·PDF, 최대 5MB)
      </label>
      <input
        id={id}
        type="file"
        accept="image/jpeg,image/png,application/pdf"
        disabled={busy}
        onChange={(e) => {
          const file = e.target.files?.[0]
          if (file) void upload(file)
          e.target.value = ''
        }}
        className="w-full text-small"
      />
      <p role="status" className="mt-2 text-small text-text-sub">
        {busy
          ? '업로드 중…'
          : name || (value ? `등록된 증빙 #${value}` : '파일은 본인과 관리자만 확인할 수 있어요.')}
      </p>
      {error && (
        <p role="alert" className="mt-2 text-small text-danger">
          {error}
        </p>
      )}
    </div>
  )
}
