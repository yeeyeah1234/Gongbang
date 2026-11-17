-- 정진우 담당 테이블 (DB 명세서 member ~ service_notice).
-- 번호: V3 최규선, V4 이상현, V5~V7 김수형, V8 김건우가 써서 V9부터 쓴다 (11/14 팀 단톡).
-- id 타입은 이미 있는 contest·category와 맞춰 BIGINT UNSIGNED로 한다 (FK 타입이 같아야 함).
-- TODO(확인 필요): uploaded_file(공통, 담당 미정) 테이블이 아직 없어 proof_file_id FK는 빼 두었다.
--                 uploaded_file이 생기면 FK 추가 마이그레이션을 따로 올린다.

-- AUTH·MY 공통: 회원
CREATE TABLE member (
    id                BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    email             VARCHAR(100) NULL,
    password_hash     VARCHAR(100) NULL,
    login_type        VARCHAR(10)  NOT NULL DEFAULT 'EMAIL',
    kakao_id          VARCHAR(50)  NULL,
    nickname          VARCHAR(10)  NULL,
    role              VARCHAR(10)  NOT NULL DEFAULT 'USER',
    profile_image_url VARCHAR(500) NULL,
    bio               VARCHAR(50)  NULL,
    school            VARCHAR(50)  NULL,
    major             VARCHAR(50)  NULL,
    grade             VARCHAR(10)  NULL,
    portfolio_url     VARCHAR(500) NULL,
    status            VARCHAR(10)  NOT NULL DEFAULT 'ACTIVE',
    withdrawn_at      DATETIME     NULL,
    login_fail_count  INT          NOT NULL DEFAULT 0,
    locked_until      DATETIME     NULL,
    last_login_at     DATETIME     NULL,
    created_at        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT uk_member_email UNIQUE (email),
    CONSTRAINT uk_member_kakao_id UNIQUE (kakao_id),
    CONSTRAINT uk_member_nickname UNIQUE (nickname),
    INDEX idx_member_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- AUTH-04 약관 동의
CREATE TABLE terms_agreement (
    id            BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    member_id     BIGINT UNSIGNED NOT NULL,
    terms_type    VARCHAR(20) NOT NULL,
    terms_version VARCHAR(10) NOT NULL,
    agreed        BOOLEAN     NOT NULL,
    agreed_at     DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_terms_agreement UNIQUE (member_id, terms_type, terms_version),
    CONSTRAINT fk_terms_agreement_member FOREIGN KEY (member_id) REFERENCES member (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- AUTH-05 이메일 인증번호 (원문 저장 금지, 해시만)
CREATE TABLE email_verification (
    id          BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    email       VARCHAR(100) NOT NULL,
    purpose     VARCHAR(10)  NOT NULL,
    code_hash   VARCHAR(100) NOT NULL,
    fail_count  INT          NOT NULL DEFAULT 0,
    expires_at  DATETIME     NOT NULL,
    verified_at DATETIME     NULL,
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_email_verification (email, purpose, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- AUTH-01 refresh 토큰 (기기별 해시)
CREATE TABLE refresh_token (
    id          BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    member_id   BIGINT UNSIGNED NOT NULL,
    token_hash  VARCHAR(100) NOT NULL,
    remember_me BOOLEAN      NOT NULL DEFAULT FALSE,
    expires_at  DATETIME     NOT NULL,
    revoked_at  DATETIME     NULL,
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_refresh_token_hash UNIQUE (token_hash),
    INDEX idx_refresh_token_member (member_id),
    CONSTRAINT fk_refresh_token_member FOREIGN KEY (member_id) REFERENCES member (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- AUTH-07·MY-01 관심 분야 (category 테이블 공통 코드)
CREATE TABLE member_interest (
    member_id   BIGINT UNSIGNED NOT NULL,
    category_id BIGINT UNSIGNED NOT NULL,
    PRIMARY KEY (member_id, category_id),
    INDEX idx_member_interest_category (category_id),
    CONSTRAINT fk_member_interest_member FOREIGN KEY (member_id) REFERENCES member (id) ON DELETE CASCADE,
    CONSTRAINT fk_member_interest_category FOREIGN KEY (category_id) REFERENCES category (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- AUTH-07·MY-01 희망 역할
CREATE TABLE member_desired_role (
    member_id BIGINT UNSIGNED NOT NULL,
    role_code VARCHAR(20) NOT NULL,
    PRIMARY KEY (member_id, role_code),
    CONSTRAINT fk_member_desired_role_member FOREIGN KEY (member_id) REFERENCES member (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- MY-01 기술 태그 (10개 제한은 서버 검증)
CREATE TABLE member_skill_tag (
    id        BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    member_id BIGINT UNSIGNED NOT NULL,
    name      VARCHAR(20) NOT NULL,
    CONSTRAINT uk_member_skill_tag UNIQUE (member_id, name),
    CONSTRAINT fk_member_skill_tag_member FOREIGN KEY (member_id) REFERENCES member (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- MY-07 경력
CREATE TABLE career (
    id           BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    member_id    BIGINT UNSIGNED NOT NULL,
    organization VARCHAR(50)  NOT NULL,
    role         VARCHAR(50)  NOT NULL,
    start_date   DATE         NOT NULL,
    end_date     DATE         NULL,
    description  VARCHAR(200) NULL,
    created_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_career_member (member_id),
    CONSTRAINT fk_career_member FOREIGN KEY (member_id) REFERENCES member (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- MY-08 자격증
CREATE TABLE certificate (
    id            BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    member_id     BIGINT UNSIGNED NOT NULL,
    name          VARCHAR(100) NOT NULL,
    issuer        VARCHAR(100) NOT NULL,
    acquired_date DATE         NOT NULL,
    cert_number   VARCHAR(50)  NULL,
    proof_file_id BIGINT UNSIGNED NULL,
    verify_status VARCHAR(10)  NOT NULL DEFAULT 'NONE',
    created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_certificate_member (member_id),
    CONSTRAINT fk_certificate_member FOREIGN KEY (member_id) REFERENCES member (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- MY-09 수상 경력
CREATE TABLE award (
    id            BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    member_id     BIGINT UNSIGNED NOT NULL,
    contest_name  VARCHAR(100) NOT NULL,
    award_name    VARCHAR(50)  NOT NULL,
    awarded_date  DATE         NOT NULL,
    proof_file_id BIGINT UNSIGNED NULL,
    verify_status VARCHAR(10)  NOT NULL DEFAULT 'NONE',
    created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_award_member (member_id),
    CONSTRAINT fk_award_member FOREIGN KEY (member_id) REFERENCES member (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- MY-08·09 인증 요청 (target_id는 다형 참조라 FK 없음, 실명은 AES 암호화)
CREATE TABLE verification_request (
    id            BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    member_id     BIGINT UNSIGNED NOT NULL,
    target_type   VARCHAR(15)  NOT NULL,
    target_id     BIGINT UNSIGNED NOT NULL,
    real_name     VARCHAR(255) NULL,
    proof_file_id BIGINT UNSIGNED NOT NULL,
    status        VARCHAR(10)  NOT NULL DEFAULT 'PENDING',
    reject_reason VARCHAR(200) NULL,
    reviewer_id   BIGINT UNSIGNED NULL,
    requested_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    reviewed_at   DATETIME     NULL,
    INDEX idx_verification_request_member (member_id),
    INDEX idx_verification_request_target (target_type, target_id),
    INDEX idx_verification_request_status (status),
    CONSTRAINT fk_verification_request_member FOREIGN KEY (member_id) REFERENCES member (id) ON DELETE CASCADE,
    CONSTRAINT fk_verification_request_reviewer FOREIGN KEY (reviewer_id) REFERENCES member (id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- MY-03 북마크
CREATE TABLE bookmark (
    member_id  BIGINT UNSIGNED NOT NULL,
    contest_id BIGINT UNSIGNED NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (member_id, contest_id),
    INDEX idx_bookmark_contest (contest_id),
    CONSTRAINT fk_bookmark_member FOREIGN KEY (member_id) REFERENCES member (id) ON DELETE CASCADE,
    CONSTRAINT fk_bookmark_contest FOREIGN KEY (contest_id) REFERENCES contest (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- NOTI-01~04 알림
CREATE TABLE notification (
    id          BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    receiver_id BIGINT UNSIGNED NOT NULL,
    type        VARCHAR(30)  NOT NULL,
    actor_id    BIGINT UNSIGNED NULL,
    target_type VARCHAR(20)  NULL,
    target_id   BIGINT UNSIGNED NULL,
    message     VARCHAR(200) NOT NULL,
    link_url    VARCHAR(300) NOT NULL,
    group_count INT          NOT NULL DEFAULT 1,
    is_read     BOOLEAN      NOT NULL DEFAULT FALSE,
    read_at     DATETIME     NULL,
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_notification_receiver (receiver_id, is_read, created_at),
    INDEX idx_notification_created_at (created_at),
    CONSTRAINT fk_notification_receiver FOREIGN KEY (receiver_id) REFERENCES member (id) ON DELETE CASCADE,
    CONSTRAINT fk_notification_actor FOREIGN KEY (actor_id) REFERENCES member (id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- MY-10 알림 수신 설정 (행이 없으면 기본값)
CREATE TABLE notification_setting (
    member_id     BIGINT UNSIGNED NOT NULL,
    setting_type  VARCHAR(20) NOT NULL,
    site_enabled  BOOLEAN     NOT NULL DEFAULT TRUE,
    email_enabled BOOLEAN     NOT NULL DEFAULT TRUE,
    updated_at    DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (member_id, setting_type),
    CONSTRAINT fk_notification_setting_member FOREIGN KEY (member_id) REFERENCES member (id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- NOTI-06 메일 대기열
CREATE TABLE email_outbox (
    id              BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    receiver_id     BIGINT UNSIGNED NULL,
    to_email        VARCHAR(100) NOT NULL,
    mail_type       VARCHAR(30)  NOT NULL,
    is_security     BOOLEAN      NOT NULL DEFAULT FALSE,
    send_mode       VARCHAR(20)  NOT NULL DEFAULT 'IMMEDIATE',
    notification_id BIGINT UNSIGNED NULL,
    subject         VARCHAR(200) NOT NULL,
    body            TEXT         NOT NULL,
    status          VARCHAR(10)  NOT NULL DEFAULT 'PENDING',
    scheduled_at    DATETIME     NOT NULL,
    sent_at         DATETIME     NULL,
    retry_count     INT          NOT NULL DEFAULT 0,
    created_at      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_email_outbox_receiver (receiver_id),
    INDEX idx_email_outbox_status (status, scheduled_at),
    CONSTRAINT fk_email_outbox_receiver FOREIGN KEY (receiver_id) REFERENCES member (id) ON DELETE SET NULL,
    CONSTRAINT fk_email_outbox_notification FOREIGN KEY (notification_id) REFERENCES notification (id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- NOTI-05 예약 알림
CREATE TABLE scheduled_notification (
    id          BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    type        VARCHAR(20) NOT NULL,
    target_type VARCHAR(20) NOT NULL,
    target_id   BIGINT UNSIGNED NOT NULL,
    fire_at     DATETIME    NOT NULL,
    status      VARCHAR(10) NOT NULL DEFAULT 'PENDING',
    created_at  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_scheduled_notification_target (target_type, target_id),
    INDEX idx_scheduled_notification_status (status, fire_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- NOTI-07 운영 공지
CREATE TABLE service_notice (
    id         BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    author_id  BIGINT UNSIGNED NOT NULL,
    title      VARCHAR(50)   NOT NULL,
    content    VARCHAR(2000) NOT NULL,
    deleted_at DATETIME      NULL,
    created_at DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_service_notice_created_at (created_at),
    CONSTRAINT fk_service_notice_author FOREIGN KEY (author_id) REFERENCES member (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
