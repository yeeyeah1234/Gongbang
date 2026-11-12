import { Outlet } from 'react-router'
import { Gnb } from './Gnb'

// 사이트 공통 화면 틀: 맨 위에 GNB(GNB-01)를 붙인다.
// 워크스페이스 화면은 사이트 GNB를 숨기고 자기 헤더를 쓰므로(WS-02 ②) 이 틀 밖에 둔다.
export function SiteLayout() {
  return (
    <>
      <Gnb />
      <Outlet />
    </>
  )
}
