from typing import Annotated

from fastapi import Depends, HTTPException, Request, status
from fastapi.security import HTTPAuthorizationCredentials, HTTPBearer

from app.security.jwt import JwtTokenValidator, TokenValidationError
from app.security.principal import AssistantPrincipal

bearer = HTTPBearer(auto_error=False)


def get_token_validator(request: Request) -> JwtTokenValidator:
    validator: JwtTokenValidator = request.app.state.token_validator
    return validator


async def require_principal(
    credentials: Annotated[HTTPAuthorizationCredentials | None, Depends(bearer)],
    validator: Annotated[JwtTokenValidator, Depends(get_token_validator)],
) -> AssistantPrincipal:
    if credentials is None or credentials.scheme.lower() != "bearer":
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Authentication is required",
            headers={"WWW-Authenticate": "Bearer"},
        )
    try:
        return validator.validate(credentials.credentials)
    except TokenValidationError as error:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Invalid bearer token",
            headers={"WWW-Authenticate": "Bearer"},
        ) from error
