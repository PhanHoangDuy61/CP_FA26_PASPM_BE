package vn.edu.fpt.paspm.storefront.cart;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import vn.edu.fpt.paspm.common.StrictIntegerDeserializer;
import vn.edu.fpt.paspm.storefront.StorefrontContracts.PublicStore;

public final class CartContracts {
    private CartContracts() {}

    @Schema(additionalProperties = Schema.AdditionalPropertiesValue.FALSE)
    public record AddCartItem(
            @NotNull @Positive @JsonDeserialize(using = StrictIntegerDeserializer.class) Integer productId,
            @NotNull @Positive @JsonDeserialize(using = StrictIntegerDeserializer.class) Integer quantity) {}

    @Schema(additionalProperties = Schema.AdditionalPropertiesValue.FALSE)
    public record ChangeCartItemQuantity(
            @NotNull @Positive @JsonDeserialize(using = StrictIntegerDeserializer.class) Integer quantity) {}

    public enum CartLineState { AVAILABLE, OUT_OF_STOCK, NO_LONGER_AVAILABLE }

    public record CartLineView(long itemId, int productId,
            @Schema(types = {"string", "null"}) String name,
            @Schema(types = {"string", "null"}) String imageUrl,
            @Schema(types = {"string", "null"}) String unit,
            int quantity,
            @Schema(types = {"number", "null"}) BigDecimal unitPrice,
            @Schema(types = {"number", "null"}) BigDecimal lineTotal,
            CartLineState state, boolean addedFromBundle) {}

    public record CartView(
            @Schema(types = {"integer", "null"}, format = "int64") Long cartId,
            PublicStore store, long version, @Schema(allowableValues = {"VND"}) String currency,
            @Schema(types = {"string", "null"}, format = "date-time") Instant updatedAt,
            List<CartLineView> items, @Schema(types = {"number", "null"}) BigDecimal subtotal) {}
}
