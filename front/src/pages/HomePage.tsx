import { useNavigate } from 'react-router'
import { Button } from '../components/ui/Button'

// TODO(확인 필요): 메인 화면은 기능정의서·Figma 기준으로 담당자가 채운다
export default function HomePage() {
  const navigate = useNavigate()

  return (
    <main className="mx-auto max-w-[1680px] px-6 py-12 lg:px-12 xl:px-[120px]">
      <div className="rounded-card border border-line bg-surface p-8">
        <h1 className="text-subheading font-bold text-primary">공방</h1>
        <p className="mt-3 text-body text-text-sub">
          워크스페이스 채팅 화면(F-NET-06)을 바로 확인할 수 있습니다.
        </p>
        <div className="mt-6">
          <Button variant="primary" size="md" onClick={() => navigate('/teams/1/chat')}>
            현대차 공모전 · 알파팀 채팅방 열기
          </Button>
        </div>
      </div>
    </main>
  )
}
