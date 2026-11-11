import os

from dotenv import load_dotenv

load_dotenv()

WEVITY_BASE_URL = "https://www.wevity.com/"
USER_AGENT = "GongbangCrawler/1.0 (+contact: yeyeah-team)"

# CRW-01 ⑤: 요청 사이에 2초 이상 쉰다.
REQUEST_DELAY_SECONDS = 2.0

# 백엔드가 아직 없어서 당분간은 JSON 파일로 저장해 확인한다. 백엔드 완성되면
# .env의 CRAWLER_OUTPUT_MODE를 backend로 바꾼다.
OUTPUT_MODE = os.environ.get("CRAWLER_OUTPUT_MODE", "json")  # "json" | "backend"

# backend 모드에서만 필요. json 모드에서는 비어 있어도 된다.
BACKEND_BASE_URL = os.environ.get("BACKEND_BASE_URL")
CRAWLER_API_KEY = os.environ.get("CRAWLER_API_KEY")

# TODO(확인 필요): 백엔드 /internal/contests/batch가 크롤러 전용 키를 어떤 헤더로
# 받을지 API 명세서에 정해져 있지 않음. 백엔드 구현 시 맞춰서 바꿀 것.
CRAWLER_KEY_HEADER = "X-Crawler-Key"

_BASE_DIR = os.path.dirname(__file__)
STATE_FILE_PATH = os.path.join(_BASE_DIR, "..", "state", "contest_deadlines.json")
OUTPUT_DIR = os.path.join(_BASE_DIR, "..", "output")
LOG_FILE_PATH = os.path.join(_BASE_DIR, "..", "logs", "crawler.log")
