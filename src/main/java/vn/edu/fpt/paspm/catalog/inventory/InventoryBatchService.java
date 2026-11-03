package vn.edu.fpt.paspm.catalog.inventory;

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

@Service
public class InventoryBatchService {
    private static final Set<String> STATUSES = Set.of("ACTIVE", "BLOCKED");
    private final InventoryBatchRepository batches;
    private final ProductRepository products;
    private final CatalogAccess access;

    public InventoryBatchService(InventoryBatchRepository batches, ProductRepository products, CatalogAccess access) {
        this.batches = batches; this.products = products; this.access = access;
    }

    public List<BatchView> list(Jwt jwt, int productId) {
        int storeId = access.currentStoreId(jwt);
        requireProduct(storeId, productId);
        return batches.findForProduct(storeId, productId);
    }

    public BatchView get(Jwt jwt, int id) { return requireBatch(access.currentStoreId(jwt), id); }

    @Transactional
    public BatchView create(Jwt jwt, int productId, BatchCreateRequest request) {
        int storeId = access.currentStoreId(jwt);
        ProductView product = requireProduct(storeId, productId);
        if (product.requiresExpiry() && request.expiryDate() == null) {
            throw new ApiException(400, "Expiry date is required for this product");
        }
        return batches.create(productId, request.batchCode(), request.receivedAt(),
                request.expiryDate(), request.initialQuantity());
    }

    @Transactional
    public BatchView update(Jwt jwt, int id, BatchPatchRequest request) {
        int storeId = access.currentStoreId(jwt);
        if (request.remainingQuantity() == null && request.status() == null) {
            throw new ApiException(400, "At least one batch field must be provided");
        }
        if (request.status() != null && !STATUSES.contains(request.status())) {
            throw new ApiException(400, "Invalid batch status");
        }
        BatchView current = batches.findForUpdate(storeId, id);
        if (current == null) throw new ApiException(404, "Inventory batch not found");
        if (request.remainingQuantity() != null && request.remainingQuantity() > current.initialQuantity()) {
            throw new ApiException(400, "Remaining quantity cannot exceed initial quantity");
        }
        batches.update(id, request.remainingQuantity(), request.status());
        return requireBatch(storeId, id);
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
