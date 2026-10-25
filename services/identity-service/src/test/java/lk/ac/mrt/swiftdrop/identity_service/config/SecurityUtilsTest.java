package lk.ac.mrt.swiftdrop.identity_service.config;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SecurityUtilsTest {

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("Should extract UUID from authenticated Authentication object")
    void shouldExtractUserIdFromAuthentication() {
        UUID expectedId = UUID.randomUUID();
        Authentication auth = new UsernamePasswordAuthenticationToken(
                expectedId.toString(),
                "credentials",
                AuthorityUtils.createAuthorityList("ROLE_CUSTOMER")
        );

        UUID extracted = SecurityUtils.extractUserId(auth);

        assertThat(extracted).isEqualTo(expectedId);
    }

    @Test
    @DisplayName("Should extract UUID from SecurityContextHolder when authentication argument is null")
    void shouldExtractUserIdFromSecurityContextHolder() {
        UUID expectedId = UUID.randomUUID();
        Authentication auth = new UsernamePasswordAuthenticationToken(
                expectedId.toString(),
                "credentials",
                AuthorityUtils.createAuthorityList("ROLE_CUSTOMER")
        );
        SecurityContextHolder.getContext().setAuthentication(auth);

        UUID extracted = SecurityUtils.getAuthenticatedUserId();

        assertThat(extracted).isEqualTo(expectedId);
    }

    @Test
    @DisplayName("Should throw InsufficientAuthenticationException when authentication is null")
    void shouldThrowWhenAuthenticationIsNull() {
        assertThatThrownBy(() -> SecurityUtils.extractUserId(null))
                .isInstanceOf(InsufficientAuthenticationException.class)
                .hasMessageContaining("Full authentication is required");
    }

    @Test
    @DisplayName("Should throw InsufficientAuthenticationException when authentication is anonymous")
    void shouldThrowWhenAuthenticationIsAnonymous() {
        AnonymousAuthenticationToken anonymous = new AnonymousAuthenticationToken(
                "key",
                "anonymousUser",
                AuthorityUtils.createAuthorityList("ROLE_ANONYMOUS")
        );

        assertThatThrownBy(() -> SecurityUtils.extractUserId(anonymous))
                .isInstanceOf(InsufficientAuthenticationException.class)
                .hasMessageContaining("Full authentication is required");
    }

    @Test
    @DisplayName("Should throw InsufficientAuthenticationException when principal is not a valid UUID")
    void shouldThrowWhenPrincipalIsNotAValidUuid() {
        Authentication auth = new UsernamePasswordAuthenticationToken(
                "not-a-uuid-string",
                "credentials",
                AuthorityUtils.createAuthorityList("ROLE_CUSTOMER")
        );

        assertThatThrownBy(() -> SecurityUtils.extractUserId(auth))
                .isInstanceOf(InsufficientAuthenticationException.class)
                .hasMessageContaining("Invalid user identifier");
    }
}
