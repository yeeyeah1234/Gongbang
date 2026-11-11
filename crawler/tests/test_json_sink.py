import json
import os

import config
import json_sink


def test_배치_결과를_json_파일로_저장한다(tmp_path, monkeypatch):
    monkeypatch.setattr(config, "OUTPUT_DIR", str(tmp_path))
    monkeypatch.setattr(json_sink, "OUTPUT_DIR", str(tmp_path))

    contests = [{"sourceUrl": "https://www.wevity.com/?ix=1", "title": "제목"}]
    path = json_sink.save(contests)

    assert os.path.exists(path)
    with open(path, "r", encoding="utf-8") as f:
        assert json.load(f) == contests
