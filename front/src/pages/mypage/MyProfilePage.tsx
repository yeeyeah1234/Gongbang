import { useEffect, useMemo, useRef, useState } from 'react'
import { useBlocker } from 'react-router'
import { getErrorMessage } from '../../api/errors'
import { Button } from '../../components/ui/Button'
import { Chip } from '../../components/ui/Chip'
import { Input } from '../../components/ui/Input'
import { Modal } from '../../components/ui/Modal'
import { Toast } from '../../components/ui/Toast'
import { useTimedToast } from '../../hooks/useTimedToast'
import { checkNickname } from '../../features/auth/api'
import { isNicknameFormatValid } from '../../features/auth/passwordRules'
import { useCategories } from '../../features/contest/hooks'
import {
  AwardModal,
  CareerModal,
  CertificateModal,
  VerifyRequestModal,
} from '../../features/member/components/HistoryModals'
import { HistorySection } from '../../features/member/components/HistorySection'
import { LeaveConfirmModal } from '../../features/member/components/LeaveConfirmModal'
import { MyPageLayout } from '../../features/member/components/MyPageLayout'
import { ProfilePhoto } from '../../features/member/components/ProfilePhoto'
import { PublicProfileCard } from '../../features/member/components/PublicProfileCard'
import { VerifyBadge } from '../../features/member/components/VerifyBadge'
import {
  BIO_MAX,
  DESIRED_ROLES,
  GRADES,
  SKILL_TAG_LIMIT,
  formatDate,
  formatMonth,
  gradeLabel,
  roleLabel,
} from '../../features/member/constants'
import { uploadProfileImage } from '../../features/member/api'
import {
  useDeleteHistory,
  useHistory,
  useMyProfile,
  useUpdateMyProfile,
} from '../../features/member/hooks'
import type { Award, Career, Certificate, MyProfile } from '../../features/member/types'

type HistoryModalState =
  | { kind: 'career'; item: Career | null }
  | { kind: 'certificate'; item: Certificate | null }
  | { kind: 'award'; item: Award | null }
  | null

