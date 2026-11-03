package vn.edu.fpt.paspm.catalog.inventory;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.ZoneId;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;

import vn.edu.fpt.paspm.catalog.CatalogAccess;
import vn.edu.fpt.paspm.catalog.CatalogContracts.InventorySummary;
import vn.edu.fpt.paspm.catalog.product.ProductRepository;
import vn.edu.fpt.paspm.common.ApiException;

@Service
public class InventorySummaryService {
    private final InventorySummaryRepository summaries;
    private final ProductRepository products;
    private final CatalogAccess access;
    private final ZoneId storeTimeZone;

    public InventorySummaryService(InventorySummaryRepository summaries, ProductRepository products,
                                   CatalogAccess access,
                                   @Value("${paspm.store-time-zone:Asia/Ho_Chi_Minh}") String storeTimeZone) {
        this.summaries = summaries;
        this.products = products;
        this.access = access;
        try {
            this.storeTimeZone = storeTimeZone.isBlank() ? null : ZoneId.of(storeTimeZone);
        } catch (DateTimeException exception) {
            throw new IllegalArgumentException("Invalid paspm.store-time-zone", exception);
        }
    }

    public InventorySummary summarize(Jwt jwt, int productId, int expiringWithinDays) {
        int storeId = access.currentStoreId(jwt);
        if (products.find(storeId, productId) == null) throw new ApiException(404, "Product not found");
        if (expiringWithinDays < 0) throw new ApiException(400, "expiringWithinDays cannot be negative");
        LocalDate today = LocalDate.now(storeTimeZone);
        LocalDate through;
        try {
            through = today.plusDays(expiringWithinDays);
        } catch (DateTimeException exception) {
            throw new ApiException(400, "expiringWithinDays is too large");
        }
        return summaries.summarize(productId, today, through);
    }
}
