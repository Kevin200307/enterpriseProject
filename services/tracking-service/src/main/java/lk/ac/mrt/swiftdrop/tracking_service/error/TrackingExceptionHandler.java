package lk.ac.mrt.swiftdrop.tracking_service.error;

import jakarta.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import lk.ac.mrt.swiftdrop.contracts.error.ErrorCode;
import lk.ac.mrt.swiftdrop.contracts.error.ProblemFields;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@Slf4j
@RestControllerAdvice
public class TrackingExceptionHandler {

    @ExceptionHandler(TrackingException.class)
    public ResponseEntity<Map<String, Object>> handleTrackingException(TrackingException ex, HttpServletRequest request) {
        ErrorCode code = ex.getErrorCode();
        return buildResponse(code, ex.getMessage(), request);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArgument(IllegalArgumentException ex, HttpServletRequest request) {
        return buildResponse(ErrorCode.VALIDATION_FAILED, ex.getMessage(), request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGenericException(Exception ex, HttpServletRequest request) {
        log.error("Unhandled exception processing request {}: {}", request.getRequestURI(), ex.getMessage(), ex);
        return buildResponse(ErrorCode.INTERNAL_ERROR, "An unexpected error occurred", request);
    }

    private ResponseEntity<Map<String, Object>> buildResponse(ErrorCode code, String detail, HttpServletRequest request) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put(ProblemFields.TYPE, code.type().toString());
        body.put(ProblemFields.TITLE, code.title());
        body.put(ProblemFields.STATUS, code.httpStatus());
        body.put(ProblemFields.DETAIL, detail);
        body.put(ProblemFields.INSTANCE, request.getRequestURI());
        body.put(ProblemFields.ERROR_CODE, code.name());
        body.put(ProblemFields.TIMESTAMP, Instant.now().toString());

        String requestId = request.getHeader("X-Request-Id");
        if (requestId != null && !requestId.isBlank()) {
            body.put(ProblemFields.TRACE_ID, requestId);
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_PROBLEM_JSON);
        if (requestId != null && !requestId.isBlank()) {
            headers.set("X-Request-Id", requestId);
        }

        return new ResponseEntity<>(body, headers, HttpStatusCode.valueOf(code.httpStatus()));
    }
}