// MY-01·05·07·08·09 / Figma 08-1(보기) · 08-2(수정) · 15~19
export default function MyProfilePage() {
  const toast = useTimedToast()
  const { data: profile, isLoading, isError } = useMyProfile()
  const careers = useHistory('careers')
  const certificates = useHistory('certificates')
  const awards = useHistory('awards')
  const deleteCareer = useDeleteHistory('careers')
  const deleteCertificate = useDeleteHistory('certificates')
  const deleteAward = useDeleteHistory('awards')
  const [editing, setEditing] = useState(false)
  const [historyModal, setHistoryModal] = useState<HistoryModalState>(null)
  const [verifyTarget, setVerifyTarget] = useState<{
    kind: 'certificates' | 'awards'
    id: number
    title: string
    proofFileId: number | null
  } | null>(null)
  const [previewOpen, setPreviewOpen] = useState(false)
  const [deleteTarget, setDeleteTarget] = useState<{ run: () => void; title: string } | null>(null)

  if (isLoading) {
    return (
      <MyPageLayout breadcrumb="마이페이지 / 내 프로필" title="내 프로필">
        <div className="h-64 animate-pulse rounded-card bg-surface-sub" />
      </MyPageLayout>
    )
  }
  if (isError || !profile) {
    return (
      <MyPageLayout breadcrumb="마이페이지 / 내 프로필" title="내 프로필">
        <p className="text-small text-text-sub">
          프로필을 불러오지 못했어요. 잠시 후 다시 시도해 주세요.
        </p>
      </MyPageLayout>
    )
  }

  return (
    <MyPageLayout
      breadcrumb={editing ? '마이페이지 / 내 프로필 / 수정' : '마이페이지 / 내 프로필'}
      title={editing ? '내 프로필 수정' : '내 프로필'}
      description={
        editing
          ? '수정한 내용은 [저장]을 눌러야 반영돼요.'
          : '팀원들에게 보여줄 나를 소개해 보세요.'
      }
      action={
        !editing && (
          <Button variant="outline" size="sm" onClick={() => setPreviewOpen(true)}>
            공개 프로필 미리보기
          </Button>
        )
      }
    >
      <div className="grid grid-cols-1 gap-6 xl:grid-cols-[minmax(0,1fr)_minmax(0,1fr)]">
        {editing ? (
          <ProfileEditCard
            profile={profile}
            onDone={(message) => {
              setEditing(false)
              if (message) toast.show(message)
            }}
          />
        ) : (
          <ProfileViewCard profile={profile} onEdit={() => setEditing(true)} />
        )}

        <div className="flex flex-col gap-6">
          <HistorySection
            title="경력"
            onAdd={() => setHistoryModal({ kind: 'career', item: null })}
            rows={(careers.data ?? []).map((c) => ({
              key: c.careerId,
              title: `${c.organization} · ${c.role}`,
              sub: `${formatMonth(c.startDate)} – ${c.endDate ? formatMonth(c.endDate) : '현재'}${c.description ? ` · ${c.description}` : ''}`,
              link: c.linkUrl,
              onEdit: () => setHistoryModal({ kind: 'career', item: c }),
              onDelete: () =>
                setDeleteTarget({ title: '경력', run: () => deleteCareer.mutate(c.careerId) }),
            }))}
          />
          <HistorySection
            title="자격증"
            onAdd={() => setHistoryModal({ kind: 'certificate', item: null })}
            rows={(certificates.data ?? []).map((c) => ({
              key: c.certificateId,
              title: (
                <>
                  {c.name} <VerifyBadge status={c.verifyStatus} />
                </>
              ),
              sub: `${formatDate(c.acquiredDate)} · ${c.issuer}`,
              onEdit: () => setHistoryModal({ kind: 'certificate', item: c }),
              onDelete: () =>
                setDeleteTarget({
                  title: '자격증',
                  run: () => deleteCertificate.mutate(c.certificateId),
                }),
            }))}
          />
          <HistorySection
            title="수상 경력"
            onAdd={() => setHistoryModal({ kind: 'award', item: null })}
            rows={(awards.data ?? []).map((a) => ({
              key: a.awardId,
              title: (
                <>
                  {a.contestName} {a.awardName} <VerifyBadge status={a.verifyStatus} />
                </>
              ),
              sub: `${formatDate(a.awardedDate)}`,
              onEdit: () => setHistoryModal({ kind: 'award', item: a }),
              onDelete: () =>
                setDeleteTarget({ title: '수상 경력', run: () => deleteAward.mutate(a.awardId) }),
            }))}
          />
        </div>
      </div>

      {historyModal?.kind === 'career' && (
        <CareerModal
          open
          item={historyModal.item}
          onClose={() => setHistoryModal(null)}
          onSaved={(m) => {
            setHistoryModal(null)
            toast.show(m)
          }}
        />
      )}
      {historyModal?.kind === 'certificate' && (
        <CertificateModal
          open
          item={historyModal.item}
          onClose={() => setHistoryModal(null)}
          onSaved={(m) => {
            setHistoryModal(null)
            toast.show(m)
          }}
          onRequestVerify={(c) => {
            setHistoryModal(null)
            setVerifyTarget({
              kind: 'certificates',
              id: c.certificateId,
              title: `${c.name} · 자격증`,
              proofFileId: c.proofFileId,
            })
          }}
        />
      )}
      {historyModal?.kind === 'award' && (
        <AwardModal
          open
          item={historyModal.item}
          onClose={() => setHistoryModal(null)}
          onSaved={(m) => {
            setHistoryModal(null)
            toast.show(m)
          }}
        />
      )}
      <VerifyRequestModal
        target={verifyTarget}
        onClose={() => setVerifyTarget(null)}
        onDone={(m) => {
          setVerifyTarget(null)
          toast.show(m)
        }}
      />

      <Modal
        open={previewOpen}
        title="공개 프로필 미리보기"
        size="md"
        onClose={() => setPreviewOpen(false)}
      >
        <div className="flex justify-center">
          <PublicProfileCard memberId={profile.memberId} />
        </div>
      </Modal>
      <Modal
        open={deleteTarget !== null}
        size="md"
        title={`${deleteTarget?.title ?? ''}을(를) 삭제할까요?`}
        description="되돌릴 수 없어요"
        onClose={() => setDeleteTarget(null)}
        footer={
          <>
            <Button variant="outline" size="lg" onClick={() => setDeleteTarget(null)}>
              취소
            </Button>
            <Button
              variant="danger"
              size="lg"
              onClick={() => {
                deleteTarget?.run()
                setDeleteTarget(null)
              }}
            >
              삭제
            </Button>
          </>
        }
      />
      <Toast message={toast.message} />
    </MyPageLayout>
  )
}

