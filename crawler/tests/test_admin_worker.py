from unittest.mock import Mock
import pytest
import admin_worker


def test_no_job_does_not_run_crawler(monkeypatch):
    monkeypatch.setattr(admin_worker.config, "OUTPUT_MODE", "backend")
    call = Mock(return_value=None)
    run = Mock()
    monkeypatch.setattr(admin_worker, "call", call)
    monkeypatch.setattr(admin_worker.crawler, "main", run)
    assert admin_worker.run_once() is False
    run.assert_not_called()


@pytest.mark.parametrize("failed", [False, True])
def test_claimed_job_reports_result_and_forces_detail_refresh(monkeypatch, failed):
    monkeypatch.setattr(admin_worker.config, "OUTPUT_MODE", "backend")
    call = Mock(side_effect=[{"id": 12}, None])
    run = Mock(side_effect=RuntimeError("failed") if failed else None)
    monkeypatch.setattr(admin_worker, "call", call)
    monkeypatch.setattr(admin_worker.crawler, "main", run)
    if failed:
        with pytest.raises(RuntimeError):
            admin_worker.run_once()
    else:
        assert admin_worker.run_once() is True
    run.assert_called_once_with(force=True)
    call.assert_any_call("/12/finish", {"success": not failed})


def test_json_mode_cannot_consume_job(monkeypatch):
    monkeypatch.setattr(admin_worker.config, "OUTPUT_MODE", "json")
    call = Mock()
    monkeypatch.setattr(admin_worker, "call", call)
    with pytest.raises(RuntimeError):
        admin_worker.run_once()
    call.assert_not_called()
