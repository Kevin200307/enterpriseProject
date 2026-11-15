from datetime import UTC, datetime, timedelta
from types import SimpleNamespace

import jwt
import pytest
from cryptography.hazmat.primitives.asymmetric import rsa

from app.security.jwt import JwtTokenValidator, TokenValidationError

ISSUER = "https://identity.test"
AUDIENCE = "swiftdrop"


class StaticKeyProvider:
    def __init__(self, public_key: object) -> None:
        self.public_key = public_key

    def get_signing_key_from_jwt(self, token: str) -> object:
        return SimpleNamespace(key=self.public_key)


@pytest.fixture
def keys() -> tuple[object, object]:
    private = rsa.generate_private_key(public_exponent=65537, key_size=2048)
    return private, private.public_key()


def make_token(private_key: object, **overrides: object) -> str:
    now = datetime.now(UTC)
    claims: dict[str, object] = {
        "sub": "user-123",
        "roles": ["ROLE_CUSTOMER"],
        "iss": ISSUER,
        "aud": [AUDIENCE],
        "iat": now,
        "exp": now + timedelta(minutes=5),
    }
    claims.update(overrides)
    return jwt.encode(claims, private_key, algorithm="RS256")


def validator(public_key: object) -> JwtTokenValidator:
    return JwtTokenValidator(
        "https://unused.test/jwks",
        ISSUER,
        AUDIENCE,
        key_provider=StaticKeyProvider(public_key),
    )


def test_valid_token_extracts_normalized_roles(keys: tuple[object, object]) -> None:
    private, public = keys
    principal = validator(public).validate(make_token(private, roles=["ROLE_CUSTOMER", "MERCHANT"]))

    assert principal.subject == "user-123"
    assert principal.roles == frozenset({"CUSTOMER", "MERCHANT"})
    assert principal.has_role("ROLE_CUSTOMER")


@pytest.mark.parametrize(
    "overrides",
    [
        {"iss": "https://attacker.test"},
        {"aud": ["other"]},
        {"exp": datetime.now(UTC) - timedelta(seconds=1)},
    ],
)
def test_invalid_registered_claims_are_rejected(
    keys: tuple[object, object], overrides: dict[str, object]
) -> None:
    private, public = keys
    with pytest.raises(TokenValidationError):
        validator(public).validate(make_token(private, **overrides))


def test_unsigned_and_malformed_tokens_are_rejected(keys: tuple[object, object]) -> None:
    _, public = keys
    for token in ["not-a-token", jwt.encode({"sub": "user-123"}, key="", algorithm="none")]:
        with pytest.raises(TokenValidationError):
            validator(public).validate(token)


def test_roles_from_request_data_are_not_part_of_authentication(
    keys: tuple[object, object],
) -> None:
    private, public = keys
    principal = validator(public).validate(make_token(private, roles=[]))
    request_body = {"roles": ["ADMIN"]}

    assert not principal.has_role(request_body["roles"][0])
