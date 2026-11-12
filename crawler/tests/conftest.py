import os
import sys

# config.py는 모듈 로드 시 BACKEND_BASE_URL/CRAWLER_API_KEY 환경 변수를 필수로 읽는다.
# 테스트에서는 실제 .env 없이도 임포트가 되도록 기본값을 미리 넣어준다.
os.environ.setdefault("BACKEND_BASE_URL", "http://localhost:8080")
os.environ.setdefault("CRAWLER_API_KEY", "test-key")

sys.path.insert(0, os.path.join(os.path.dirname(__file__), "..", "src"))
