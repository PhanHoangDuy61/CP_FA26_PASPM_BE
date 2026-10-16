package vn.edu.fpt.paspm.catalog;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import io.swagger.v3.oas.annotations.media.Schema;

public final class CatalogContracts {
    private CatalogContracts() {}

    public record CategoryRequest(
            @NotBlank @Size(max = 150) String name,
            @NotBlank @Schema(allowableValues = {"ACTIVE", "INACTIVE"}) String status) {}

    public record StatusRequest(@NotBlank @Schema(allowableValues = {"ACTIVE", "INACTIVE"}) String status) {}

    public record CategoryView(int id, String name, String status) {}

    public record ProductRequest(
            @NotBlank @Size(max = 80) String sku,
            @NotBlank @Size(max = 180) String name,
            Integer categoryId,
            @NotBlank @Size(max = 40) String unit,
            @NotNull Boolean requiresExpiry,
            @NotNull @PositiveOrZero BigDecimal sellingPrice,
            @NotNull @PositiveOrZero BigDecimal costPrice,
            @NotBlank @Schema(allowableValues = {"ACTIVE", "INACTIVE"}) String status) {}

    public record ProductView(
            int id, String sku, String name, String unit, boolean requiresExpiry,
            CategoryView category, BigDecimal sellingPrice, BigDecimal costPrice,
            String status,
            @Schema(accessMode = Schema.AccessMode.READ_ONLY,
                    description = "Derived sum of inventory batch remainingQuantity values") long stockQuantity) {}

    public record ProductPage(List<ProductView> content, int page, int pageSize,
                              long totalElements, int totalPages) {}

    public record BatchCreateRequest(
            @NotBlank @Size(max = 80) String batchCode,
            @NotNull Instant receivedAt,
            @Schema(description = "Optional unless the product requires expiry tracking") LocalDate expiryDate,
            @NotNull @Positive Integer initialQuantity) {}

    public record BatchPatchRequest(
            @PositiveOrZero Integer remainingQuantity,
            @Schema(allowableValues = {"ACTIVE", "BLOCKED"}) String status) {}

    public record BatchView(
            int id, int productId, String batchCode, Instant receivedAt,
            LocalDate expiryDate, int initialQuantity, int remainingQuantity,
            String status, Instant createdAt, Instant updatedAt) {}

    public record InventorySummary(
            int productId, long totalQuantity, long sellableQuantity,
            long expiredQuantity, long blockedQuantity,
            @Schema(description = "Sellable quantity expiring between today and the inclusive lookahead date")
            long expiringSoonQuantity) {}
}
