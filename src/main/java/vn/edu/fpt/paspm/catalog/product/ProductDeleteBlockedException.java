package vn.edu.fpt.paspm.catalog.product;

import java.util.List;

import vn.edu.fpt.paspm.common.ApiException;

/**
 * DEC-20261124-03: 409 for {@code DELETE /api/products/{id}}. The body is {@code {code,error,reason,sellableQuantity,campaigns}};
 * {@code PRODUCT_HAS_SELLABLE_STOCK} wins when both conditions apply, and {@code campaigns} is always included.
 */
public final class ProductDeleteBlockedException extends ApiException {
    public static final String HAS_SELLABLE_STOCK = "PRODUCT_HAS_SELLABLE_STOCK";
    public static final String IN_LIVE_CAMPAIGN = "PRODUCT_IN_LIVE_CAMPAIGN";

    public record LiveCampaign(long campaignId, String status) {}

    private final String reason;
    private final long sellableQuantity;
    private final List<LiveCampaign> campaigns;

    public ProductDeleteBlockedException(String code, String reason, long sellableQuantity, List<LiveCampaign> campaigns) {
        super(409, "Product cannot be deleted", code);
        this.reason = reason;
        this.sellableQuantity = sellableQuantity;
        this.campaigns = List.copyOf(campaigns);
    }

    public String reason() { return reason; }
    public long sellableQuantity() { return sellableQuantity; }
    public List<LiveCampaign> campaigns() { return campaigns; }
}
