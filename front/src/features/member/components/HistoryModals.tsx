import { useState } from 'react'
import { useQueryClient } from '@tanstack/react-query'
import { getErrorMessage } from '../../../api/errors'
import { Button } from '../../../components/ui/Button'
import { Checkbox } from '../../../components/ui/Checkbox'
import { Input } from '../../../components/ui/Input'
import { Modal } from '../../../components/ui/Modal'
import { addHistory, requestVerify, updateHistory } from '../api'
import { memberKeys } from '../hooks'
import type { Award, Career, Certificate } from '../types'
import { VerifyBadge } from './VerifyBadge'

const today = () => new Date().toISOString().slice(0, 10)

function useRefresh(kind: 'careers' | 'certificates' | 'awards') {
  const queryClient = useQueryClient()
  return () => {
    void queryClient.invalidateQueries({ queryKey: memberKeys.history(kind) })
    void queryClient.invalidateQueries({ queryKey: memberKeys.me })
  }
}

function DateInput({
  id,
  label,
  value,
  onChange,
  max,
  disabled,
}: {
  id: string
  label: string
  value: string
  onChange: (v: string) => void
  max?: string
  disabled?: boolean
}) {
  return (
    <div className="flex flex-col">
      <label htmlFor={id} className="mb-2 text-small font-bold text-text">
        {label}
      </label>
      <input
        id={id}
        type="date"
        value={value}
        max={max}
        disabled={disabled}
        onChange={(e) => onChange(e.target.value)}
        className="h-12 rounded-input border border-line-input bg-surface px-4 text-body text-text focus:border-2 focus:border-primary focus:outline-none disabled:bg-surface-sub disabled:text-text-disabled"
      />
    </div>
  )
}

// 증빙 파일 칸 (MY-08 ②). TODO(확인 필요): 파일 업로드 모듈(uploaded_file, 담당 미정)이 생기면 실제로 올린다
function ProofFileBox() {
  return (
    <div className="flex flex-col">
      <span className="mb-2 text-small font-bold text-text">증빙 파일 (선택)</span>
      <div className="rounded-input border border-dashed border-line-dashed bg-bg px-4 py-5 text-center text-small text-text-sub">
        파일을 끌어다 놓거나 클릭해서 업로드
        <p className="mt-1 text-caption">
          상장·확인서 · PDF, JPG, PNG · 5MB 이하 (파일 업로드 준비 중)
        </p>
      </div>
    </div>
  )
}

interface ModalBaseProps<T> {
  open: boolean
  item: T | null // null이면 추가
  onClose: () => void
  onSaved: (message: string) => void
}

