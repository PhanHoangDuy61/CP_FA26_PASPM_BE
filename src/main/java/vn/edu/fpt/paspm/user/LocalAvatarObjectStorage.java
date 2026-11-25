package vn.edu.fpt.paspm.user;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class LocalAvatarObjectStorage implements AvatarObjectStorage {
    private static final Pattern OBJECT_KEY = Pattern.compile(
            "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.(jpg|png)"
    );

    private final Path directory;

    public LocalAvatarObjectStorage(@Value("${paspm.avatar.directory}") String directory) {
        String configuredDirectory = directory.isBlank()
                ? Path.of(System.getProperty("user.home"), ".paspm", "profile-images").toString()
                : directory;
        this.directory = Path.of(configuredDirectory).toAbsolutePath().normalize();
    }

    @Override
    public String put(byte[] content, String extension) throws IOException {
        if (!".jpg".equals(extension) && !".png".equals(extension)) {
            throw new IllegalArgumentException("Unsupported avatar image extension");
        }

        Files.createDirectories(directory);
        String objectKey = UUID.randomUUID() + extension;
        Path target = resolve(objectKey);
        Path temporary = Files.createTempFile(directory, ".avatar-", ".tmp");
        boolean moved = false;
        try {
            Files.write(temporary, content);
            try {
                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(temporary, target);
            }
            moved = true;
            return objectKey;
        } finally {
            if (!moved) {
                Files.deleteIfExists(temporary);
            }
        }
    }

    @Override
    public StoredAvatar get(String objectKey) throws IOException {
        return new StoredAvatar(
                Files.readAllBytes(resolve(objectKey)),
                objectKey.endsWith(".png") ? "image/png" : "image/jpeg"
        );
    }

    @Override
    public void delete(String objectKey) throws IOException {
        Files.deleteIfExists(resolve(objectKey));
    }

    private Path resolve(String objectKey) throws IOException {
        if (!OBJECT_KEY.matcher(objectKey).matches()) {
            throw new IOException("Invalid avatar object key");
        }
        Path object = directory.resolve(objectKey).normalize();
        if (!object.getParent().equals(directory)) {
            throw new IOException("Invalid avatar object path");
        }
        return object;
    }
}
