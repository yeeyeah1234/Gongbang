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
