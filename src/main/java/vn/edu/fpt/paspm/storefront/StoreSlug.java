package vn.edu.fpt.paspm.storefront;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.Normalizer;
import java.util.HexFormat;
import java.util.Locale;

/**
 * Slug generator used <b>only</b> by the frozen Flyway migration {@code V29__f09_storefront} (old F09 merge on dev).
 * Runtime storefront lookup uses {@code store.slug} from V27 (DEC-20261123-01, DEC-20261125-04); the
 * {@code store.store_slug} column this fills is dropped by V30. Do not change the algorithm: V29 must behave the same
 * on every fresh database.
 */
public final class StoreSlug {
    private StoreSlug() {}

    public static String generate(String name, int storeId) {
        String base = Normalizer.normalize(name, Normalizer.Form.NFD)
                .replace('\u0111', 'd').replace('\u0110', 'D')
                .replaceAll("\\p{M}+", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-|-$", "");
        if (base.isBlank()) base = "store";
        if (base.length() > 100) base = base.substring(0, 100).replaceAll("-+$", "");
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(Integer.toString(storeId).getBytes(StandardCharsets.UTF_8));
            return base + "-" + HexFormat.of().formatHex(digest, 0, 8);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
