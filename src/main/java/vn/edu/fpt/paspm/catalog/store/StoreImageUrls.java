package vn.edu.fpt.paspm.catalog.store;

/**
 * The one place that builds the public URL of a Store icon or banner (DEC-20261123-01). The staff Store views and
 * the public storefront (F09) both use it, so they always advertise the same URL, which
 * {@link PublicStoreImageController} serves while the Store is ACTIVE.
 */
public final class StoreImageUrls {
    private StoreImageUrls() {}

    /**
     * The public URL of the current file, or {@code null} without an image. It is cache-safe because every upload
     * gets a new UUID file name; only the file name of the stored key is used, never its directory.
     */
    public static String publicUrl(int storeId, String storedKey) {
        return storedKey == null ? null
                : "/api/public/stores/" + storeId + "/images/" + storedKey.substring(storedKey.lastIndexOf('/') + 1);
    }
}
