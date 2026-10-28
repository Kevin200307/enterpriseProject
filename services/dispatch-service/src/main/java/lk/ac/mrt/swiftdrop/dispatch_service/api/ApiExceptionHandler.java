package lk.ac.mrt.swiftdrop.dispatch_service.api;

import java.net.URI;
import java.time.Instant;
import java.util.*;
import org.springframework.dao.*;
import org.springframework.http.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {
    public static ProblemDetail problem(int status, String message) {
        var problem = ProblemDetail.forStatusAndDetail(HttpStatusCode.valueOf(status),message);
        String code = switch (status) {
            case 400 -> "VALIDATION_FAILED"; case 401 -> "UNAUTHENTICATED"; case 403 -> "ACCESS_DENIED";
            case 404 -> "RESOURCE_NOT_FOUND"; case 409 -> "CONFLICT"; case 503 -> "UPSTREAM_UNAVAILABLE";
            default -> "INTERNAL_ERROR";
        };
        problem.setTitle(HttpStatus.valueOf(status).getReasonPhrase());
        problem.setType(URI.create("urn:swiftdrop:error:"+code.toLowerCase(Locale.ROOT).replace('_','-')));
        problem.setProperty("errorCode",code); problem.setProperty("timestamp",Instant.now());
        return problem;
    }
    @ExceptionHandler(DispatchException.class)
    ResponseEntity<ProblemDetail> domain(DispatchException ex) {
        var problem = problem(ex.status(),ex.getMessage());
        if (!ex.trackingNumbers().isEmpty()) problem.setProperty("trackingNumbers",ex.trackingNumbers());
        return ResponseEntity.status(ex.status()).body(problem);
    }
    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<ProblemDetail> denied(AccessDeniedException ex) {
        return ResponseEntity.status(403).body(problem(403,"This operation is not permitted"));
    }
    @ExceptionHandler({DataIntegrityViolationException.class, OptimisticLockingFailureException.class,
            PessimisticLockingFailureException.class})
    ResponseEntity<ProblemDetail> conflict(RuntimeException ex) {
        return ResponseEntity.status(409).body(problem(409,"Concurrent change or duplicate record; refresh and retry"));
    }
    @Override protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        var problem = problem(400,"One or more fields are invalid");
        problem.setProperty("violations",ex.getBindingResult().getFieldErrors().stream()
                .map(e -> Map.of("field",e.getField(),"message",Objects.toString(e.getDefaultMessage(),"Invalid value"))).toList());
        return ResponseEntity.badRequest().body(problem);
    }
    @Override protected ResponseEntity<Object> handleExceptionInternal(Exception ex, Object body,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        var problem = problem(status.value(),"The request could not be processed");
        return new ResponseEntity<>(problem,headers,status);
    }
    @ExceptionHandler(Exception.class) ResponseEntity<ProblemDetail> unexpected(Exception ex) {
        logger.error("Unhandled dispatch failure: " + ex.getClass().getName());
        return ResponseEntity.internalServerError().body(problem(500,"An unexpected error occurred"));
    }
}
