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
CRAWLER_INTERNAL_KEY = os.environ.get("CRAWLER_INTERNAL_KEY")

# 크롤러 전용 키를 담는 헤더 (API 명세서 CRW-01 비고, 백엔드 CrawlerKeyFilter와 같은 이름)
CRAWLER_KEY_HEADER = "X-Crawler-Key"

_BASE_DIR = os.path.dirname(__file__)
STATE_FILE_PATH = os.path.join(_BASE_DIR, "..", "state", "contest_deadlines.json")
OUTPUT_DIR = os.path.join(_BASE_DIR, "..", "output")
LOG_FILE_PATH = os.path.join(_BASE_DIR, "..", "logs", "crawler.log")
