package vn.edu.fpt.paspm.storefront;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;
import vn.edu.fpt.paspm.common.ApiException;

@Component
class BundleKeys {
    private static final String CIPHER = "AES/GCM/NoPadding";
    private static final int NONCE_BYTES = 12;
    private static final int TAG_BYTES = 16;
    private static final int PAYLOAD_BYTES = 2 * Integer.BYTES + 3 * Long.BYTES;
    private static final int TOKEN_BYTES = NONCE_BYTES + PAYLOAD_BYTES + TAG_BYTES;
    private static final int TOKEN_LENGTH = (TOKEN_BYTES * 4 + 2) / 3;
    private static final byte[] PURPOSE = "PASPM/F10/bundle-key/v1".getBytes(StandardCharsets.UTF_8);

    record BlockRef(int storeId, long campaignId, long layoutId, long layoutVersion, int blockNo) {}

    private final SecureRandom random = new SecureRandom();
    private final SecretKeySpec key;

    BundleKeys(SecretKeySpec signingKey) {
        try {
            Mac derive = Mac.getInstance("HmacSHA256");
            derive.init(signingKey);
            key = new SecretKeySpec(derive.doFinal(PURPOSE), "AES");
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Cannot derive the storefront bundle key", exception);
        }
    }

    String encode(BlockRef block) {
        byte[] nonce = new byte[NONCE_BYTES];
        random.nextBytes(nonce);
        byte[] payload = ByteBuffer.allocate(PAYLOAD_BYTES).putInt(block.storeId()).putLong(block.campaignId())
                .putLong(block.layoutId()).putLong(block.layoutVersion()).putInt(block.blockNo()).array();
        try {
            byte[] encrypted = cipher(Cipher.ENCRYPT_MODE, nonce).doFinal(payload);
            byte[] token = ByteBuffer.allocate(TOKEN_BYTES).put(nonce).put(encrypted).array();
            return Base64.getUrlEncoder().withoutPadding().encodeToString(token);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Cannot issue storefront bundle key", exception);
        }
    }

    BlockRef decode(String token) {
        if (token.length() != TOKEN_LENGTH) throw notFound();
        try {
            byte[] bytes = Base64.getUrlDecoder().decode(token);
            if (bytes.length != TOKEN_BYTES) throw notFound();
            byte[] nonce = java.util.Arrays.copyOf(bytes, NONCE_BYTES);
            byte[] payload = cipher(Cipher.DECRYPT_MODE, nonce)
                    .doFinal(bytes, NONCE_BYTES, bytes.length - NONCE_BYTES);
            ByteBuffer data = ByteBuffer.wrap(payload);
            return new BlockRef(data.getInt(), data.getLong(), data.getLong(), data.getLong(), data.getInt());
        } catch (GeneralSecurityException | IllegalArgumentException exception) {
            throw notFound();
        }
    }

    static ApiException notFound() {
        return new ApiException(404, "Bundle not found");
    }

    private Cipher cipher(int mode, byte[] nonce) throws GeneralSecurityException {
        Cipher cipher = Cipher.getInstance(CIPHER);
        cipher.init(mode, key, new GCMParameterSpec(TAG_BYTES * Byte.SIZE, nonce));
        return cipher;
    }
}
