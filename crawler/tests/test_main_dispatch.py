import pytest

import backend_client
import config
import json_sink
import main
import state_store
import wevity_client


def _item(ix="1", dday=5):
    return {"ix": ix, "title": "제목", "host": "주최", "categories": ["기타"], "dday": dday}


def _detail():
    return {
        "title": "상세 제목",
        "host": "상세 주최",
        "categories": ["기획/아이디어"],
        "thumbnail_url": None,
        "start_date": "2026-10-01",
        "end_date": "2026-12-06",
        "description": "설명",
        "homepage_url": None,
    }


def _patch_common(monkeypatch):
    monkeypatch.setattr(main, "collect_list_items", lambda: [_item()])
    # 분야 보충(CRW-01 ⑩)은 test_main.py에서 따로 확인한다. 여기서는 네트워크를 타지 않게 그대로 돌려준다.
    monkeypatch.setattr(main, "fill_category_minimum", lambda items: items)
    monkeypatch.setattr(state_store, "load", lambda: {})
    saved = {}
    monkeypatch.setattr(state_store, "save", lambda state: saved.update(state))
    monkeypatch.setattr(wevity_client, "fetch_detail_page", lambda ix: "<html></html>")
    monkeypatch.setattr(wevity_client, "parse_detail_page", lambda html: _detail())
    return saved


def test_json_모드에서는_백엔드를_호출하지_않고_파일로_저장한다(monkeypatch):
    saved = _patch_common(monkeypatch)
    monkeypatch.setattr(config, "OUTPUT_MODE", "json")

    saved_contests = {}
    monkeypatch.setattr(json_sink, "save", lambda contests: saved_contests.setdefault("v", contests))

    def _fail(*_args, **_kwargs):
        raise AssertionError("json 모드에서는 backend_client.send_batch가 호출되면 안 된다")

    monkeypatch.setattr(backend_client, "send_batch", _fail)

    main.main()

    assert saved_contests["v"][0]["sourceSite"] == "WEVITY"
    assert saved  # state_store.save가 호출되어 상태가 갱신됨


def test_backend_모드에서는_백엔드로_전송하고_파일로_저장하지_않는다(monkeypatch):
    _patch_common(monkeypatch)
    monkeypatch.setattr(config, "OUTPUT_MODE", "backend")

    sent = {}

    def _send_batch(contests):
        sent["v"] = contests
        return {"created": 1, "updated": 0, "deadlineChanged": 0}

    monkeypatch.setattr(backend_client, "send_batch", _send_batch)

    def _fail(*_args, **_kwargs):
        raise AssertionError("backend 모드에서는 json_sink.save가 호출되면 안 된다")

    monkeypatch.setattr(json_sink, "save", _fail)

    main.main()

    assert sent["v"][0]["sourceSite"] == "WEVITY"


def test_받을_수_없는_공모전만_빼고_나머지는_보낸다(monkeypatch):
    saved = _patch_common(monkeypatch)
    monkeypatch.setattr(config, "OUTPUT_MODE", "backend")
    monkeypatch.setattr(main, "collect_list_items", lambda: [_item("1"), _item("2")])

    def _parse(html):
        detail = _detail()
        if html == "too-long":
            detail["description"] = "가" * 30000  # 90000바이트 > TEXT
        return detail

    monkeypatch.setattr(wevity_client, "fetch_detail_page", lambda ix: "too-long" if ix == "1" else "ok")
    monkeypatch.setattr(wevity_client, "parse_detail_page", _parse)
    sent = {}

    def _send_batch(contests):
        sent["v"] = contests
        return {"created": 1, "updated": 0, "deadlineChanged": 0}

    monkeypatch.setattr(backend_client, "send_batch", _send_batch)

    main.main()

    assert [c["sourceUrl"] for c in sent["v"]] == [wevity_client.build_source_url("2")]
    # 뺀 공모전은 상태에 넣지 않아 다음 실행 때 다시 확인한다
    assert list(saved) == [wevity_client.build_source_url("2")]


def test_상세_페이지_전부_접수기간이_없으면_실패로_끝낸다(monkeypatch):
    saved = _patch_common(monkeypatch)
    monkeypatch.setattr(main, "collect_list_items", lambda: [_item(str(i)) for i in range(main.HEALTH_MIN_DETAILS)])
    monkeypatch.setattr(wevity_client, "parse_detail_page", lambda html: {**_detail(), "end_date": None})

    with pytest.raises(main.CrawlerHealthError):
        main.main()
    assert not saved


def test_상세_페이지_일부만_접수기간이_없으면_나머지는_보낸다(monkeypatch):
    _patch_common(monkeypatch)
    monkeypatch.setattr(config, "OUTPUT_MODE", "json")
    monkeypatch.setattr(main, "collect_list_items", lambda: [_item("1"), _item("2")])
    monkeypatch.setattr(wevity_client, "fetch_detail_page", lambda ix: ix)
    monkeypatch.setattr(
        wevity_client, "parse_detail_page", lambda ix: {**_detail(), "end_date": None if ix == "1" else "2026-12-06"}
    )
    saved_contests = {}
    monkeypatch.setattr(json_sink, "save", lambda contests: saved_contests.setdefault("v", contests))

    main.main()

    assert len(saved_contests["v"]) == 1


def test_상세_페이지가_적으면_접수기간이_없어도_실패로_보지_않는다(monkeypatch):
    _patch_common(monkeypatch)
    monkeypatch.setattr(main, "collect_list_items", lambda: [_item("1")])
    monkeypatch.setattr(wevity_client, "parse_detail_page", lambda html: {**_detail(), "end_date": None})

    main.main()  # 상시 모집 공모전 1건일 수 있으므로 예외 없이 끝난다
