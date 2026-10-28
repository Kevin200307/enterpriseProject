package lk.ac.mrt.swiftdrop.pricing_service.api;

import java.util.Objects;
import lk.ac.mrt.swiftdrop.contracts.error.ErrorCode;
import lk.ac.mrt.swiftdrop.contracts.error.FieldViolation;
import lk.ac.mrt.swiftdrop.contracts.error.ProblemFields;
import lk.ac.mrt.swiftdrop.pricing_service.service.PricingException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.*;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@RestControllerAdvice
public class PricingExceptionHandler extends ResponseEntityExceptionHandler {
    private static final Logger LOG = LoggerFactory.getLogger(PricingExceptionHandler.class);

    @ExceptionHandler(PricingException.class)
    ResponseEntity<ProblemDetail> pricing(PricingException ex) {
        return ResponseEntity.status(ex.code().httpStatus()).body(problem(ex.code(), ex.getMessage()));
    }

    @ExceptionHandler({DataIntegrityViolationException.class, org.springframework.orm.ObjectOptimisticLockingFailureException.class})
    ResponseEntity<ProblemDetail> conflict(RuntimeException ex) {
        return ResponseEntity.status(409).body(problem(ErrorCode.CONFLICT,
            "The pricing configuration conflicts with an existing record. Refresh and retry."));
    }

    @ExceptionHandler(org.springframework.security.access.AccessDeniedException.class)
    ResponseEntity<ProblemDetail> denied() {
        return ResponseEntity.status(403).body(problem(ErrorCode.ACCESS_DENIED, "This operation is not permitted"));
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
            HttpHeaders headers, HttpStatusCode status, org.springframework.web.context.request.WebRequest request) {
        var detail = problem(ErrorCode.VALIDATION_FAILED, "One or more fields are invalid");
        detail.setProperty(ProblemFields.VIOLATIONS, ex.getBindingResult().getFieldErrors().stream()
            .map(error -> new FieldViolation(error.getField(), Objects.toString(error.getDefaultMessage(), "Invalid value"))).toList());
        return ResponseEntity.badRequest().body(detail);
    }

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception ex, Object body, HttpHeaders headers,
            HttpStatusCode status, org.springframework.web.context.request.WebRequest request) {
        ErrorCode code = status.is4xxClientError() ? ErrorCode.MALFORMED_REQUEST : ErrorCode.INTERNAL_ERROR;
        var detail = problem(code, "The request could not be processed");
        detail.setStatus(status.value());
        return new ResponseEntity<>(detail, headers, status);
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ProblemDetail> unexpected(Exception ex) {
        LOG.error("Unhandled pricing failure: {}", ex.getClass().getName());
        return ResponseEntity.internalServerError().body(problem(ErrorCode.INTERNAL_ERROR, "An unexpected error occurred"));
    }

    public static ProblemDetail problem(ErrorCode code, String message) {
        var detail = ProblemDetail.forStatusAndDetail(HttpStatusCode.valueOf(code.httpStatus()), message);
        detail.setTitle(code.title());
        detail.setType(code.type());
        detail.setProperty(ProblemFields.ERROR_CODE, code.name());
        detail.setProperty(ProblemFields.TRACE_ID, java.util.UUID.randomUUID().toString());
        detail.setProperty(ProblemFields.TIMESTAMP, java.time.Instant.now());
        return detail;
    }
}