function InfoRow({ label, value }: { label: string; value: string }) {
  return (
    <div className="flex gap-4 text-small">
      <dt className="w-14 shrink-0 text-text-sub">{label}</dt>
      <dd className="text-text">{value}</dd>
    </div>
  )
}

// Figma 08-1 왼쪽 「기본 프로필」 카드 (보기 모드)
function ProfileViewCard({ profile, onEdit }: { profile: MyProfile; onEdit: () => void }) {
  const categories = useCategories()
  const interestNames = profile.interestCategoryCodes.map(
    (code) => categories.data?.find((c) => c.categoryCode === code)?.name ?? code,
  )
  return (
    <section className="rounded-card border border-line bg-surface p-6">
      <header className="flex items-center justify-between">
        <h2 className="text-body font-bold text-text">기본 프로필</h2>
        <Button variant="primary" size="sm" onClick={onEdit}>
          프로필 수정
        </Button>
      </header>
      <div className="mt-6 flex items-center gap-5">
        <ProfilePhoto url={profile.profileImageUrl} name={profile.nickname} />
        <div className="min-w-0">
          <p className="text-subheading font-bold text-text">{profile.nickname}</p>
          <p className="text-small text-text-sub">
            {[profile.school, profile.major, gradeLabel(profile.grade)].filter(Boolean).join(' · ')}
          </p>
          {profile.bio && <p className="mt-1 text-small text-text">{profile.bio}</p>}
        </div>
      </div>
      <h3 className="mt-6 text-small font-bold text-text-sub">관심 분야 · 역할</h3>
      <div className="mt-2 flex flex-wrap gap-2">
        {[...interestNames, ...profile.desiredRoles.map(roleLabel)].map((tag) => (
          <span
            key={tag}
            className="rounded-full border border-line-strong px-3 py-1 text-caption text-text"
          >
            {tag}
          </span>
        ))}
      </div>
      {profile.skillTags.length > 0 && (
        <>
          <h3 className="mt-6 text-small font-bold text-text-sub">기술 태그</h3>
          <div className="mt-2 flex flex-wrap gap-2">
            {profile.skillTags.map((tag) => (
              <span
                key={tag}
                className="rounded-full bg-surface-sub px-3 py-1 text-caption text-text"
              >
                #{tag}
              </span>
            ))}
          </div>
        </>
      )}
      <h3 className="mt-6 text-small font-bold text-text-sub">계정</h3>
      <dl className="mt-2 flex flex-col gap-1">
        <InfoRow label="이메일" value={`${profile.email} (비공개)`} />
        <InfoRow label="가입일" value={formatDate(profile.createdAt)} />
        {profile.portfolioUrl && <InfoRow label="포트폴리오" value={profile.portfolioUrl} />}
      </dl>
    </section>
  )
}

