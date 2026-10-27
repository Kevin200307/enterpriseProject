package lk.ac.mrt.swiftdrop.identity_service.api;

import lk.ac.mrt.swiftdrop.contracts.error.ErrorCode;
import lk.ac.mrt.swiftdrop.identity_service.config.GoogleOAuth2Properties;
import lk.ac.mrt.swiftdrop.identity_service.config.SecurityConfig;
import lk.ac.mrt.swiftdrop.identity_service.error.GlobalExceptionHandler;
import lk.ac.mrt.swiftdrop.identity_service.service.OAuth2StateService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = OAuth2Controller.class)
@Import({GlobalExceptionHandler.class, SecurityConfig.class})
class OAuth2ControllerAuthorizeTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private OAuth2StateService oAuth2StateService;

    @MockitoBean
    private GoogleOAuth2Properties googleOAuth2Properties;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @BeforeEach
    void setUp() {
        when(googleOAuth2Properties.clientId()).thenReturn("google-client-id-12345");
        when(googleOAuth2Properties.authorizationUri()).thenReturn("https://accounts.google.com/o/oauth2/v2/auth");
        when(googleOAuth2Properties.redirectUri()).thenReturn("http://localhost:5173/oauth/callback");
    }

    @Test
    @DisplayName("GET /api/v1/auth/oauth2/google/authorize - 200 OK with custom redirect URI")
    void shouldGenerateGoogleAuthorizationUrlWithCustomRedirectUri() throws Exception {
        String codeChallenge = "E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM";
        String customRedirectUri = "https://app.swiftdrop.lk/custom-callback";
        String generatedState = "generated-secure-state-token-xyz";

        when(oAuth2StateService.generateAndStoreState(codeChallenge, customRedirectUri))
                .thenReturn(generatedState);

        mockMvc.perform(get("/api/v1/auth/oauth2/google/authorize")
                        .param("codeChallenge", codeChallenge)
                        .param("redirectUri", customRedirectUri)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.state").value(generatedState))
                .andExpect(jsonPath("$.authorizationUrl", containsString("https://accounts.google.com/o/oauth2/v2/auth")))
                .andExpect(jsonPath("$.authorizationUrl", containsString("response_type=code")))
                .andExpect(jsonPath("$.authorizationUrl", containsString("client_id=google-client-id-12345")))
                .andExpect(jsonPath("$.authorizationUrl", containsString("redirect_uri=https://app.swiftdrop.lk/custom-callback")))
                .andExpect(jsonPath("$.authorizationUrl", containsString("state=" + generatedState)))
                .andExpect(jsonPath("$.authorizationUrl", containsString("code_challenge=" + codeChallenge)))
                .andExpect(jsonPath("$.authorizationUrl", containsString("code_challenge_method=S256")));
    }

    @Test
    @DisplayName("GET /api/v1/auth/oauth2/google/authorize - 200 OK with default redirect URI when omitted")
    void shouldUseDefaultRedirectUriWhenNotProvided() throws Exception {
        String codeChallenge = "E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM";
        String defaultRedirectUri = "http://localhost:5173/oauth/callback";
        String generatedState = "generated-secure-state-default";

        when(oAuth2StateService.generateAndStoreState(codeChallenge, defaultRedirectUri))
                .thenReturn(generatedState);

        mockMvc.perform(get("/api/v1/auth/oauth2/google/authorize")
                        .param("codeChallenge", codeChallenge)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.state").value(generatedState))
                .andExpect(jsonPath("$.authorizationUrl", containsString("redirect_uri=http://localhost:5173/oauth/callback")));
    }

    @Test
    @DisplayName("GET /api/v1/auth/oauth2/google/authorize - 400 Bad Request when codeChallenge parameter is missing")
    void shouldRejectMissingCodeChallengeWith400ProblemDetail() throws Exception {
        mockMvc.perform(get("/api/v1/auth/oauth2/google/authorize")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.errorCode").value(ErrorCode.VALIDATION_FAILED.name()))
                .andExpect(jsonPath("$.violations[0].field").value("codeChallenge"));
    }

    @Test
    @DisplayName("GET /api/v1/auth/oauth2/google/authorize - 400 Bad Request when codeChallenge is blank")
    void shouldRejectBlankCodeChallengeWith400ProblemDetail() throws Exception {
        mockMvc.perform(get("/api/v1/auth/oauth2/google/authorize")
                        .param("codeChallenge", "   ")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.errorCode").value(ErrorCode.VALIDATION_FAILED.name()));
    }
}
