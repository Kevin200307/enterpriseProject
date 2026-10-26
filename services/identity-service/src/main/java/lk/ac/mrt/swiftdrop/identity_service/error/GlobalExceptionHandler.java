package lk.ac.mrt.swiftdrop.identity_service.error;

import jakarta.servlet.http.HttpServletRequest;
import lk.ac.mrt.swiftdrop.contracts.error.ErrorCode;
import lk.ac.mrt.swiftdrop.contracts.error.FieldViolation;
import lk.ac.mrt.swiftdrop.contracts.error.ProblemFields;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Global REST exception handler producing standard RFC 7807 application/problem+json responses
 * matching the shared contracts (ErrorCode, ProblemFields, FieldViolation).
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private static final MediaType PROBLEM_JSON_MEDIA_TYPE = MediaType.parseMediaType("application/problem+json");

    @ExceptionHandler(AppException.class)
    public ResponseEntity<ProblemDetail> handleAppException(AppException ex, HttpServletRequest request) {
        ErrorCode errorCode = ex.getErrorCode();
        HttpHeaders headers = new HttpHeaders();

        if (ex instanceof RateLimitExceededException rateLimitEx) {
            headers.set(HttpHeaders.RETRY_AFTER, String.valueOf(rateLimitEx.getRetryAfterSeconds()));
        }

        ProblemDetail problemDetail = buildProblemDetail(
                errorCode,
                ex.getMessage(),
                request,
                null
        );

        return ResponseEntity
                .status(errorCode.httpStatus())
                .contentType(PROBLEM_JSON_MEDIA_TYPE)
                .headers(headers)
                .body(problemDetail);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ProblemDetail> handleValidationException(
            MethodArgumentNotValidException ex,
            HttpServletRequest request
    ) {
        List<FieldViolation> violations = new ArrayList<>();
        for (FieldError fieldError : ex.getBindingResult().getFieldErrors()) {
            violations.add(new FieldViolation(fieldError.getField(), fieldError.getDefaultMessage()));
        }

        ProblemDetail problemDetail = buildProblemDetail(
                ErrorCode.VALIDATION_FAILED,
                "Request body validation failed",
                request,
                violations
        );

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .contentType(PROBLEM_JSON_MEDIA_TYPE)
                .body(problemDetail);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ProblemDetail> handleHttpMessageNotReadable(
            HttpMessageNotReadableException ex,
            HttpServletRequest request
    ) {
        log.warn("Malformed JSON payload received: {}", ex.getMessage());
        ProblemDetail problemDetail = buildProblemDetail(
                ErrorCode.MALFORMED_REQUEST,
                "Malformed request body",
                request,
                null
        );

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .contentType(PROBLEM_JSON_MEDIA_TYPE)
                .body(problemDetail);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ProblemDetail> handleAccessDenied(
            AccessDeniedException ex,
            HttpServletRequest request
    ) {
        ProblemDetail problemDetail = buildProblemDetail(
                ErrorCode.ACCESS_DENIED,
                "Access denied: insufficient permissions",
                request,
                null
        );

        return ResponseEntity
                .status(HttpStatus.FORBIDDEN)
                .contentType(PROBLEM_JSON_MEDIA_TYPE)
                .body(problemDetail);
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ProblemDetail> handleAuthenticationException(
            AuthenticationException ex,
            HttpServletRequest request
    ) {
        ProblemDetail problemDetail = buildProblemDetail(
                ErrorCode.UNAUTHENTICATED,
                "Full authentication is required to access this resource",
                request,
                null
        );

        return ResponseEntity
                .status(HttpStatus.UNAUTHORIZED)
                .contentType(PROBLEM_JSON_MEDIA_TYPE)
                .body(problemDetail);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ProblemDetail> handleNotFound(
            NoResourceFoundException ex,
            HttpServletRequest request
    ) {
        ProblemDetail problemDetail = buildProblemDetail(
                ErrorCode.RESOURCE_NOT_FOUND,
                ex.getMessage(),
                request,
                null
        );

        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .contentType(PROBLEM_JSON_MEDIA_TYPE)
                .body(problemDetail);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ProblemDetail> handleMethodNotSupported(
            HttpRequestMethodNotSupportedException ex,
            HttpServletRequest request
    ) {
        ProblemDetail problemDetail = buildProblemDetail(
                ErrorCode.MALFORMED_REQUEST,
                ex.getMessage(),
                request,
                null
        );

        return ResponseEntity
                .status(HttpStatus.METHOD_NOT_ALLOWED)
                .contentType(PROBLEM_JSON_MEDIA_TYPE)
                .body(problemDetail);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handleUnhandledException(
            Exception ex,
            HttpServletRequest request
    ) {
        String traceId = extractOrCreateTraceId(request);
        log.error("Unhandled internal server error [traceId={}]", traceId, ex);

        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatusCode.valueOf(ErrorCode.INTERNAL_ERROR.httpStatus()),
                "An unexpected internal error occurred"
        );
        problemDetail.setType(ErrorCode.INTERNAL_ERROR.type());
        problemDetail.setTitle(ErrorCode.INTERNAL_ERROR.title());
        problemDetail.setInstance(URI.create(request.getRequestURI()));
        problemDetail.setProperty(ProblemFields.ERROR_CODE, ErrorCode.INTERNAL_ERROR.name());
        problemDetail.setProperty(ProblemFields.TRACE_ID, traceId);
        problemDetail.setProperty(ProblemFields.TIMESTAMP, Instant.now().toString());

        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .contentType(PROBLEM_JSON_MEDIA_TYPE)
                .body(problemDetail);
    }

    private ProblemDetail buildProblemDetail(
            ErrorCode errorCode,
            String detail,
            HttpServletRequest request,
            List<FieldViolation> violations
    ) {
        String traceId = extractOrCreateTraceId(request);
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatusCode.valueOf(errorCode.httpStatus()),
                detail
        );
        problemDetail.setType(errorCode.type());
        problemDetail.setTitle(errorCode.title());
        problemDetail.setInstance(URI.create(request.getRequestURI()));
        problemDetail.setProperty(ProblemFields.ERROR_CODE, errorCode.name());
        problemDetail.setProperty(ProblemFields.TRACE_ID, traceId);
        problemDetail.setProperty(ProblemFields.TIMESTAMP, Instant.now().toString());

        if (violations != null && !violations.isEmpty()) {
            problemDetail.setProperty(ProblemFields.VIOLATIONS, violations);
        }

        return problemDetail;
    }

    private String extractOrCreateTraceId(HttpServletRequest request) {
        String traceId = request.getHeader("X-Request-Id");
        if (traceId != null && !traceId.isBlank()) {
            return traceId;
        }
        return UUID.randomUUID().toString();
    }
}