// Figma 08-2 「기본 프로필」 수정 모드 (MY-01 ②~⑤)
function ProfileEditCard({
  profile,
  onDone,
}: {
  profile: MyProfile
  onDone: (message?: string) => void
}) {
  const categories = useCategories()
  const update = useUpdateMyProfile()
  const toast = useTimedToast()
  const [nickname, setNickname] = useState(profile.nickname)
  const [nicknameChecked, setNicknameChecked] = useState<string | null>(profile.nickname)
  const [nicknameError, setNicknameError] = useState<string | null>(null)
  const [school, setSchool] = useState(profile.school ?? '')
  const [major, setMajor] = useState(profile.major ?? '')
  const [grade, setGrade] = useState(profile.grade ?? '')
  const [bio, setBio] = useState(profile.bio ?? '')
  const [interests, setInterests] = useState(profile.interestCategoryCodes)
  const [roles, setRoles] = useState(profile.desiredRoles)
  const [skillTags, setSkillTags] = useState(profile.skillTags)
  const [tagInput, setTagInput] = useState('')
  const [portfolioUrl, setPortfolioUrl] = useState(profile.portfolioUrl ?? '')
  const [removePhoto, setRemovePhoto] = useState(false)
  // MY-01 ③ 새로 고른 사진: [저장]을 누를 때 올린다. 고르면 바로 미리 보기
  const [photoFile, setPhotoFile] = useState<File | null>(null)
  const [uploading, setUploading] = useState(false)
  const photoInput = useRef<HTMLInputElement>(null)
  const photoPreview = useMemo(
    () => (photoFile ? URL.createObjectURL(photoFile) : null),
    [photoFile],
  )
  useEffect(() => {
    return () => {
      if (photoPreview) URL.revokeObjectURL(photoPreview)
    }
  }, [photoPreview])
  const [error, setError] = useState<string | null>(null)

  const dirty =
    nickname !== profile.nickname ||
    school !== (profile.school ?? '') ||
    major !== (profile.major ?? '') ||
    grade !== (profile.grade ?? '') ||
    bio !== (profile.bio ?? '') ||
    portfolioUrl !== (profile.portfolioUrl ?? '') ||
    removePhoto ||
    photoFile !== null ||
    interests.join() !== profile.interestCategoryCodes.join() ||
    roles.join() !== profile.desiredRoles.join() ||
    skillTags.join() !== profile.skillTags.join()

  // ⑤ 저장하지 않고 다른 메뉴로 가면 확인 모달
  const blocker = useBlocker(
    ({ currentLocation, nextLocation }) =>
      dirty && !update.isPending && currentLocation.pathname !== nextLocation.pathname,
  )
  const [cancelConfirm, setCancelConfirm] = useState(false)

  const toggle = (list: string[], value: string) =>
    list.includes(value) ? list.filter((v) => v !== value) : [...list, value]
  const nicknameOk = nickname.trim() === profile.nickname || nicknameChecked === nickname.trim()

  const handleCheckNickname = async () => {
    const value = nickname.trim()
    if (!isNicknameFormatValid(value)) {
      setNicknameError('닉네임은 2~10자 한글·영문·숫자로 입력해 주세요')
      return
    }
    try {
      if (await checkNickname(value)) {
        setNicknameChecked(value)
        setNicknameError(null)
      } else setNicknameError('이미 쓰고 있는 닉네임이에요')
    } catch (e) {
      setNicknameError(getErrorMessage(e))
    }
  }

  const addTag = () => {
    const tag = tagInput.trim().replace(/^#/, '')
    if (!tag || skillTags.includes(tag)) {
      setTagInput('')
      return
    }
    if (skillTags.length >= SKILL_TAG_LIMIT) {
      toast.show('기술 태그는 10개까지 넣을 수 있어요')
      return
    }
    setSkillTags([...skillTags, tag.slice(0, 20)])
    setTagInput('')
  }

  const pickPhoto = (file: File | undefined) => {
    if (!file) return
    if (!['image/jpeg', 'image/png'].includes(file.type)) {
      toast.show('JPG 또는 PNG 사진만 올릴 수 있어요')
      return
    }
    if (file.size > 5 * 1024 * 1024) {
      toast.show('사진은 5MB 이하만 올릴 수 있어요')
      return
    }
    setPhotoFile(file)
    setRemovePhoto(false)
  }

  const save = async () => {
    setError(null)
    if (photoFile) {
      setUploading(true)
      try {
        await uploadProfileImage(photoFile)
      } catch (e) {
        setError(getErrorMessage(e))
        setUploading(false)
        return
      }
      setUploading(false)
    }
    update.mutate(
      {
        // ④ 닉네임을 바꾸지 않았으면 보내지 않는다
        ...(nickname.trim() !== profile.nickname ? { nickname: nickname.trim() } : {}),
        removeProfileImage: removePhoto || undefined,
        school,
        major,
        grade: grade || undefined,
        bio,
        interestCategoryCodes: interests,
        desiredRoles: roles,
        skillTags,
        portfolioUrl,
      },
      {
        onSuccess: () => onDone('프로필을 저장했어요'),
        onError: (e) => setError(getErrorMessage(e)),
      },
    )
  }

  return (
    <section className="rounded-card border border-line bg-surface p-6">
      <header className="flex items-center justify-between">
        <h2 className="text-body font-bold text-text">기본 프로필</h2>
        <div className="flex gap-2">
          <Button
            variant="outline"
            size="sm"
            onClick={() => (dirty ? setCancelConfirm(true) : onDone())}
          >
            취소
          </Button>
          <Button
            variant="primary"
            size="sm"
            loading={uploading || update.isPending}
            disabled={!nicknameOk || interests.length === 0}
            onClick={() => void save()}
          >
            저장
          </Button>
        </div>
      </header>

      <div className="mt-6 flex items-center gap-4">
        <ProfilePhoto
          url={photoPreview ?? (removePhoto ? null : profile.profileImageUrl)}
          name={nickname}
        />
        <div>
          <div className="flex gap-2">
            <input
              ref={photoInput}
              type="file"
              accept="image/jpeg,image/png"
              className="hidden"
              onChange={(e) => {
                pickPhoto(e.target.files?.[0])
                e.target.value = '' // 같은 파일을 다시 골라도 바뀌게
              }}
            />
            <Button variant="outline" size="sm" onClick={() => photoInput.current?.click()}>
              사진 변경
            </Button>
            {(photoFile || (profile.profileImageUrl && !removePhoto)) && (
              <Button
                variant="text"
                size="sm"
                onClick={() => {
                  setPhotoFile(null)
                  setRemovePhoto(true)
                }}
              >
                삭제
              </Button>
            )}
          </div>
          <p className="mt-1 text-caption text-text-sub">JPG, PNG · 5MB 이하</p>
        </div>
      </div>

      <div className="mt-6 flex flex-col gap-4">
        <div>
          {/* 라벨은 따로 두고 버튼을 입력칸 윗선에 맞춘다 → 아래 안내 문구가 생겨도 버튼이 움직이지 않는다 */}
          <label htmlFor="edit-nickname" className="mb-2 block text-body font-bold text-text">
            닉네임
          </label>
          <div className="flex items-start gap-2">
            <div className="flex-1">
              <Input
                id="edit-nickname"
                maxLength={10}
                value={nickname}
                onChange={(e) => {
                  setNickname(e.target.value)
                  setNicknameError(null)
                }}
                errorText={nicknameError ?? undefined}
                successText={
                  nickname.trim() !== profile.nickname && nicknameOk
                    ? '사용할 수 있는 닉네임이에요'
                    : undefined
                }
              />
            </div>
            {nickname.trim() !== profile.nickname && (
              <Button
                variant="outline"
                size="lg"
                className="h-14 shrink-0 text-body"
                onClick={handleCheckNickname}
              >
                중복 확인
              </Button>
            )}
          </div>
        </div>
        <Input
          id="edit-email"
          label="이메일"
          value={profile.email}
          disabled
          helperText="이메일은 바꿀 수 없어요"
        />
        <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
          <Input
            id="edit-school"
            label="학교"
            maxLength={50}
            value={school}
            onChange={(e) => setSchool(e.target.value)}
          />
          <Input
            id="edit-major"
            label="전공"
            maxLength={50}
            value={major}
            onChange={(e) => setMajor(e.target.value)}
          />
        </div>
        <div className="flex flex-col">
          <label htmlFor="edit-grade" className="mb-2 text-body font-bold text-text">
            학년
          </label>
          <select
            id="edit-grade"
            value={grade}
            onChange={(e) => setGrade(e.target.value)}
            className="h-14 rounded-input border border-line-input bg-surface px-4 text-body focus:border-2 focus:border-primary focus:outline-none"
          >
            <option value="">선택 안 함</option>
            {GRADES.map((g) => (
              <option key={g.code} value={g.code}>
                {g.label}
              </option>
            ))}
          </select>
        </div>
        <fieldset>
          <legend className="mb-2 text-body font-bold text-text">
            관심 분야 <span className="font-normal text-text-sub">(필수)</span>
          </legend>
          <div className="flex flex-wrap gap-2">
            {categories.data?.map((c) => (
              <Chip
                key={c.categoryCode}
                selected={interests.includes(c.categoryCode)}
                onClick={() => setInterests(toggle(interests, c.categoryCode))}
              >
                {c.name}
              </Chip>
            ))}
          </div>
        </fieldset>
        <fieldset>
          <legend className="mb-2 text-body font-bold text-text">희망 역할</legend>
          <div className="flex flex-wrap gap-2">
            {DESIRED_ROLES.map((r) => (
              <Chip
                key={r.code}
                selected={roles.includes(r.code)}
                onClick={() => setRoles(toggle(roles, r.code))}
              >
                {r.label}
              </Chip>
            ))}
          </div>
        </fieldset>
        <div>
          <label htmlFor="edit-tag" className="mb-2 block text-body font-bold text-text">
            기술 태그{' '}
            <span className="font-normal text-text-sub">
              ({skillTags.length} / {SKILL_TAG_LIMIT})
            </span>
          </label>
          <div className="flex flex-wrap gap-2">
            {skillTags.map((tag) => (
              <span
                key={tag}
                className="inline-flex h-8 items-center gap-1 rounded-full bg-surface-sub px-3 text-caption text-text"
              >
                #{tag}
                <button
                  type="button"
                  aria-label={`${tag} 태그 빼기`}
                  className="cursor-pointer text-text-sub"
                  onClick={() => setSkillTags(skillTags.filter((t) => t !== tag))}
                >
                  ×
                </button>
              </span>
            ))}
          </div>
          <div className="mt-2 flex gap-2">
            <input
              id="edit-tag"
              value={tagInput}
              maxLength={20}
              placeholder="예: Spring"
              onChange={(e) => setTagInput(e.target.value)}
              onKeyDown={(e) => {
                if (e.key === 'Enter') {
                  e.preventDefault()
                  addTag()
                }
              }}
              className="h-11 flex-1 rounded-input border border-line-input bg-surface px-4 text-body focus:border-2 focus:border-primary focus:outline-none"
            />
            <Button variant="outline" onClick={addTag}>
              + 태그 추가
            </Button>
          </div>
        </div>
        <Input
          id="edit-bio"
          label="한 줄 소개"
          maxLength={BIO_MAX}
          value={bio}
          onChange={(e) => setBio(e.target.value)}
          helperText={`${bio.length} / ${BIO_MAX}`}
        />
        <Input
          id="edit-portfolio"
          label="포트폴리오 링크"
          placeholder="https://"
          value={portfolioUrl}
          onChange={(e) => setPortfolioUrl(e.target.value)}
        />
        {error && <p className="text-small text-danger">{error}</p>}
      </div>

      <LeaveConfirmModal
        open={blocker.state === 'blocked' || cancelConfirm}
        onStay={() => {
          if (blocker.state === 'blocked') blocker.reset()
          setCancelConfirm(false)
        }}
        onLeave={() => {
          if (blocker.state === 'blocked') blocker.proceed()
          else {
            setCancelConfirm(false)
            onDone()
          }
        }}
      />
      <Toast message={toast.message} />
    </section>
  )
}
