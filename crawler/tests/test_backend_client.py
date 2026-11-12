import backend_client


class _FakeResponse:
    def raise_for_status(self):
        pass

    def json(self):
        return {
            "success": True,
            "data": {"created": 3, "updated": 1, "deadlineChanged": 0},
            "error": None,
        }


def test_공통_응답의_data_안에서_결과를_꺼낸다(monkeypatch):
    monkeypatch.setattr(backend_client.requests, "post", lambda *args, **kwargs: _FakeResponse())

    result = backend_client.send_batch([{"sourceUrl": "https://www.wevity.com/?ix=1"}])

    assert result == {"created": 3, "updated": 1, "deadlineChanged": 0}
