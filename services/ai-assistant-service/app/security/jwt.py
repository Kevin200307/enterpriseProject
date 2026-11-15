from collections.abc import Mapping, Sequence
from typing import Any, Protocol

import jwt
from jwt import PyJWKClient
from jwt.exceptions import InvalidTokenError

from app.security.principal import AssistantPrincipal, normalize_role


class SigningKeyProvider(Protocol):
    def get_signing_key_from_jwt(self, token: str) -> Any: ...


class TokenValidationError(ValueError):
    pass


class JwtTokenValidator:
    def __init__(
        self,
        jwks_uri: str,
        issuer: str,
        audience: str,
        *,
        key_provider: SigningKeyProvider | None = None,
    ) -> None:
        self._issuer = issuer
        self._audience = audience
        self._keys = key_provider or PyJWKClient(jwks_uri, cache_keys=True, lifespan=300)

    def validate(self, token: str) -> AssistantPrincipal:
        try:
            signing_key = self._keys.get_signing_key_from_jwt(token)
            claims = jwt.decode(
                token,
                signing_key.key,
                algorithms=["RS256"],
                audience=self._audience,
                issuer=self._issuer,
                options={"require": ["sub", "iss", "aud", "exp"]},
            )
        except (InvalidTokenError, ValueError, TypeError, OSError) as error:
            raise TokenValidationError("Invalid bearer token") from error

        subject = claims.get("sub")
        if not isinstance(subject, str) or not subject.strip():
            raise TokenValidationError("Invalid bearer token")

        roles_claim = claims.get("roles", [])
        roles = self._parse_roles(roles_claim)
        return AssistantPrincipal(subject=subject, roles=roles, token=token)

    @staticmethod
    def _parse_roles(value: object) -> frozenset[str]:
        if not isinstance(value, Sequence) or isinstance(value, str | bytes):
            return frozenset()
        return frozenset(
            normalize_role(role) for role in value if isinstance(role, str) and normalize_role(role)
        )


def safe_claims(token: str) -> Mapping[str, Any]:
    """Test/debug helper that never verifies or authorizes a token."""
    try:
        decoded = jwt.decode(token, options={"verify_signature": False})
    except InvalidTokenError:
        return {}
    return decoded if isinstance(decoded, Mapping) else {}
