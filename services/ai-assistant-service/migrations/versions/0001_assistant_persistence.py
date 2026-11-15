"""Create assistant application tables."""

from collections.abc import Sequence

from alembic import op

revision = "0001_assistant_persistence"
down_revision = None
branch_labels: Sequence[str] | None = None
depends_on: Sequence[str] | None = None


def upgrade() -> None:
    # Metadata owns the exact schema; create_all is avoided in deployed startup.
    bind = op.get_bind()
    from app.persistence.models import Base

    Base.metadata.create_all(bind)


def downgrade() -> None:
    bind = op.get_bind()
    from app.persistence.models import Base

    Base.metadata.drop_all(bind)
