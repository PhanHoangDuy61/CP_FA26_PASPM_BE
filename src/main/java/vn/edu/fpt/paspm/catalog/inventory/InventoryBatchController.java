package vn.edu.fpt.paspm.catalog.inventory;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.ResponseStatus;

import io.swagger.v3.oas.annotations.Operation;
import vn.edu.fpt.paspm.catalog.CatalogContracts.BatchCreateRequest;
import vn.edu.fpt.paspm.catalog.CatalogContracts.BatchPatchRequest;
import vn.edu.fpt.paspm.catalog.CatalogContracts.BatchView;

@RestController
@PreAuthorize("hasRole('STORE_ADMIN')")
public class InventoryBatchController {
    private final InventoryBatchService batches;
    private final InventorySummaryService summaries;

    public InventoryBatchController(InventoryBatchService batches, InventorySummaryService summaries) {
        this.batches = batches; this.summaries = summaries;
    }

    @Operation(summary = "List inventory batches for a Store product")
    @GetMapping("/api/products/{id}/batches")
    public List<BatchView> list(@AuthenticationPrincipal Jwt jwt, @PathVariable("id") int productId) {
        return batches.list(jwt, productId);
    }

    @Operation(summary = "Receive an inventory batch for a Store product")
    @PostMapping("/api/products/{id}/batches")
    @ResponseStatus(HttpStatus.CREATED)
    public BatchView create(@AuthenticationPrincipal Jwt jwt, @PathVariable("id") int productId,
                            @Valid @RequestBody BatchCreateRequest request) {
        return batches.create(jwt, productId, request);
    }

    @Operation(summary = "Get a Store inventory batch")
    @GetMapping("/api/inventory-batches/{id}")
    public BatchView get(@AuthenticationPrincipal Jwt jwt, @PathVariable int id) { return batches.get(jwt, id); }

    @Operation(summary = "Set remaining quantity and/or block state for a Store inventory batch")
    @PatchMapping("/api/inventory-batches/{id}")
    public BatchView update(@AuthenticationPrincipal Jwt jwt, @PathVariable int id,
                            @Valid @RequestBody BatchPatchRequest request) {
        return batches.update(jwt, id, request);
    }

    @Operation(summary = "Summarize derived inventory for a Store product",
            description = "Expiry dates are inclusive through the configured Store time zone's local date.")
    @GetMapping("/api/products/{id}/inventory-summary")
    public vn.edu.fpt.paspm.catalog.CatalogContracts.InventorySummary summary(
            @AuthenticationPrincipal Jwt jwt, @PathVariable("id") int productId,
            @RequestParam(defaultValue = "7") @Min(0) int expiringWithinDays) {
        return summaries.summarize(jwt, productId, expiringWithinDays);
    }
}
