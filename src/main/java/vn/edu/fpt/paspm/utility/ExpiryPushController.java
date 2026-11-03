package vn.edu.fpt.paspm.utility;

import java.time.LocalDate;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@PreAuthorize("hasAnyRole('STORE_ADMIN','MERCHANDISER')")
public class ExpiryPushController {
    private final ExpiryPushService service;
    public ExpiryPushController(ExpiryPushService service) { this.service=service; }
    @GetMapping("/api/utility/expiry-push-batches")
    @Operation(summary="Discover sellable expiry-push batches independently of Mining",description="Defaults to the current Store-local date. An explicit asOfDate is analytical; recommendations must recheck current eligibility. Ordered FEFO, then productId/batchId. Store scope comes from F01 identity.")
    public ExpiryPushService.PushPage list(@AuthenticationPrincipal Jwt jwt,
        @RequestParam(required=false) Integer productId,
        @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate asOfDate,
        @RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int pageSize) {
        return service.list(jwt,productId,asOfDate,page,pageSize);
    }
}
