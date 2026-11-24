package vn.edu.fpt.paspm.catalog.inventory;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.oauth2.jwt.Jwt;

import vn.edu.fpt.paspm.catalog.CatalogAccess;
import vn.edu.fpt.paspm.catalog.CatalogContracts.BatchCreateRequest;
import vn.edu.fpt.paspm.catalog.CatalogContracts.BatchPatchRequest;
import vn.edu.fpt.paspm.catalog.CatalogContracts.BatchView;
import vn.edu.fpt.paspm.catalog.CatalogContracts.ProductView;
import vn.edu.fpt.paspm.catalog.product.ProductRepository;
import vn.edu.fpt.paspm.common.ApiException;
import vn.edu.fpt.paspm.utility.UtilityTimeZone;

@Service
public class InventoryBatchService {
    private static final Set<String> STATUSES = Set.of("ACTIVE", "BLOCKED");
    private final InventoryBatchRepository batches;
    private final ProductRepository products;
    private final CatalogAccess access;
    /** The one Store calendar ({@code paspm.store-time-zone}, default Asia/Ho_Chi_Minh) shared with F03/F06. */
    private final UtilityTimeZone timeZone;

    public InventoryBatchService(InventoryBatchRepository batches, ProductRepository products, CatalogAccess access,
                                 UtilityTimeZone timeZone) {
        this.batches = batches; this.products = products; this.access = access; this.timeZone = timeZone;
    }

    public List<BatchView> list(Jwt jwt, int productId) {
        int storeId = access.currentStoreId(jwt);
        ProductView product = requireProduct(storeId, productId);
        LocalDate today = timeZone.today();
        return batches.findForProduct(storeId, productId).stream()
                .map(batch -> batch.derive(today, product.requiresExpiry())).toList();
    }

    public BatchView get(Jwt jwt, int id) { return present(access.currentStoreId(jwt), requireBatch(access.currentStoreId(jwt), id)); }

    @Transactional
    public BatchView create(Jwt jwt, int productId, BatchCreateRequest request) {
        int storeId = access.currentStoreId(jwt);
        ProductView product = requireProduct(storeId, productId);
        if (product.requiresExpiry() && request.expiryDate() == null) {
            throw new ApiException(400, "Expiry date is required for this product");
        }
        requirePushDay(request.pushDay(), request.expiryDate());
        String batchCode = batches.generateBatchCode(storeId, productId,
                request.receivedAt().atZone(timeZone.zone()).toLocalDate());
        if (batchCode == null) throw new ApiException(404, "Product not found");
        return present(product, batches.create(productId, batchCode, request.receivedAt(),
                request.expiryDate(), request.initialQuantity(), request.pushDay()));
    }

    @Transactional
    public BatchView update(Jwt jwt, int id, BatchPatchRequest request) {
        int storeId = access.currentStoreId(jwt);
        if (request.remainingQuantity() == null && request.status() == null
                && request.pushDay() == null && request.expiryDate() == null) {
            throw new ApiException(400, "At least one batch field must be provided");
        }
        // EXPIRED is derived (effectiveStatus) and is never writable; INACTIVE does not exist for batches
        if (request.status() != null && !STATUSES.contains(request.status())) {
            throw new ApiException(400, "Invalid batch status");
        }
        BatchView current = batches.findForUpdate(storeId, id);
        if (current == null) throw new ApiException(404, "Inventory batch not found");
        ProductView product = requireProduct(storeId, current.productId());
        if (request.remainingQuantity() != null && request.remainingQuantity() > current.initialQuantity()) {
            throw new ApiException(400, "Remaining quantity cannot exceed initial quantity");
        }
        java.time.LocalDate expiryDate = request.expiryDate() == null ? current.expiryDate() : request.expiryDate();
        java.time.LocalDate pushDay = request.pushDay() == null ? current.pushDay() : request.pushDay();
        if (product.requiresExpiry() && expiryDate == null) {
            throw new ApiException(400, "Expiry date is required for this product");
        }
        requirePushDay(pushDay, expiryDate);
        batches.update(id, request.remainingQuantity(), request.status(), request.pushDay(), request.expiryDate());
        return present(product, requireBatch(storeId, id));
    }

    @Transactional
    public BatchView clearExpiryDate(Jwt jwt, int id) {
        int storeId = access.currentStoreId(jwt);
        BatchView current = batches.findForUpdate(storeId, id);
        if (current == null) throw new ApiException(404, "Inventory batch not found");
        ProductView product = requireProduct(storeId, current.productId());
        if (product.requiresExpiry()) {
            throw new ApiException(400, "Cannot clear expiry date while the product requires expiry");
        }
        batches.clearExpiryDate(storeId, id);
        return present(product, requireBatch(storeId, id));
    }

    @Transactional
    public BatchView clearPushDay(Jwt jwt, int id) {
        int storeId = access.currentStoreId(jwt);
        BatchView current = batches.findForUpdate(storeId, id);
        if (current == null) throw new ApiException(404, "Inventory batch not found");
        ProductView product = requireProduct(storeId, current.productId());
        batches.clearPushDay(storeId, id);
        return present(product, requireBatch(storeId, id));
    }

    private static void requirePushDay(java.time.LocalDate pushDay, java.time.LocalDate expiryDate) {
        if (pushDay != null && expiryDate != null && pushDay.isAfter(expiryDate)) {
            throw new ApiException(400, "pushDay must be on or before expiryDate");
        }
    }

    private BatchView present(int storeId, BatchView batch) {
        return present(requireProduct(storeId, batch.productId()), batch);
    }

    private BatchView present(ProductView product, BatchView batch) {
        return batch.derive(timeZone.today(), product.requiresExpiry());
    }

    private ProductView requireProduct(int storeId, int id) {
        ProductView product = products.find(storeId, id);
        if (product == null) throw new ApiException(404, "Product not found");
        return product;
    }

    private BatchView requireBatch(int storeId, int id) {
        BatchView batch = batches.find(storeId, id);
        if (batch == null) throw new ApiException(404, "Inventory batch not found");
        return batch;
    }
}
