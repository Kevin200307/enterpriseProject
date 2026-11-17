from contextlib import asynccontextmanager
from unittest.mock import AsyncMock, MagicMock

import pytest

from app import migrate
from app.persistence import checkpoints


@pytest.mark.parametrize("setup", [True, False])
async def test_checkpoint_setup_can_be_delegated_to_job(monkeypatch, setup):
    saver = MagicMock()
    saver.setup = AsyncMock()

    @asynccontextmanager
    async def connect(url):
        yield saver

    monkeypatch.setattr(checkpoints.AsyncPostgresSaver, "from_conn_string", connect)
    async with checkpoints.postgres_checkpointer("postgresql://unused", setup=setup) as connected:
        assert connected is saver
    assert saver.setup.await_count == int(setup)


def test_migration_runs_both_schemas_in_order(monkeypatch):
    monkeypatch.setenv("ASSISTANT_DATABASE_URL", "postgresql+asyncpg://unused")
    monkeypatch.setenv("CHECKPOINT_DATABASE_URL", "postgresql://unused")
    calls = []
    monkeypatch.setattr(migrate.command, "upgrade", lambda config, revision: calls.append(revision))

    @asynccontextmanager
    async def checkpointer(url):
        calls.append("checkpoint")
        yield None

    monkeypatch.setattr(migrate, "postgres_checkpointer", checkpointer)
    migrate.main()
    assert calls == ["head", "checkpoint"]


def test_missing_configuration_stops_before_migration(monkeypatch):
    monkeypatch.delenv("ASSISTANT_DATABASE_URL", raising=False)
    monkeypatch.delenv("CHECKPOINT_DATABASE_URL", raising=False)
    upgrade = MagicMock()
    monkeypatch.setattr(migrate.command, "upgrade", upgrade)
    with pytest.raises(SystemExit, match="Both assistant and checkpoint"):
        migrate.main()
    upgrade.assert_not_called()


def test_database_failure_stops_job_without_logging_credentials(monkeypatch, capsys):
    monkeypatch.setenv("ASSISTANT_DATABASE_URL", "postgresql+asyncpg://unused")
    monkeypatch.setenv("CHECKPOINT_DATABASE_URL", "postgresql://unused")
    monkeypatch.setattr(
        migrate.command, "upgrade", MagicMock(side_effect=RuntimeError("secret-password"))
    )
    with pytest.raises(SystemExit, match="Assistant migration failed") as failure:
        migrate.main()
    assert "secret-password" not in str(failure.value)
    captured = capsys.readouterr()
    assert "secret-password" not in captured.out + captured.err
