package vn.edu.fpt.paspm.user;

import java.io.IOException;

public interface AvatarObjectStorage {
    String put(byte[] content, String extension) throws IOException;

    StoredAvatar get(String objectKey) throws IOException;

    void delete(String objectKey) throws IOException;

    record StoredAvatar(byte[] content, String contentType) {
    }
}
