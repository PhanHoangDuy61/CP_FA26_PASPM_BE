package vn.edu.fpt.paspm.catalog.store;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Iterator;
import java.util.Locale;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import vn.edu.fpt.paspm.catalog.store.StoreContracts.StoreView;
import vn.edu.fpt.paspm.catalog.store.StoreRepository.StoreRow;
import vn.edu.fpt.paspm.common.ApiException;
import vn.edu.fpt.paspm.common.media.MediaStorage;

/**
 * DEC-20261123-01 §5: Store icon and banner. PNG or JPEG only, checked by magic bytes and then decoded with ImageIO (which
 * also gives the dimensions); icon ≤ 1 MiB, banner ≤ 5 MiB, ≤ 4000×4000 px. The stored key is
 * {@code stores/{storeId}/{uuid}.{png|jpg}}; the client file name is never used. A replaced file is deleted after commit (a
 * failed delete only logs a warning); a newly written file is deleted on rollback.
 */
@Service
public class StoreImageService {
    private static final Logger logger = LoggerFactory.getLogger(StoreImageService.class);
    static final int MAX_DIMENSION = 4000;
    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'};

    private final StoreRepository stores;
    private final MediaStorage media;

    public StoreImageService(StoreRepository stores, MediaStorage media) {
        this.stores = stores;
        this.media = media;
    }

    static String folder(int storeId) {
        return "stores/" + storeId;
    }

    @Transactional
    public StoreView replace(int storeId, StoreImageKind kind, MultipartFile upload) {
        stores.find(storeId).orElseThrow(StoreService::notFound);
        if (upload == null || upload.isEmpty()) {
            throw new ApiException(400, "Image file is required");
        }
        if (upload.getSize() > kind.maxBytes()) {
            throw new ApiException(413, "The " + kind.name().toLowerCase(Locale.ROOT) + " must be at most " + kind.limit());
        }
        byte[] content;
        try {
            content = upload.getBytes();
        } catch (IOException exception) {
            throw new ApiException(400, "Unable to read the uploaded image");
        }
        String extension = validate(content);

        StoreRow row = stores.lock(storeId).orElseThrow(StoreService::notFound);
        String previousKey = key(row, kind);
        String fileName;
        try {
            fileName = media.put(folder(storeId), content, extension);
        } catch (IOException exception) {
            logger.error("Store image write failed ({})", exception.getClass().getSimpleName());
            throw new ApiException(503, "Media storage unavailable");
        }
        String newKey = folder(storeId) + "/" + fileName;
        afterCompletion(() -> deleteQuietly(previousKey, "post-commit"), () -> deleteQuietly(newKey, "rollback"));
        stores.updateImageKey(storeId, kind, newKey);
        return StoreService.view(stores.find(storeId).orElseThrow(StoreService::notFound));
    }

    @Transactional
    public StoreView remove(int storeId, StoreImageKind kind) {
        StoreRow row = stores.lock(storeId).orElseThrow(StoreService::notFound);
        String previousKey = key(row, kind);
        if (previousKey != null) {
            stores.updateImageKey(storeId, kind, null);
            afterCompletion(() -> deleteQuietly(previousKey, "post-commit"), () -> {
            });
        }
        return StoreService.view(stores.find(storeId).orElseThrow(StoreService::notFound));
    }

    /** Authenticated read, whatever the Store status (System Admin preview of an INACTIVE Store). */
    @Transactional(readOnly = true)
    public MediaStorage.StoredMedia read(int storeId, StoreImageKind kind) {
        StoreRow row = stores.find(storeId).orElseThrow(StoreService::notFound);
        String key = key(row, kind);
        if (key == null) {
            throw new ApiException(404, "Store image not found");
        }
        return load(key);
    }

    /** Public read: only the current icon or banner of an ACTIVE Store; everything else is 404. */
    @Transactional(readOnly = true)
    public MediaStorage.StoredMedia readPublic(int storeId, String fileName) {
        StoreRow row = stores.find(storeId).filter(StoreRow::active)
                .orElseThrow(() -> new ApiException(404, "Store image not found"));
        String wanted = folder(storeId) + "/" + fileName;
        if (!wanted.equals(row.iconKey()) && !wanted.equals(row.bannerKey())) {
            throw new ApiException(404, "Store image not found");
        }
        return load(wanted);
    }

    private MediaStorage.StoredMedia load(String key) {
        int slash = key.lastIndexOf('/');
        try {
            return media.get(key.substring(0, slash), key.substring(slash + 1));
        } catch (java.nio.file.NoSuchFileException exception) {
            logger.warn("Store image file is missing ({})", key);
            throw new ApiException(404, "Store image not found");
        } catch (IOException exception) {
            logger.error("Store image read failed ({})", exception.getClass().getSimpleName());
            throw new ApiException(503, "Media storage unavailable");
        }
    }

    private static String key(StoreRow row, StoreImageKind kind) {
        return kind == StoreImageKind.ICON ? row.iconKey() : row.bannerKey();
    }

    /** Magic bytes first (PNG or JPEG), then a full ImageIO decode of the same format; returns the stored extension. */
    static String validate(byte[] content) {
        boolean png = content.length >= PNG.length && java.util.Arrays.equals(content, 0, PNG.length, PNG, 0, PNG.length);
        boolean jpeg = content.length >= 3 && (content[0] & 0xFF) == 0xFF && (content[1] & 0xFF) == 0xD8 && (content[2] & 0xFF) == 0xFF;
        if (!png && !jpeg) {
            throw new ApiException(400, "Only PNG or JPEG images are accepted");
        }
        try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(content))) {
            Iterator<ImageReader> readers = input == null ? java.util.Collections.emptyIterator() : ImageIO.getImageReaders(input);
            if (!readers.hasNext()) {
                throw new ApiException(400, "Image content is invalid");
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(input, true, true);
                String format = reader.getFormatName().toLowerCase(Locale.ROOT);
                if (png ? !format.equals("png") : !(format.equals("jpeg") || format.equals("jpg"))) {
                    throw new ApiException(400, "Image content is invalid");
                }
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                if (width < 1 || height < 1 || width > MAX_DIMENSION || height > MAX_DIMENSION) {
                    throw new ApiException(400, "Image dimensions must be at most 4000×4000 pixels");
                }
                if (reader.read(0) == null) {
                    throw new ApiException(400, "Image content is invalid");
                }
                return png ? ".png" : ".jpg";
            } finally {
                reader.dispose();
            }
        } catch (IOException | RuntimeException exception) {
            if (exception instanceof ApiException api) {
                throw api;
            }
            throw new ApiException(400, "Image content is invalid");
        }
    }

    private static void afterCompletion(Runnable committed, Runnable rolledBack) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status == STATUS_COMMITTED) {
                    committed.run();
                } else {
                    rolledBack.run();
                }
            }
        });
    }

    private void deleteQuietly(String key, String phase) {
        if (key == null) {
            return;
        }
        int slash = key.lastIndexOf('/');
        try {
            media.delete(key.substring(0, slash), key.substring(slash + 1));
        } catch (IOException | RuntimeException exception) {
            logger.warn("Store image cleanup failed during {} ({})", phase, exception.getClass().getSimpleName());
        }
    }
}