// Figma 15 이력 추가/수정 모달 · 경력 (MY-07 ②③)
export function CareerModal({ open, item, onClose, onSaved }: ModalBaseProps<Career>) {
  const refresh = useRefresh('careers')
  const [organization, setOrganization] = useState(item?.organization ?? '')
  const [role, setRole] = useState(item?.role ?? '')
  const [startDate, setStartDate] = useState(item?.startDate ?? '')
  const [endDate, setEndDate] = useState(item?.endDate ?? '')
  const [ongoing, setOngoing] = useState(item ? item.endDate === null : false)
  const [description, setDescription] = useState(item?.description ?? '')
  const [linkUrl, setLinkUrl] = useState(item?.linkUrl ?? '')
  const [error, setError] = useState<string | null>(null)
  const [saving, setSaving] = useState(false)

  // 관련 링크는 선택. 넣었다면 http:// 또는 https://로 시작해야 한다 (서버 UrlPolicy와 같은 규칙)
  const linkError =
    linkUrl.trim() && !/^https?:\/\//i.test(linkUrl.trim())
      ? 'http:// 또는 https://로 시작하는 주소를 넣어 주세요'
      : null
  const periodError =
    !ongoing && startDate && endDate && endDate < startDate ? '종료일은 시작일 이후여야 해요' : null
  const ready =
    organization.trim() &&
    role.trim() &&
    startDate &&
    (ongoing || endDate) &&
    !periodError &&
    !linkError

  const save = async () => {
    setSaving(true)
    setError(null)
    const input = {
      organization: organization.trim(),
      role: role.trim(),
      startDate,
      endDate: ongoing ? null : endDate,
      description: description.trim() || null,
      linkUrl: linkUrl.trim() || null,
    }
    try {
      if (item) await updateHistory('careers', item.careerId, input)
      else await addHistory('careers', input)
      refresh()
      onSaved(item ? '경력을 수정했어요' : '경력을 추가했어요')
    } catch (e) {
      setError(getErrorMessage(e))
    } finally {
      setSaving(false)
    }
  }

  return (
    <Modal
      open={open}
      title={item ? '경력 수정' : '경력 추가'}
      size="lg"
      onClose={onClose}
      footer={
        <>
          <Button variant="outline" size="lg" onClick={onClose}>
            취소
          </Button>
          <Button variant="primary" size="lg" disabled={!ready} loading={saving} onClick={save}>
            저장
          </Button>
        </>
      }
    >
      <div className="flex flex-col gap-4">
        <Input
          id="career-org"
          label="기관 / 단체명"
          required
          placeholder="회사·동아리·학회"
          maxLength={50}
          value={organization}
          onChange={(e) => setOrganization(e.target.value)}
        />
        <Input
          id="career-role"
          label="역할"
          required
          placeholder="백엔드 파트장"
          maxLength={50}
          value={role}
          onChange={(e) => setRole(e.target.value)}
        />
        <div className="grid grid-cols-[1fr_auto_1fr] items-end gap-2">
          <DateInput
            id="career-start"
            label="시작일 (필수)"
            value={startDate}
            onChange={setStartDate}
            max={today()}
          />
          <span className="pb-3 text-text-sub">~</span>
          <DateInput
            id="career-end"
            label="종료일"
            value={ongoing ? '' : endDate}
            onChange={setEndDate}
            disabled={ongoing}
          />
        </div>
        {periodError && <p className="text-small text-danger">{periodError}</p>}
        <Checkbox
          label="현재 진행 중"
          checked={ongoing}
          onChange={(e) => setOngoing(e.target.checked)}
        />
        <div className="flex flex-col">
          <label htmlFor="career-desc" className="mb-2 text-small font-bold text-text">
            주요 활동 (선택)
          </label>
          <textarea
            id="career-desc"
            maxLength={200}
            value={description}
            onChange={(e) => setDescription(e.target.value)}
            placeholder="어떤 일을 했는지 적어 주세요"
            className="h-28 resize-none rounded-input border border-line-input bg-surface p-4 text-body focus:border-2 focus:border-primary focus:outline-none"
          />
          <span className="mt-1 self-end text-caption text-text-sub">
            {description.length} / 200
          </span>
        </div>
        <Input
          id="career-link"
          label="관련 링크 (선택)"
          type="url"
          placeholder="https://"
          maxLength={500}
          value={linkUrl}
          onChange={(e) => setLinkUrl(e.target.value)}
          errorText={linkError ?? undefined}
          helperText="회사·동아리 페이지, 활동 결과물 등"
        />
        {error && <p className="text-small text-danger">{error}</p>}
      </div>
    </Modal>
  )
}

