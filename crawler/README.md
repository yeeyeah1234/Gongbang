# 크롤러 (CRW-01)

위비티에서 공모전을 모아 백엔드 `POST /internal/contests/batch`로 보낸다. MySQL에 직접 접속하지 않는다.
포트폴리오용이라 최신 공모전 일부만 모은다 (`src/main.py`의 `MAX_PAGES = 2`, 약 30건).

## 내 로컬에 공모전이 안 보일 때

**버그가 아니다.** 공모전 데이터는 git으로 공유되지 않고, 크롤러를 돌린 사람의 로컬 DB에만 들어간다.
develop을 받으면 마이그레이션이 `contest` 테이블만 만들고 데이터는 비어 있다. 그래서 메인 화면에 「조건에 맞는 공모전이 없습니다」가 나온다 (MAIN-03 ①).

공모전 데이터가 필요하면 아래 순서대로 내 로컬 DB에 넣는다.

### 1. 백엔드를 크롤러 키와 함께 켠다

키 값은 아무거나 정하면 된다. 비어 있으면 백엔드가 크롤러 요청을 모두 401 `INVALID_INTERNAL_KEY`로 막는다.

```powershell
# PowerShell (backend 폴더에서)
$env:CRAWLER_INTERNAL_KEY = "local-key"; ./gradlew bootRun
```

```bash
# Git Bash / macOS (backend 폴더에서)
CRAWLER_INTERNAL_KEY=local-key ./gradlew bootRun
```

IntelliJ로 켤 때는 실행 설정의 Environment variables에 `CRAWLER_INTERNAL_KEY=local-key`를 넣는다.

### 2. 크롤러 `.env`를 backend 모드로 만든다

`crawler/.env.example`을 `crawler/.env`로 복사하고 아래처럼 바꾼다. 키는 1번과 같은 값이어야 한다.

```
CRAWLER_OUTPUT_MODE=backend
BACKEND_BASE_URL=http://localhost:8080
CRAWLER_INTERNAL_KEY=local-key
```

### 3. 수집 기록을 지우고 크롤러를 돌린다

```bash
# crawler 폴더에서 (처음 한 번만 가상환경을 만들고 설치)
python -m venv .venv
.venv\Scripts\activate        # macOS·Git Bash: source .venv/bin/activate
pip install -r requirements.txt

# 수집 기록 삭제 후 실행
rm state/contest_deadlines.json   # PowerShell: Remove-Item state\contest_deadlines.json
python src/main.py
```

로그에 `배치 전송 완료: created=…`가 나오면 성공이다. 메인 화면을 새로 고치면 공모전이 보인다.

### 왜 `state/contest_deadlines.json`을 지우나

크롤러는 이미 모은 공모전을 이 파일에 적어 두고, 마감일이 바뀌지 않았으면 다시 보내지 않는다. 아래 경우에는 이 기록 때문에 「보낼 변경 사항이 없습니다」만 나오고 DB에 아무것도 들어가지 않는다.

- 예전에 `json` 모드(기본값)로 돌린 적이 있을 때: DB가 아니라 `output/` 폴더에 파일로만 저장됐는데도 기록은 남는다.
- 로컬 DB를 초기화했을 때: DB는 비었는데 기록은 그대로다.

이 파일은 git에 올라가지 않는다 (`.gitignore`).

## 그래도 안 보이면

| 증상 | 확인할 것 |
|---|---|
| 크롤러 로그에 401 | 백엔드와 크롤러 `.env`의 `CRAWLER_INTERNAL_KEY`가 같은지, 백엔드를 키와 함께 켰는지 |
| 크롤러 로그에 연결 실패 | 백엔드가 켜져 있는지, `BACKEND_BASE_URL`이 맞는지 |
| 전송은 됐는데 화면에 없음 | 메인은 진행 중인 공모전만 보여 준다 (MAIN-01 ①). 예전에 넣은 데이터라면 모두 마감됐을 수 있다 |

## 배포 환경

배포하면 크롤러는 서버에서 정해진 시간마다 자동으로 돌고 (`scripts/run_cron.sh`, crontab 등록), 서버의 백엔드·DB 하나를 모두가 같이 쓴다.
사용자와 팀원이 크롤러를 따로 돌릴 필요는 없다. 로컬에서 직접 돌리는 건 각자 DB가 따로 있는 개발 환경이라서다.
