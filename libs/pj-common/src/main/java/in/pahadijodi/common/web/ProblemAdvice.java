package in.pahadijodi.common.web;

import java.net.URI;
import java.util.List;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** RFC 7807 problem+json for validation and domain errors (docs/05 error catalogue). */
@AutoConfiguration
@RestControllerAdvice
public class ProblemAdvice {

    public static class PjException extends RuntimeException {
        private final HttpStatus status;
        private final String code;
        public PjException(HttpStatus status, String code, String message) {
            super(message);
            this.status = status;
            this.code = code;
        }
        public HttpStatus status() { return status; }
        public String code() { return code; }
    }

    @ExceptionHandler(PjException.class)
    ProblemDetail pj(PjException e) {
        var p = ProblemDetail.forStatusAndDetail(e.status(), e.getMessage());
        p.setType(URI.create("https://errors.pahadijodi.in/" + e.code()));
        p.setProperty("code", e.code());
        return p;
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail invalid(MethodArgumentNotValidException e) {
        var p = ProblemDetail.forStatusAndDetail(HttpStatus.UNPROCESSABLE_ENTITY, "Validation failed");
        p.setType(URI.create("https://errors.pahadijodi.in/VALIDATION_FAILED"));
        p.setProperty("code", "VALIDATION_FAILED");
        List<String> errs = e.getBindingResult().getFieldErrors().stream()
                .map(f -> f.getField() + ": " + f.getDefaultMessage()).toList();
        p.setProperty("errors", errs);
        return p;
    }
}
