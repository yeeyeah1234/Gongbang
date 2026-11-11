import datetime
import logging

import backend_client
import state_store
import wevity_client
from category_map import map_categories_to_code

logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(message)s")
log = logging.getLogger("crawler")

MAX_PAGES = 100  # 안전장치: 목록 페이지가 비정상적으로 계속 나오는 경우 대비.


def _estimate_deadline(dday):
    if dday is None:
        return None
    return (datetime.date.today() + datetime.timedelta(days=dday)).isoformat()


def collect_list_items():
    items = []
    for page in range(1, MAX_PAGES + 1):
        html = wevity_client.fetch_list_page(page)
        page_items = wevity_client.parse_list_page(html)
        if not page_items:
            break
        items.extend(page_items)
        log.info("목록 %d페이지: %d건", page, len(page_items))
    return items


def build_contest_payload(item, detail, source_url):
    categories = detail["categories"] or item["categories"]
    return {
        "sourceUrl": source_url,
        "sourceSite": "WEVITY",
        "title": detail["title"] or item["title"],
        "host": detail["host"] or item["host"],
        "categoryCode": map_categories_to_code(categories),
        "thumbnailUrl": detail["thumbnail_url"],
        "startDate": detail["start_date"],
        "deadline": detail["end_date"],
        "description": detail["description"],
    }


def main():
    state = state_store.load()
    pending_state = {}
    contests_batch = []

    for item in collect_list_items():
        source_url = wevity_client.build_source_url(item["ix"])
        cached_deadline = state.get(source_url, {}).get("deadline")
        estimated_deadline = _estimate_deadline(item["dday"])

        need_detail = cached_deadline is None or estimated_deadline != cached_deadline
        if not need_detail:
            continue

        log.info("상세 조회: ix=%s (%s)", item["ix"], item["title"])
        try:
            detail_html = wevity_client.fetch_detail_page(item["ix"])
            detail = wevity_client.parse_detail_page(detail_html)
        except Exception:
            log.exception("상세 페이지 조회/파싱 실패, 건너뜀: ix=%s", item["ix"])
            continue

        if not detail["end_date"]:
            log.warning("접수기간 파싱 실패, 건너뜀: ix=%s", item["ix"])
            continue

        contests_batch.append(build_contest_payload(item, detail, source_url))
        pending_state[source_url] = {"deadline": detail["end_date"]}

    if not contests_batch:
        log.info("보낼 변경 사항이 없습니다.")
        return

    result = backend_client.send_batch(contests_batch)
    log.info(
        "배치 전송 완료: created=%s updated=%s deadlineChanged=%s",
        result.get("created"), result.get("updated"), result.get("deadlineChanged"),
    )

    state.update(pending_state)
    state_store.save(state)


if __name__ == "__main__":
    main()