// Figma 16 이력 추가/수정 모달 · 자격증 (MY-08 ①~③⑥)
export function CertificateModal({
  open,
  item,
  onClose,
  onSaved,
  onRequestVerify,
}: ModalBaseProps<Certificate> & { onRequestVerify: (item: Certificate) => void }) {
  const refresh = useRefresh('certificates')
  const [name, setName] = useState(item?.name ?? '')
  const [issuer, setIssuer] = useState(item?.issuer ?? '')
  const [acquiredDate, setAcquiredDate] = useState(item?.acquiredDate ?? '')
  const [certNumber, setCertNumber] = useState(item?.certNumber ?? '')
  const [error, setError] = useState<string | null>(null)
  const [saving, setSaving] = useState(false)
  const ready = name.trim() && issuer.trim() && acquiredDate

  const save = async () => {
    setSaving(true)
    setError(null)
    const input = {
      name: name.trim(),
      issuer: issuer.trim(),
      acquiredDate,
      certNumber: certNumber.trim() || null,
      proofFileId: item?.proofFileId ?? null,
    }
    try {
      if (item) await updateHistory('certificates', item.certificateId, input)
      else await addHistory('certificates', input)
      refresh()
      onSaved(item ? '자격증을 수정했어요' : '자격증을 추가했어요')
    } catch (e) {
      setError(getErrorMessage(e))
    } finally {
      setSaving(false)
    }
  }

  return (
    <Modal
      open={open}
      title={item ? '자격증 수정' : '자격증 추가'}
      size="lg"
      onClose={onClose}
      footer={
        <>
          <Button variant="outline" size="lg" onClick={onClose}>
            취소
          </Button>
          <Button variant="primary" size="lg" disabled={!ready} loading={saving} onClick={save}>
            저장
          </Button>
        </>
      }
    >
      <div className="flex flex-col gap-4">
        {/* TODO(확인 필요): 국가기술자격 자동완성 목록 (MY-08 ②) */}
        <Input
          id="cert-name"
          label="자격증명"
          required
          placeholder="정보처리기사"
          maxLength={100}
          value={name}
          onChange={(e) => setName(e.target.value)}
        />
        <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
          <Input
            id="cert-issuer"
            label="발급기관"
            required
            placeholder="한국산업인력공단"
            maxLength={100}
            value={issuer}
            onChange={(e) => setIssuer(e.target.value)}
          />
          <DateInput
            id="cert-date"
            label="취득일 (필수)"
            value={acquiredDate}
            onChange={setAcquiredDate}
            max={today()}
          />
        </div>
        <Input
          id="cert-number"
          label="자격증 번호 (선택)"
          placeholder="큐넷 진위확인용"
          maxLength={50}
          value={certNumber}
          onChange={(e) => setCertNumber(e.target.value)}
        />
        <ProofFileBox />
        <div className="flex items-center justify-between rounded-input border border-line px-4 py-3">
          <span className="flex items-center gap-2 text-small text-text-sub">
            <VerifyBadge status={item?.verifyStatus ?? 'NONE'} />
            증빙을 인증하면 프로필에 &apos;인증&apos; 표시가 붙어요.
          </span>
          <Button
            variant="outline"
            size="sm"
            disabled={
              !item ||
              item.verifyStatus === 'PENDING' ||
              item.verifyStatus === 'VERIFIED' ||
              item.proofFileId === null
            }
            onClick={() => item && onRequestVerify(item)}
          >
            {item?.verifyStatus === 'REJECTED' ? '다시 제출' : '인증 요청'}
          </Button>
        </div>
        {item?.verifyStatus === 'VERIFIED' && (
          <p className="text-caption text-text-sub">
            인증된 항목을 고치면 인증이 풀리고 다시 요청해야 해요.
          </p>
        )}
        {error && <p className="text-small text-danger">{error}</p>}
      </div>
    </Modal>
  )
}

