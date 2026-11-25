package vn.edu.fpt.paspm.user;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Iterator;

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

import vn.edu.fpt.paspm.common.ApiException;
import vn.edu.fpt.paspm.common.Contracts.UserView;

@Service
public class AvatarService {
    private static final Logger logger = LoggerFactory.getLogger(AvatarService.class);
    private static final int MAX_BYTES = 5 * 1024 * 1024;
    private static final int MAX_DIMENSION = 4096;
    private static final long MAX_PIXELS = 12_000_000L;

    private final UserRepository users;
    private final AvatarObjectStorage storage;

    public AvatarService(UserRepository users, AvatarObjectStorage storage) {
        this.users = users;
        this.storage = storage;
    }

    @Transactional
    public UserView replace(int userId, MultipartFile upload) {
        if (upload.isEmpty()) {
            throw new ApiException(400, "Avatar image is required");
        }
        if (upload.getSize() > MAX_BYTES) {
            throw new ApiException(413, "Uploaded file exceeds the 5 MiB limit");
        }

        byte[] content;
        try {
            content = upload.getBytes();
        } catch (IOException exception) {
            throw new ApiException(400, "Unable to read uploaded avatar image");
        }
        String extension = validateImage(content);

        User user = users.lockById(userId);
        String previousKey = user.avatarKey();
        String objectKey;
        try {
            objectKey = storage.put(content, extension);
        } catch (IOException exception) {
            logger.error("Avatar storage write failed ({})", exception.getClass().getSimpleName());
            throw new ApiException(503, "Avatar storage unavailable");
        }

        afterCompletion(
                () -> deleteAfterCommit(previousKey),
                () -> deleteAfterRollback(objectKey)
        );
        users.updateAvatar(userId, objectKey);
        return users.findById(userId).view();
    }

    @Transactional
    public UserView delete(int userId) {
        User user = users.lockById(userId);
        if (user.avatarKey() != null) {
            users.updateAvatar(userId, null);
            afterCompletion(
                    () -> deleteAfterCommit(user.avatarKey()),
                    () -> {
                    }
            );
        }
        return users.findById(userId).view();
    }

    @Transactional(readOnly = true)
    public AvatarObjectStorage.StoredAvatar get(int userId, int authenticatedUserId) {
        if (userId != authenticatedUserId) {
            throw new ApiException(403, "Forbidden");
        }
        User user = users.findById(userId);
        if (user.avatarKey() == null) {
            throw new ApiException(404, "Avatar not found");
        }
        try {
            return storage.get(user.avatarKey());
        } catch (IOException exception) {
            logger.error("Avatar storage read failed ({})", exception.getClass().getSimpleName());
            throw new ApiException(503, "Avatar storage unavailable");
        }
    }

    private String validateImage(byte[] content) {
        try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(content))) {
            if (input == null) {
                throw new ApiException(400, "Only valid JPEG or PNG images are accepted");
            }

            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) {
                throw new ApiException(400, "Only valid JPEG or PNG images are accepted");
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(input, true, true);
                String format = reader.getFormatName();
                String extension = switch (format.toLowerCase(java.util.Locale.ROOT)) {
                    case "jpeg", "jpg" -> ".jpg";
                    case "png" -> ".png";
                    default -> throw new ApiException(400, "Only JPEG and PNG images are accepted");
                };

                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                if (width < 1 || height < 1
                        || width > MAX_DIMENSION
                        || height > MAX_DIMENSION
                        || (long) width * height > MAX_PIXELS
                        || reader.read(0) == null) {
                    throw new ApiException(400, "Avatar image dimensions or content are invalid");
                }
                return extension;
            } finally {
                reader.dispose();
            }
        } catch (IOException exception) {
            throw new ApiException(400, "Only valid JPEG or PNG images are accepted");
        }
    }

    private void afterCompletion(Runnable committed, Runnable rolledBack) {
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

    private void deleteAfterCommit(String objectKey) {
        if (objectKey != null) {
            deleteObject(objectKey, "post-commit");
        }
    }

    private void deleteAfterRollback(String objectKey) {
        deleteObject(objectKey, "rollback");
    }

    private void deleteObject(String objectKey, String phase) {
        try {
            storage.delete(objectKey);
        } catch (IOException exception) {
            logger.error("Avatar cleanup failed during {} ({})", phase, exception.getClass().getSimpleName());
        }
    }
}
