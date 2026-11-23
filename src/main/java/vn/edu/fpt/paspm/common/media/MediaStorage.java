package vn.edu.fpt.paspm.common.media;

import java.io.IOException;

/**
 * DEC-20261123-01 §5: shared storage for uploaded images (Store icons and banners, profile avatars). Callers keep only a
 * relative key (folder + generated file name), never an absolute path, URL or BLOB, so the implementation can later move to
 * object storage (MinIO/S3) without data changes.
 */
public interface MediaStorage {
    /**
     * Writes {@code content} under {@code folder} ("" for the root, otherwise lowercase segments such as {@code stores/12})
     * with a new random {@code UUID + extension} name, and returns that file name. Only {@code .png} and {@code .jpg}.
     */
    String put(String folder, byte[] content, String extension) throws IOException;

    StoredMedia get(String folder, String fileName) throws IOException;

    /** Deleting a missing file is not an error. */
    void delete(String folder, String fileName) throws IOException;

    record StoredMedia(byte[] content, String contentType) {
    }
}
