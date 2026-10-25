package lk.ac.mrt.swiftdrop.parcel_service.api;

import java.util.*;
import lk.ac.mrt.swiftdrop.contracts.error.ErrorCode;
import lk.ac.mrt.swiftdrop.contracts.error.FieldViolation;
import lk.ac.mrt.swiftdrop.contracts.error.ProblemFields;
import org.slf4j.*;
import org.springframework.dao.*;
import org.springframework.http.*;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {
    private static final Logger LOG = LoggerFactory.getLogger(ApiExceptionHandler.class);
    @ExceptionHandler(ParcelException.class)
    ResponseEntity<ProblemDetail> parcel(ParcelException ex) {
        return ResponseEntity.status(ex.code().httpStatus()).body(problem(ex.code(), ex.getMessage()));
    }
    @ExceptionHandler({OptimisticLockingFailureException.class, DataIntegrityViolationException.class})
    ResponseEntity<ProblemDetail> conflict(RuntimeException ex) {
        return ResponseEntity.status(409).body(problem(ErrorCode.CONFLICT,
            "The parcel changed or this booking was submitted concurrently. Retry the request."));
    }
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
        HttpHeaders headers, HttpStatusCode status, org.springframework.web.context.request.WebRequest request) {
        var detail = problem(ErrorCode.VALIDATION_FAILED, "One or more fields are invalid");
        detail.setProperty(ProblemFields.VIOLATIONS, ex.getBindingResult().getFieldErrors().stream()
            .map(e -> new FieldViolation(e.getField(), Objects.toString(e.getDefaultMessage(), "Invalid value"))).toList());
        return ResponseEntity.badRequest().body(detail);
    }
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception ex, Object body,
        HttpHeaders headers, HttpStatusCode status, org.springframework.web.context.request.WebRequest request) {
        ErrorCode code = status.value() == 413 ? ErrorCode.VALIDATION_FAILED : ErrorCode.MALFORMED_REQUEST;
        var detail = problem(code, status.value() == 413 ? "Maximum upload size exceeded" : "The request could not be processed");
        detail.setStatus(status.value());
        return new ResponseEntity<>(detail, headers, status);
    }
    @ExceptionHandler(Exception.class)
    ResponseEntity<ProblemDetail> unexpected(Exception ex) {
        // Do not log exception messages, request bodies, addresses or bearer tokens.
        LOG.error("Unhandled parcel failure: {}", ex.getClass().getName());
        return ResponseEntity.internalServerError().body(problem(ErrorCode.INTERNAL_ERROR, "An unexpected error occurred"));
    }
    public static ProblemDetail problem(ErrorCode code, String message) {
        var detail = ProblemDetail.forStatusAndDetail(HttpStatusCode.valueOf(code.httpStatus()), message);
        detail.setTitle(code.title());
        detail.setType(code.type());
        detail.setProperty(ProblemFields.ERROR_CODE, code.name());
        detail.setProperty(ProblemFields.TIMESTAMP, java.time.Instant.now());
        return detail;
    }
}
