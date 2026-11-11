import os

import config
import state_store


def test_상태_파일이_없으면_빈_dict를_반환한다(tmp_path, monkeypatch):
    monkeypatch.setattr(config, "STATE_FILE_PATH", str(tmp_path / "missing.json"))
    monkeypatch.setattr(state_store, "STATE_FILE_PATH", str(tmp_path / "missing.json"))
    assert state_store.load() == {}


def test_저장한_상태를_다시_불러올_수_있다(tmp_path, monkeypatch):
    path = str(tmp_path / "state" / "contest_deadlines.json")
    monkeypatch.setattr(state_store, "STATE_FILE_PATH", path)

    state = {"https://www.wevity.com/?ix=1": {"deadline": "2026-12-06"}}
    state_store.save(state)

    assert os.path.exists(path)
    assert state_store.load() == state
