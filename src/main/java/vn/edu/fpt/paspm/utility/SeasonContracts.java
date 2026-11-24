package vn.edu.fpt.paspm.utility;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public final class SeasonContracts {
    private SeasonContracts() {}
    public record SeasonRequest(
        @Schema(example="Tet 2027") @NotBlank @Size(max=100) String name,
        @Schema(example="2027-01-20") @NotNull LocalDate startDate,
        @Schema(example="2027-02-20") @NotNull LocalDate endDate,
        @Schema(example="0.9") @NotNull @DecimalMin("0") @DecimalMax("1") @Digits(integer=1,fraction=6) BigDecimal weight,
        @Schema(description="Required on update (optimistic lock); must be absent on create",types={"integer","null"}) Long expectedVersion) {}
    public record SeasonView(int seasonId,int storeId,String name,LocalDate startDate,LocalDate endDate,BigDecimal weight,
        long version,int productCount,
        @Schema(description="Whether the Store-local today lies within [startDate, endDate] inclusive (rule P3)") boolean activeToday,
        int createdBy,int updatedBy,Instant createdAt,Instant updatedAt) {}
    public record SeasonPage(List<SeasonView> items,int page,int pageSize,long totalElements) {}
    public record SeasonProduct(int productId,String sku,String name,String status,int assignedBy,Instant assignedAt,
        @Schema(description="True when the product was soft-deleted (DEC-20261124-03): the assignment is kept for history, can be removed, and cannot be created again") boolean deleted) {}
    public record SeasonProductPage(List<SeasonProduct> items,int page,int pageSize,long totalElements) {}
}
