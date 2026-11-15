from dataclasses import dataclass


@dataclass(frozen=True, slots=True)
class AssistantPrincipal:
    subject: str
    roles: frozenset[str]
    token: str

    def has_role(self, role: str) -> bool:
        return normalize_role(role) in self.roles


def normalize_role(role: str) -> str:
    normalized = role.strip().upper()
    return normalized.removeprefix("ROLE_")
