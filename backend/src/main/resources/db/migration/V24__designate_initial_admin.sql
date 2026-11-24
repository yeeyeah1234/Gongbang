-- 2026-11-23 사용자 지정 초기 관리자. 계정·비밀번호를 새로 생성하지 않는다.
-- 해당 활성 계정이 없으면 변경 행은 0건이다. 이후 가입을 자동 승격시키지는 않는다.
UPDATE member
SET role = 'ADMIN'
WHERE email = '1007jjw@naver.com' AND status = 'ACTIVE' AND role = 'USER';
