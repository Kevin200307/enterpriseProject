package lk.ac.mrt.swiftdrop.identity_service.error;

import lk.ac.mrt.swiftdrop.contracts.error.ErrorCode;
import lk.ac.mrt.swiftdrop.contracts.error.FieldViolation;
import lk.ac.mrt.swiftdrop.contracts.error.ProblemFields;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

import java.lang.reflect.Method;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler exceptionHandler;
    private MockHttpServletRequest request;

    @BeforeEach
    void setUp() {
        exceptionHandler = new GlobalExceptionHandler();
        request = new MockHttpServletRequest();
        request.setRequestURI("/api/v1/auth/login");
        request.addHeader("X-Request-Id", "test-trace-12345");
    }

    @Test
    @DisplayName("Should map InvalidCredentialsException to 401 ProblemDetail with RFC 7807 contract")
    void shouldHandleInvalidCredentials() {
        InvalidCredentialsException ex = new InvalidCredentialsException();

        ResponseEntity<ProblemDetail> response = exceptionHandler.handleAppException(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        ProblemDetail body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.getStatus()).isEqualTo(401);
        assertThat(body.getType()).isEqualTo(ErrorCode.INVALID_CREDENTIALS.type());
        assertThat(body.getTitle()).isEqualTo(ErrorCode.INVALID_CREDENTIALS.title());
        assertThat(body.getDetail()).isEqualTo("Invalid email or password");
        assertThat(body.getProperties()).containsEntry(ProblemFields.ERROR_CODE, "INVALID_CREDENTIALS");
        assertThat(body.getProperties()).containsEntry(ProblemFields.TRACE_ID, "test-trace-12345");
        assertThat(body.getProperties()).containsKey(ProblemFields.TIMESTAMP);
    }

    @Test
    @DisplayName("Should map RateLimitExceededException to 429 with Retry-After header")
    void shouldHandleRateLimitExceeded() {
        RateLimitExceededException ex = new RateLimitExceededException(60);

        ResponseEntity<ProblemDetail> response = exceptionHandler.handleAppException(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(response.getHeaders().getFirst(HttpHeaders.RETRY_AFTER)).isEqualTo("60");
        ProblemDetail body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.getStatus()).isEqualTo(429);
        assertThat(body.getProperties()).containsEntry(ProblemFields.ERROR_CODE, "RATE_LIMITED");
    }

    @Test
    @DisplayName("Should map DuplicateEmailException to 409 Conflict")
    void shouldHandleDuplicateEmail() {
        DuplicateEmailException ex = new DuplicateEmailException();

        ResponseEntity<ProblemDetail> response = exceptionHandler.handleAppException(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        ProblemDetail body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.getStatus()).isEqualTo(409);
        assertThat(body.getProperties()).containsEntry(ProblemFields.ERROR_CODE, "CONFLICT");
    }

    @Test
    @DisplayName("Should map UserSuspendedException to 403 Forbidden")
    void shouldHandleUserSuspended() {
        UserSuspendedException ex = new UserSuspendedException();

        ResponseEntity<ProblemDetail> response = exceptionHandler.handleAppException(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
        ProblemDetail body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.getStatus()).isEqualTo(403);
        assertThat(body.getProperties()).containsEntry(ProblemFields.ERROR_CODE, "ACCESS_DENIED");
    }

    @Test
    @DisplayName("Should map MethodArgumentNotValidException to 400 with FieldViolations")
    void shouldHandleValidationException() throws Exception {
        Object target = new Object();
        BeanPropertyBindingResult bindingResult = new BeanPropertyBindingResult(target, "registerRequest");
        bindingResult.addError(new FieldError("registerRequest", "email", "must be a well-formed email address"));
        bindingResult.addError(new FieldError("registerRequest", "phone", "must match Sri Lankan phone format"));

        Method method = getClass().getDeclaredMethod("shouldHandleValidationException");
        MethodParameter parameter = new MethodParameter(method, -1);
        MethodArgumentNotValidException ex = new MethodArgumentNotValidException(parameter, bindingResult);

        ResponseEntity<ProblemDetail> response = exceptionHandler.handleValidationException(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        ProblemDetail body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.getStatus()).isEqualTo(400);
        assertThat(body.getProperties()).containsEntry(ProblemFields.ERROR_CODE, "VALIDATION_FAILED");

        @SuppressWarnings("unchecked")
        List<FieldViolation> violations = (List<FieldViolation>) body.getProperties().get(ProblemFields.VIOLATIONS);
        assertThat(violations).isNotNull().hasSize(2);
        assertThat(violations).extracting(FieldViolation::field).containsExactlyInAnyOrder("email", "phone");
    }

    @Test
    @DisplayName("Should map generic Exception to 500 without leaking sensitive messages")
    void shouldHandleUnhandledException() {
        Exception ex = new RuntimeException("Database connection timed out or credentials invalid");

        ResponseEntity<ProblemDetail> response = exceptionHandler.handleUnhandledException(ex, request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        ProblemDetail body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.getStatus()).isEqualTo(500);
        assertThat(body.getDetail()).isEqualTo("An unexpected internal error occurred");
        assertThat(body.getProperties()).containsEntry(ProblemFields.ERROR_CODE, "INTERNAL_ERROR");
        assertThat(body.getProperties()).containsEntry(ProblemFields.TRACE_ID, "test-trace-12345");
    }
}
