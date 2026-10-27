package lk.ac.mrt.swiftdrop.identity_service.api;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import lk.ac.mrt.swiftdrop.identity_service.service.JwtTokenService;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.BadCredentialsException;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class DispatchServiceTokenControllerTest {
    @Test void acceptsOnlyConfiguredClientCredentialsAndDisablesEmptySecrets() {
        var tokens=mock(JwtTokenService.class);
        when(tokens.createServiceToken(DispatchServiceTokenController.SUBJECT,"dispatch.internal")).thenReturn("signed-token");
        String secret="a-random-test-secret-at-least-32-characters";
        var controller=new DispatchServiceTokenController(secret,tokens);
        String basic="Basic "+Base64.getEncoder().encodeToString(("dispatch-service:"+secret).getBytes(StandardCharsets.UTF_8));
        var result=controller.token(basic,"client_credentials");
        assertThat(result.getBody().access_token()).isEqualTo("signed-token");
        assertThat(result.getHeaders().getCacheControl()).contains("no-store");
        for (String bad : new String[]{"","Basic invalid","Bearer token","Basic "+Base64.getEncoder().encodeToString("dispatch-service:wrong".getBytes(StandardCharsets.UTF_8))})
            assertThatThrownBy(() -> controller.token(bad,"client_credentials")).isInstanceOf(BadCredentialsException.class);
        assertThatThrownBy(() -> controller.token(basic,"password")).isInstanceOf(BadCredentialsException.class);
        assertThatThrownBy(() -> new DispatchServiceTokenController("",tokens).token("Basic ZGlzcGF0Y2gtc2VydmljZTo=","client_credentials"))
                .isInstanceOf(BadCredentialsException.class);
    }
}
