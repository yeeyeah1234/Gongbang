import os

from dotenv import load_dotenv

load_dotenv()

WEVITY_BASE_URL = "https://www.wevity.com/"
USER_AGENT = "GongbangCrawler/1.0 (+contact: yeyeah-team)"

# CRW-01 ⑤: 요청 사이에 2초 이상 쉰다.
REQUEST_DELAY_SECONDS = 2.0

BACKEND_BASE_URL = os.environ["BACKEND_BASE_URL"]
CRAWLER_API_KEY = os.environ["CRAWLER_API_KEY"]

# TODO(확인 필요): 백엔드 /internal/contests/batch가 크롤러 전용 키를 어떤 헤더로
# 받을지 API 명세서에 정해져 있지 않음. 백엔드 구현 시 맞춰서 바꿀 것.
CRAWLER_KEY_HEADER = "X-Crawler-Key"

STATE_FILE_PATH = os.path.join(os.path.dirname(__file__), "..", "state", "contest_deadlines.json")
