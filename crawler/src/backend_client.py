import requests

from config import BACKEND_BASE_URL, CRAWLER_API_KEY, CRAWLER_KEY_HEADER

BATCH_URL = f"{BACKEND_BASE_URL}/internal/contests/batch"


def send_batch(contests):
    """POST /internal/contests/batch (CRW-01, API 명세서).

    요청: { contests: [{ sourceUrl, sourceSite, title, host, categoryCode,
            thumbnailUrl, startDate, deadline, description }] }
    응답: { created, updated, deadlineChanged }
    에러: 401 INVALID_CRAWLER_KEY · 400 INVALID_INPUT
    """
    response = requests.post(
        BATCH_URL,
        json={"contests": contests},
        headers={CRAWLER_KEY_HEADER: CRAWLER_API_KEY},
        timeout=30,
    )
    response.raise_for_status()
    return response.json()
