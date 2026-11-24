# 공통 URL 입력 정책 (#120)

공통 위치: `com.gongbang.global.validation.UrlPolicy`

```java
import com.gongbang.global.validation.UrlPolicy;

String homepageUrl = UrlPolicy.validateOrNull(request.homepageUrl());
```

반환값을 실제 저장 값으로 사용한다. 메서드는 DB 접근이나 외부 네트워크 요청을 하지 않는다.

| 입력 | 결과 |
|---|---|
| null, 빈 문자열, 공백만 있는 문자열 | null |
| 앞뒤 공백이 있는 주소 | 앞뒤 공백 제거 |
| http://, https:// (대소문자 무관) + 호스트 | 허용 |
| HTTPS://Example.com/Path?Key=Value | 대소문자·경로·쿼리 그대로 보존 |
| https://, https:///path, 상대경로 | INVALID_INPUT |
| javascript:, data:, ftp: | INVALID_INPUT |
| 잘못된 URI 문법, 내부 공백, 잘못된 퍼센트 인코딩 | INVALID_INPUT |

호스트는 Java URI의 `getHost()`로 판별한다. localhost·IP·IPv6도 호스트로 인정한다. 국제화 도메인은 ASCII/Punycode 형태로 전달한다. DNS 존재 여부나 접속 가능 여부는 검사하지 않는다. 이 정책은 입력 형식 검사이며 서버가 해당 URL을 직접 요청할 때 필요한 접근 대상 제한을 대신하지 않는다.

## 담당별 적용

- member: `MemberService`의 프로필/포트폴리오 URL, `HistoryService`의 경력 링크에 적용했다. 기존 member/service/UrlPolicy는 공통 위치로 이동했다.
- post·board: 후속 PR에서 기존 접두사 검사를 공통 메서드 호출로 교체하고 반환값을 저장한다.
- contest: `ContestBatchItem`의 기존 `@Pattern`이 대문자 스킴을 먼저 거부할 수 있으므로 DTO 검증과 저장 시 정규화를 함께 맞춘다. 공통 메서드만 추가하고 기존 검증을 남기면 기준이 일치하지 않는다.

필수 여부·최대 길이는 각 기능의 기존 명세를 유지한다. 공통 정책은 빈 값을 null로 변환하므로 필수 URL인 기능은 별도로 필수 검증을 한다. 기존 DB 데이터는 이 변경으로 일괄 수정하지 않는다.

검증 테스트: `UrlPolicyTest` (빈 값, 스킴 대소문자, 호스트 누락, URI 문법, 공백 제거 및 주소 보존).
