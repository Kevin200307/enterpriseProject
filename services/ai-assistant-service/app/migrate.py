"""Apply assistant and LangGraph schemas once, before Kubernetes starts replicas."""

import asyncio
import logging

from alembic import command
from alembic.config import Config

from app.config import Settings
from app.persistence.checkpoints import postgres_checkpointer


def main() -> None:
    settings = Settings()
    checkpoint_url = settings.checkpoint_database_url
    if settings.assistant_database_url is None or checkpoint_url is None:
        raise SystemExit("Both assistant and checkpoint database URLs are required for migrations.")

    # Database libraries may include connection details in exceptions. Keep Job logs redacted.
    previous_logging_disable = logging.root.manager.disable
    logging.disable(logging.CRITICAL)
    try:
        command.upgrade(Config("alembic.ini"), "head")

        async def setup_checkpoints() -> None:
            async with postgres_checkpointer(checkpoint_url.get_secret_value()):
                pass

        asyncio.run(setup_checkpoints())
    except Exception:
        raise SystemExit(
            "Assistant migration failed; inspect database and migration history."
        ) from None
    finally:
        logging.disable(previous_logging_disable)
    print("Assistant and checkpoint migrations completed.")


if __name__ == "__main__":
    main()
