package vn.edu.fpt.paspm.common;

import java.util.Map;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.mail.MailException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiError {
    @ExceptionHandler(ApiException.class)
    public ResponseEntity<Map<String, String>> api(ApiException exception) {
        return ResponseEntity.status(exception.status())
                .body(Map.of("error", exception.getMessage()));
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, HttpMessageNotReadableException.class})
    public ResponseEntity<Map<String, String>> input(Exception exception) {
        return ResponseEntity.badRequest()
                .body(Map.of("error", "Invalid request fields"));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, String>> conflict(Exception exception) {
        return ResponseEntity.status(409)
                .body(Map.of("error", "Resource already exists or violates a data constraint"));
    }

    @ExceptionHandler(MailException.class)
    public ResponseEntity<Map<String, String>> mail(Exception exception) {
        return ResponseEntity.status(503)
                .body(Map.of("error", "Email delivery unavailable; retry later"));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Map<String, String>> denied(Exception exception) {
        return ResponseEntity.status(403)
                .body(Map.of("error", "Forbidden"));
    }
}
