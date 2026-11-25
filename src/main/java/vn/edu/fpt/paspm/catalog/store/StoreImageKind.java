package vn.edu.fpt.paspm.catalog.store;

import java.util.Locale;

import vn.edu.fpt.paspm.common.ApiException;

/** Store branding images (DEC-20261123-01 §5): PNG or JPEG, at most 4000×4000 px; icon ≤ 1 MiB, banner ≤ 5 MiB. */
public enum StoreImageKind {
    ICON("icon_key", 1024 * 1024, "1 MiB"),
    BANNER("banner_key", 5 * 1024 * 1024, "5 MiB");

    private final String column;
    private final long maxBytes;
    private final String limit;

    StoreImageKind(String column, long maxBytes, String limit) {
        this.column = column;
        this.maxBytes = maxBytes;
        this.limit = limit;
    }

    String column() {
        return column;
    }

    long maxBytes() {
        return maxBytes;
    }

    String limit() {
        return limit;
    }

    /** Path value {@code icon} or {@code banner}; anything else is 404 (there is no such image). */
    public static StoreImageKind fromPath(String value) {
        return switch (value == null ? "" : value.toLowerCase(Locale.ROOT)) {
            case "icon" -> ICON;
            case "banner" -> BANNER;
            default -> throw new ApiException(404, "Store image not found");
        };
    }
}
