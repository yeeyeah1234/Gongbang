import { useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Navigate, useNavigate } from 'react-router'
import { isAxiosError } from 'axios'
import { fetchMyProfile, updateMyProfile } from '../../features/member/api'
import { loadLoginMember, signOut } from '../../features/auth/session'
import { memberKeys } from '../../features/member/hooks'
import { useAuthStore } from '../../features/auth/store'
import { Button } from '../../components/ui/Button'

export default function RequiredNicknamePage() {
  const [nickname, setNickname] = useState('')
  const navigate = useNavigate()
  const client = useQueryClient()
  const memberId = useAuthStore((s) => s.member?.memberId)
  const profile = useQuery({
    queryKey: ['required-nickname-profile', memberId],
    enabled: !!memberId,
    queryFn: fetchMyProfile,
    retry: false,
  })
  const mutation = useMutation({
    mutationFn: () => updateMyProfile({ nickname: nickname.trim() }),
    onSuccess: async (updated) => {
      await client.cancelQueries({ queryKey: memberKeys.me, exact: true })
      client.setQueryData(memberKeys.me, updated)
      client.setQueryData(['account-status', memberId], updated)
      client.setQueryData(['required-nickname-profile', memberId], updated)
      await loadLoginMember()
      await client.invalidateQueries({ queryKey: ['account-status'] })
      client.removeQueries({ queryKey: ['required-nickname-profile'] })
      navigate('/mypage/profile', { replace: true })
    },
  })
  if (profile.isPending)
    return (
      <p role="status" className="p-4">
        회원 정보를 불러오는 중입니다…
      </p>
    )
  if (profile.isError)
    return (
      <main className="mx-auto max-w-lg space-y-4 px-4 py-8" role="alert">
        <p>회원 정보를 불러오지 못했어요. 다시 시도해 주세요.</p>
        <Button onClick={() => profile.refetch()} disabled={profile.isFetching}>
          다시 시도
        </Button>
        <Button
          onClick={async () => {
            await signOut()
            navigate('/login', { replace: true })
          }}
        >
          로그아웃
        </Button>
      </main>
    )
  if (profile.data && !profile.data.nicknameChangeRequired) {
    return <Navigate to="/mypage/profile" replace />
  }
  return (
    <main className="mx-auto w-full max-w-lg px-4 py-8 text-text">
      <h1 className="text-title font-bold">닉네임 변경</h1>
      <p className="mt-4">운영 정책에 따라 닉네임을 변경해야 서비스를 이용할 수 있습니다.</p>
      <p className="mt-2 text-small text-text-sub">
        제재된 기존 닉네임은 다시 사용할 수 없습니다. 다른 프로필 정보는 변경하지 않아도 됩니다.
      </p>
      <form
        className="mt-6 space-y-4"
        onSubmit={(e) => {
          e.preventDefault()
          if (!mutation.isPending && profile.data) mutation.mutate()
        }}
      >
        <label className="block">
          새 닉네임
          <input
            autoFocus
            required
            minLength={2}
            maxLength={10}
            value={nickname}
            onChange={(e) => setNickname(e.target.value)}
            className="mt-2 w-full rounded-input border border-line-input px-4 py-3"
          />
        </label>
        <p className="text-small">한글·영문·숫자 2~10자</p>
        {mutation.isError && (
          <p role="alert" className="text-danger">
            {isAxiosError(mutation.error)
              ? (mutation.error.response?.data?.error?.message ?? '변경에 실패했습니다.')
              : '변경에 실패했습니다.'}
          </p>
        )}
        <Button
          type="submit"
          variant="primary"
          disabled={
            mutation.isPending ||
            !profile.data ||
            nickname.trim().length < 2 ||
            nickname.trim().toLowerCase() === profile.data.nickname.toLowerCase()
          }
        >
          닉네임 변경하고 계속하기
        </Button>
      </form>
      <Button
        className="mt-4"
        onClick={async () => {
          await signOut()
          navigate('/login', { replace: true })
        }}
      >
        로그아웃
      </Button>
    </main>
  )
}
