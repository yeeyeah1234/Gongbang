import requests

from config import BACKEND_BASE_URL, CRAWLER_INTERNAL_KEY, CRAWLER_KEY_HEADER


def send_batch(contests):
    """POST /internal/contests/batch (CRW-01, API 명세서).

    요청: { contests: [{ sourceUrl, sourceSite, title, host, categoryCode,
            thumbnailUrl, startDate, deadline, description, homepageUrl }] }
    응답: { created, updated, deadlineChanged }
    에러: 401 INVALID_INTERNAL_KEY · 400 INVALID_INPUT
    """
    if not BACKEND_BASE_URL or not CRAWLER_INTERNAL_KEY:
        raise RuntimeError(
            "BACKEND_BASE_URL/CRAWLER_INTERNAL_KEY가 설정되지 않았습니다. "
            "백엔드가 준비되기 전이면 CRAWLER_OUTPUT_MODE=json으로 두세요."
        )

    response = requests.post(
        f"{BACKEND_BASE_URL}/internal/contests/batch",
        json={"contests": contests},
        headers={CRAWLER_KEY_HEADER: CRAWLER_INTERNAL_KEY},
        timeout=30,
    )
    response.raise_for_status()
    # 백엔드 공통 응답 { success, data, error }에서 data만 꺼낸다 (AGENTS.md 5장)
    return response.json().get("data") or {}
