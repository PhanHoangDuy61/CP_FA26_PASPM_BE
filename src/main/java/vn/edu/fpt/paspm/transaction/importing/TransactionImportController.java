package vn.edu.fpt.paspm.transaction.importing;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import vn.edu.fpt.paspm.catalog.CatalogAccess;
import vn.edu.fpt.paspm.common.ApiException;
import vn.edu.fpt.paspm.transaction.TransactionContracts.ImportError;
import vn.edu.fpt.paspm.transaction.TransactionContracts.ImportSummary;
import vn.edu.fpt.paspm.transaction.TransactionContracts.Page;

@RestController
public class TransactionImportController {
    private final CatalogAccess access;
    private final TransactionImportService imports;
    private final TransactionImportRepository repository;

    public TransactionImportController(CatalogAccess access, TransactionImportService imports,
            TransactionImportRepository repository) {
        this.access = access;
        this.imports = imports;
        this.repository = repository;
    }

    @Operation(summary = "Upload a CSV sales transaction import for the authenticated Store")
    @PostMapping(path = "/api/transactions/imports/csv", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('STORE_ADMIN')")
    @ResponseStatus(HttpStatus.CREATED)
    public ImportSummary upload(@AuthenticationPrincipal Jwt jwt,
            @Parameter(description = "CSV file using the approved F04 template") @RequestPart("file") MultipartFile file) {
        return imports.importCsv(jwt, file);
    }

    @Operation(summary = "List transaction imports for the authenticated Store")
    @GetMapping("/api/transactions/imports")
    @PreAuthorize("hasAnyRole('STORE_ADMIN','MERCHANDISER')")
    public Page<ImportSummary> list(@AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int pageSize) {
        validatePage(page, pageSize);
        int storeId = access.currentStoreId(jwt);
        return new Page<>(repository.listBatches(storeId, page, pageSize), page, pageSize, repository.countBatches(storeId));
    }

    @Operation(summary = "Get one transaction import owned by the authenticated Store")
    @GetMapping("/api/transactions/imports/{id}")
    @PreAuthorize("hasAnyRole('STORE_ADMIN','MERCHANDISER')")
    public ImportSummary get(@AuthenticationPrincipal Jwt jwt, @PathVariable int id) {
        ImportSummary summary = repository.getBatch(access.currentStoreId(jwt), id);
        if (summary == null) throw new ApiException(404, "Import batch not found");
        return summary;
    }

    @Operation(summary = "List rejected or conflicted rows for one Store-owned import")
    @GetMapping("/api/transactions/imports/{id}/errors")
    @PreAuthorize("hasAnyRole('STORE_ADMIN','MERCHANDISER')")
    public Page<ImportError> errors(@AuthenticationPrincipal Jwt jwt, @PathVariable int id,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "100") @Min(1) @Max(100) int pageSize) {
        validatePage(page, pageSize);
        int storeId = access.currentStoreId(jwt);
        if (repository.getBatch(storeId, id) == null) throw new ApiException(404, "Import batch not found");
        List<ImportError> errors = repository.listErrors(storeId, id, page, pageSize);
        return new Page<>(errors, page, pageSize, repository.countErrors(storeId, id));
    }

    private void validatePage(int page, int pageSize) {
        if (page < 0 || pageSize < 1 || pageSize > 100) throw new ApiException(400, "Invalid pagination");
    }
}
