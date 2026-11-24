package vn.edu.fpt.paspm.common;

import java.util.Map;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.mail.MailException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import vn.edu.fpt.paspm.catalog.product.ProductDeleteBlockedException;
import vn.edu.fpt.paspm.catalog.product.ProductNameConfirmationRequiredException;

@RestControllerAdvice
public class ApiError {
    @ExceptionHandler(UnsupportedScoringVersion.class)
    public ResponseEntity<Map<String,String>> unsupportedVersion(UnsupportedScoringVersion exception) {
        org.slf4j.LoggerFactory.getLogger(ApiError.class).error("Stored scoring model is unsupported",exception);
        return ResponseEntity.status(exception.status()).body(Map.of("code",UnsupportedScoringVersion.CODE,"error",exception.getMessage()));
    }

    @ExceptionHandler(ProductNameConfirmationRequiredException.class)
    public ResponseEntity<Map<String, Object>> productNameConfirmation(
            ProductNameConfirmationRequiredException exception) {
        return ResponseEntity.status(exception.status()).body(Map.of(
                "code", ProductNameConfirmationRequiredException.CODE,
                "error", exception.getMessage(),
                "confirmWith", "confirmSimilarName=true"));
    }

    @ExceptionHandler(ProductDeleteBlockedException.class)
    public ResponseEntity<Map<String, Object>> productDeleteBlocked(ProductDeleteBlockedException exception) {
        return ResponseEntity.status(exception.status()).body(Map.of(
                "code", exception.code(),
                "error", exception.getMessage(),
                "reason", exception.reason(),
                "sellableQuantity", exception.sellableQuantity(),
                "campaigns", exception.campaigns()));
    }

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<Map<String, String>> api(ApiException exception) {
        return ResponseEntity.status(exception.status())
                .body(exception.code() == null
                        ? Map.of("error", exception.getMessage())
                        : Map.of("error", exception.getMessage(), "code", exception.code()));
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

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<Map<String, String>> uploadTooLarge(MaxUploadSizeExceededException exception) {
        return ResponseEntity.status(413)
                .body(Map.of("error", "Uploaded file exceeds the 5 MiB limit"));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Map<String, String>> denied(Exception exception) {
        return ResponseEntity.status(403)
                .body(Map.of("error", "Forbidden"));
    }
}
