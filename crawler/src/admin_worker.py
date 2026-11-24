"""ADM-06 수동 재수집 worker. 별도 프로세스로 실행: python src/admin_worker.py"""
import logging
import time
import requests
import config
import main as crawler

log = logging.getLogger(__name__)


def call(path, body=None):
    if not config.BACKEND_BASE_URL or not config.CRAWLER_INTERNAL_KEY:
        raise RuntimeError("백엔드 URL과 크롤러 내부 키가 필요합니다")
    response = requests.post(
        f"{config.BACKEND_BASE_URL}/internal/contests/jobs{path}",
        json=body, headers={config.CRAWLER_KEY_HEADER: config.CRAWLER_INTERNAL_KEY}, timeout=30,
    )
    response.raise_for_status()
    return response.json().get("data")


def run_once():
    if config.OUTPUT_MODE != "backend":
        raise RuntimeError("수동 재수집은 CRAWLER_OUTPUT_MODE=backend가 필요합니다")
    job = call("/claim")
    if not job:
        return False
    success = False
    try:
        crawler.main(force=True)
        success = True
    finally:
        call(f"/{job['id']}/finish", {"success": success})
    return True


if __name__ == "__main__":
    while True:
        try:
            run_once()
        except Exception:
            log.exception("수동 재수집 실패")
        time.sleep(15)
