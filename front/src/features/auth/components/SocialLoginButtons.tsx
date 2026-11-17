import { useQuery } from '@tanstack/react-query'
import { Button } from '../../../components/ui/Button'
import { fetchOAuthProviders, startOAuthLogin, type OAuthProvider } from '../oauth'

interface SocialLoginButtonsProps {
  redirectTo: string
  onUnavailable: (message: string) => void
}

// AUTH-10 ① 로그인·회원가입 화면 아래 [카카오로 계속하기] [구글로 계속하기] (Figma 01-1)
export function SocialLoginButtons({ redirectTo, onUnavailable }: SocialLoginButtonsProps) {
  const providers = useQuery({
    queryKey: ['oauth-providers'],
    queryFn: fetchOAuthProviders,
    staleTime: Infinity,
    retry: false,
  })

  const start = (provider: OAuthProvider, label: string) => {
    // 서버에 그 제공자 키가 없으면(로컬 개발 등) 이동하지 않고 알려 준다
    if (!providers.data?.includes(provider)) {
      onUnavailable(`${label} 로그인은 준비 중이에요`)
      return
    }
    startOAuthLogin(provider, redirectTo)
  }

  return (
    <div className="flex flex-col gap-3">
      <Button variant="kakao" size="lg" className="w-full" onClick={() => start('kakao', '카카오')}>
        카카오로 계속하기
      </Button>
      <Button
        variant="outline"
        size="lg"
        className="w-full"
        onClick={() => start('google', '구글')}
      >
        구글로 계속하기
      </Button>
    </div>
  )
}
