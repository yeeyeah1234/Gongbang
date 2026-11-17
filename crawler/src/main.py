import datetime
import logging
import os

import backend_client
import config
import json_sink
import state_store
import wevity_client
from category_map import CODE_TO_WEVITY_CIDX, map_categories_to_code

os.makedirs(os.path.dirname(config.LOG_FILE_PATH), exist_ok=True)
logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s %(levelname)s %(message)s",
    handlers=[
        logging.StreamHandler(),
        logging.FileHandler(config.LOG_FILE_PATH, encoding="utf-8"),
    ],
)
log = logging.getLogger("crawler")

# 포트폴리오용 서비스라 최신 공모전 일부만 모은다 (목록 1페이지 15건, 2페이지면 약 30건).
MAX_PAGES = 2

# CRW-01 ⑩: 우리 분야마다 진행 중 공모전을 이만큼은 모은다. 모자라면 위비티 분야 필터 목록에서 채운다.
MIN_PER_CATEGORY = 4
# 분야 필터 하나당 볼 최대 목록 페이지 수 (요청 수를 작게 유지, CRW-01 ⑤)
FILL_MAX_PAGES_PER_FILTER = 2


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


def fill_category_minimum(items):
    """CRW-01 ⑩: 분야마다 MIN_PER_CATEGORY개가 안 되면 위비티 분야 필터 목록에서 더 모은다.

    같은 공모전(ix)은 두 번 넣지 않고, 우리 분야로 바꿨을 때 그 분야인 것만 채운다
    (위비티 분야 필터 목록에도 첫 분야가 다른 공모전이 섞여 있다). 위비티에 모자라면 있는 만큼만 모은다.
    """
    items = list(items)
    seen_ix = {item["ix"] for item in items}
    counts = {code: 0 for code in CODE_TO_WEVITY_CIDX}
    for item in items:
        code = map_categories_to_code(item["categories"])
        counts[code] = counts.get(code, 0) + 1

    for code, cidx_list in CODE_TO_WEVITY_CIDX.items():
        if counts[code] >= MIN_PER_CATEGORY:
            continue
        before = counts[code]
        for cidx in cidx_list:
            for page in range(1, FILL_MAX_PAGES_PER_FILTER + 1):
                if counts[code] >= MIN_PER_CATEGORY:
                    break
                page_items = wevity_client.parse_list_page(wevity_client.fetch_list_page(page, cidx))
                if not page_items:
                    break
                for item in page_items:
                    if counts[code] >= MIN_PER_CATEGORY:
                        break
                    if item["ix"] in seen_ix or map_categories_to_code(item["categories"]) != code:
                        continue
                    items.append(item)
                    seen_ix.add(item["ix"])
                    counts[code] += 1
            if counts[code] >= MIN_PER_CATEGORY:
                break

        if counts[code] > before:
            log.info("분야 보충: %s %d건 → %d건", code, before, counts[code])
        if counts[code] < MIN_PER_CATEGORY:
            log.warning("분야 보충: %s는 위비티에 진행 중 공모전이 %d건뿐", code, counts[code])
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
        "homepageUrl": detail["homepage_url"],
    }


def main():
    state = state_store.load()
    pending_state = {}
    contests_batch = []
    created = 0
    updated = 0
    deadline_changed = 0

    for item in fill_category_minimum(collect_list_items()):
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

        if cached_deadline is None:
            created += 1
        else:
            updated += 1
            if detail["end_date"] != cached_deadline:
                deadline_changed += 1

    if not contests_batch:
        log.info("보낼 변경 사항이 없습니다.")
        return

    if config.OUTPUT_MODE == "backend":
        remote_result = backend_client.send_batch(contests_batch)
        log.info(
            "배치 전송 완료: created=%s updated=%s deadlineChanged=%s",
            remote_result.get("created"),
            remote_result.get("updated"),
            remote_result.get("deadlineChanged"),
        )
    else:
        output_path = json_sink.save(contests_batch)
        log.info(
            "백엔드 미연동 - JSON 파일로 저장: %s (created=%s updated=%s deadlineChanged=%s)",
            output_path,
            created,
            updated,
            deadline_changed,
        )

    state.update(pending_state)
    state_store.save(state)


if __name__ == "__main__":
    main()
