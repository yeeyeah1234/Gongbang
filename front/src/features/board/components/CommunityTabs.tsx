import { NavLink } from 'react-router'

const TABS = [
  { label: '팀원 모집', to: '/posts' },
  { label: '커뮤니티 게시판', to: '/board' },
]

// PST-001 ①·BRD-001 ①: GNB 「커뮤니티」 안에서 팀원 모집 목록과 커뮤니티 게시판을 오가는 위쪽 탭
export function CommunityTabs() {
  return (
    <nav aria-label="커뮤니티" className="flex gap-8 border-b border-line">
      {TABS.map((tab) => (
        <NavLink
          key={tab.to}
          to={tab.to}
          end
          className={({ isActive }) =>
            [
              '-mb-px inline-flex h-12 items-center border-b-2 text-body-lg',
              isActive
                ? 'border-primary font-bold text-text'
                : 'border-transparent text-text-sub hover:text-text',
            ].join(' ')
          }
        >
          {tab.label}
        </NavLink>
      ))}
    </nav>
  )
}
