package vn.edu.fpt.paspm.catalog.product;

import vn.edu.fpt.paspm.common.ApiException;

public final class ProductNameConfirmationRequiredException extends ApiException {
    public static final String CODE = "PRODUCT_NAME_CONFIRMATION_REQUIRED";

    public ProductNameConfirmationRequiredException() {
        super(409, "A similar product name exists in this Store; confirm before saving");
    }
}
