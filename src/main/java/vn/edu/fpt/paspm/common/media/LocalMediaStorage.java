package vn.edu.fpt.paspm.common.media;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Local-filesystem {@link MediaStorage} under one root directory (the root comes from {@code PASPM_MEDIA_ROOT}; on Linux
 * deployments it is a Docker volume). Folder and file names are validated and every resolved path must stay under the
 * root; writes go to a temporary file first and are then moved atomically into place.
 */
public class LocalMediaStorage implements MediaStorage {
    private static final Pattern FOLDER = Pattern.compile("|[a-z0-9]+(/[a-z0-9]+)*");
    private static final Pattern FILE = Pattern.compile(
            "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.(jpg|png)");

    private final Path root;

    /** A blank root means {@code ~/.paspm/media}. */
    public LocalMediaStorage(String root) {
        String configured = root == null || root.isBlank()
                ? Path.of(System.getProperty("user.home"), ".paspm", "media").toString()
                : root;
        this.root = Path.of(configured).toAbsolutePath().normalize();
    }

    /** True only for a generated media file name: lowercase UUID plus {@code .png} or {@code .jpg}. */
    public static boolean isFileName(String fileName) {
        return fileName != null && FILE.matcher(fileName).matches();
    }

    public Path root() {
        return root;
    }

    @Override
    public String put(String folder, byte[] content, String extension) throws IOException {
        if (!".jpg".equals(extension) && !".png".equals(extension)) {
            throw new IllegalArgumentException("Unsupported media extension");
        }
        Path directory = directory(folder);
        Files.createDirectories(directory);
        String fileName = UUID.randomUUID() + extension;
        Path target = file(directory, fileName);
        Path temporary = Files.createTempFile(directory, ".media-", ".tmp");
        boolean moved = false;
        try {
            Files.write(temporary, content);
            try {
                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(temporary, target);
            }
            moved = true;
            return fileName;
        } finally {
            if (!moved) {
                Files.deleteIfExists(temporary);
            }
        }
    }

    @Override
    public StoredMedia get(String folder, String fileName) throws IOException {
        Path file = file(directory(folder), fileName);
        return new StoredMedia(Files.readAllBytes(file), fileName.endsWith(".png") ? "image/png" : "image/jpeg");
    }

    @Override
    public void delete(String folder, String fileName) throws IOException {
        Files.deleteIfExists(file(directory(folder), fileName));
    }

    private Path directory(String folder) throws IOException {
        if (folder == null || !FOLDER.matcher(folder).matches()) {
            throw new IOException("Invalid media folder");
        }
        Path directory = root.resolve(folder).normalize();
        if (!directory.startsWith(root)) {
            throw new IOException("Invalid media folder");
        }
        return directory;
    }

    private static Path file(Path directory, String fileName) throws IOException {
        if (fileName == null || !FILE.matcher(fileName).matches()) {
            throw new IOException("Invalid media file name");
        }
        Path file = directory.resolve(fileName).normalize();
        if (!directory.equals(file.getParent())) {
            throw new IOException("Invalid media file path");
        }
        return file;
    }
}
