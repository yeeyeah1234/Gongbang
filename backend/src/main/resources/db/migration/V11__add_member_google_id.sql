-- AUTH-10: 구글 로그인 추가 (11/17 정진우 결정, 카카오 + 구글).
-- login_type에는 'GOOGLE' 값이 추가된다 (VARCHAR(10) 그대로).
-- TODO(확인 필요): DB 명세서 member에 google_id 행 추가, 기능정의서 AUTH-10 「구글·네이버는 하지 않음」 수정
ALTER TABLE member
    ADD COLUMN google_id VARCHAR(50) NULL AFTER kakao_id,
    ADD CONSTRAINT uk_member_google_id UNIQUE (google_id);
