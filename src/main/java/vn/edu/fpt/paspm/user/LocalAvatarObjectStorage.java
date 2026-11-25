package vn.edu.fpt.paspm.user;

import java.io.IOException;
import java.nio.file.Path;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import vn.edu.fpt.paspm.common.media.LocalMediaStorage;

/**
 * Avatars on the shared {@link LocalMediaStorage} implementation (DEC-20261123-01 §5). Keys stay bare {@code UUID.png|jpg}
 * names directly in {@code paspm.avatar.directory} ({@code PROFILE_IMAGE_DIRECTORY}, default unchanged:
 * {@code ~/.paspm/profile-images}), so stored {@code app_user.avatar_key} values and existing files keep working.
 */
@Component
public class LocalAvatarObjectStorage implements AvatarObjectStorage {
    private final LocalMediaStorage storage;

    public LocalAvatarObjectStorage(@Value("${paspm.avatar.directory}") String directory) {
        String configuredDirectory = directory.isBlank()
                ? Path.of(System.getProperty("user.home"), ".paspm", "profile-images").toString()
                : directory;
        this.storage = new LocalMediaStorage(configuredDirectory);
    }

    @Override
    public String put(byte[] content, String extension) throws IOException {
        if (!".jpg".equals(extension) && !".png".equals(extension)) {
            throw new IllegalArgumentException("Unsupported avatar image extension");
        }
        return storage.put("", content, extension);
    }

    @Override
    public StoredAvatar get(String objectKey) throws IOException {
        var stored = storage.get("", objectKey);
        return new StoredAvatar(stored.content(), stored.contentType());
    }

    @Override
    public void delete(String objectKey) throws IOException {
        storage.delete("", objectKey);
    }
}