// Figma 17 이력 추가/수정 모달 · 수상 경력 (MY-09)
export function AwardModal({ open, item, onClose, onSaved }: ModalBaseProps<Award>) {
  const refresh = useRefresh('awards')
  const [contestName, setContestName] = useState(item?.contestName ?? '')
  const [awardName, setAwardName] = useState(item?.awardName ?? '')
  const [awardedDate, setAwardedDate] = useState(item?.awardedDate ?? '')
  const [error, setError] = useState<string | null>(null)
  const [saving, setSaving] = useState(false)
  const ready = contestName.trim() && awardName.trim() && awardedDate

  const save = async () => {
    setSaving(true)
    setError(null)
    const input = {
      contestName: contestName.trim(),
      awardName: awardName.trim(),
      awardedDate,
      proofFileId: item?.proofFileId ?? null,
    }
    try {
      if (item) await updateHistory('awards', item.awardId, input)
      else await addHistory('awards', input)
      refresh()
      onSaved(item ? '수상 경력을 수정했어요' : '수상 경력을 추가했어요')
    } catch (e) {
      setError(getErrorMessage(e))
    } finally {
      setSaving(false)
    }
  }

  return (
    <Modal
      open={open}
      title={item ? '수상 경력 수정' : '수상 경력 추가'}
      size="lg"
      onClose={onClose}
      footer={
        <>
          <Button variant="outline" size="lg" onClick={onClose}>
            취소
          </Button>
          <Button variant="primary" size="lg" disabled={!ready} loading={saving} onClick={save}>
            저장
          </Button>
        </>
      }
    >
      <div className="flex flex-col gap-4">
        <Input
          id="award-contest"
          label="공모전명"
          required
          placeholder="2026 캠퍼스 해커톤"
          maxLength={100}
          value={contestName}
          onChange={(e) => setContestName(e.target.value)}
        />
        <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
          <Input
            id="award-name"
            label="수상 내역"
            required
            placeholder="최우수상"
            maxLength={50}
            value={awardName}
            onChange={(e) => setAwardName(e.target.value)}
          />
          <DateInput
            id="award-date"
            label="수상일 (필수)"
            value={awardedDate}
            onChange={setAwardedDate}
            max={today()}
          />
        </div>
        <ProofFileBox />
        {error && <p className="text-small text-danger">{error}</p>}
      </div>
    </Modal>
  )
}

// Figma 18 인증 요청 팝업 (실명 입력, MY-08 ⑤)
export function VerifyRequestModal({
  target,
  onClose,
  onDone,
}: {
  target: {
    kind: 'certificates' | 'awards'
    id: number
    title: string
    proofFileId: number | null
  } | null
  onClose: () => void
  onDone: (message: string) => void
}) {
  const queryClient = useQueryClient()
  const [realName, setRealName] = useState('')
  const [agree, setAgree] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [saving, setSaving] = useState(false)
  if (!target) return null

  const submit = async () => {
    setSaving(true)
    setError(null)
    try {
      await requestVerify(target.kind, target.id, realName.trim(), target.proofFileId)
      void queryClient.invalidateQueries({ queryKey: memberKeys.history(target.kind) })
      onDone('인증을 요청했어요. 결과는 알림과 이메일로 알려 드려요')
    } catch (e) {
      setError(getErrorMessage(e))
    } finally {
      setSaving(false)
    }
  }

  return (
    <Modal
      open
      title="증빙 인증 요청"
      size="md"
      onClose={onClose}
      footer={
        <>
          <Button variant="outline" size="lg" onClick={onClose}>
            취소
          </Button>
          <Button
            variant="primary"
            size="lg"
            disabled={!realName.trim() || !agree}
            loading={saving}
            onClick={submit}
          >
            인증 요청
          </Button>
        </>
      }
    >
      <div className="flex flex-col gap-4">
        <div className="rounded-input bg-bg px-4 py-3 text-small font-bold text-text">
          {target.title}
        </div>
        <p className="text-small text-text-sub">
          증빙 서류에 적힌 이름과 같은지 확인하기 위해 실명을 입력해 주세요.
        </p>
        <Input
          id="verify-real-name"
          label="실명"
          required
          value={realName}
          maxLength={30}
          onChange={(e) => setRealName(e.target.value)}
          helperText="실명은 인증 검토에만 쓰이고 프로필에 공개되지 않아요."
        />
        <Checkbox
          label="[필수] 인증을 위한 실명 수집·이용에 동의합니다."
          checked={agree}
          onChange={(e) => setAgree(e.target.checked)}
        />
        {error && <p className="text-small text-danger">{error}</p>}
      </div>
    </Modal>
  )
}
