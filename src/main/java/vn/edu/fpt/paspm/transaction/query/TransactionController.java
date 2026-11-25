package vn.edu.fpt.paspm.transaction.query;

import io.swagger.v3.oas.annotations.Operation;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import vn.edu.fpt.paspm.catalog.CatalogAccess;
import vn.edu.fpt.paspm.common.ApiException;
import vn.edu.fpt.paspm.transaction.TransactionContracts.Page;
import vn.edu.fpt.paspm.transaction.TransactionContracts.TransactionView;

@RestController
@PreAuthorize("hasAnyRole('STORE_ADMIN','MERCHANDISER')")
public class TransactionController {
    private static final Set<String> STATUSES = Set.of("COMPLETED", "CANCELLED", "REFUNDED", "PARTIAL_REFUND");
    private final CatalogAccess access;
    private final TransactionQueryRepository transactions;

    public TransactionController(CatalogAccess access, TransactionQueryRepository transactions) {
        this.access = access;
        this.transactions = transactions;
    }

    @Operation(summary = "List sales transactions for the authenticated Store")
    @GetMapping("/api/transactions")
    public Page<TransactionView> list(@AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Instant from,
            @RequestParam(required = false) Instant to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int pageSize) {
        validate(status, from, to, page, pageSize);
        int storeId = access.currentStoreId(jwt);
        List<TransactionView> content = transactions.list(storeId, normalize(status), from, to, page, pageSize);
        return new Page<>(content, page, pageSize, transactions.count(storeId, normalize(status), from, to));
    }

    @Operation(summary = "Get one sales transaction and its historical product lines")
    @GetMapping("/api/transactions/{id}")
    public TransactionView get(@AuthenticationPrincipal Jwt jwt, @PathVariable int id) {
        TransactionView transaction = transactions.get(access.currentStoreId(jwt), id);
        if (transaction == null) throw new ApiException(404, "Transaction not found");
        return transaction;
    }

    private void validate(String status, Instant from, Instant to, int page, int pageSize) {
        if (page < 0 || pageSize < 1 || pageSize > 100) throw new ApiException(400, "Invalid pagination");
        if (from != null && to != null && from.isAfter(to)) throw new ApiException(400, "from must not be after to");
        if (status != null && !status.isBlank() && !STATUSES.contains(status.toUpperCase(Locale.ROOT))) {
            throw new ApiException(400, "Unsupported transaction status");
        }
    }

    private String normalize(String status) { return status == null || status.isBlank() ? null : status.toUpperCase(Locale.ROOT); }
}
