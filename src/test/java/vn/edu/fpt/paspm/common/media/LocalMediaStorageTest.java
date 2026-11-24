package vn.edu.fpt.paspm.common.media;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import vn.edu.fpt.paspm.user.LocalAvatarObjectStorage;

/** DEC-20261123-01 §5: the shared local media storage (Store images and avatars). */
class LocalMediaStorageTest {
    @TempDir Path root;

    @Test void storesUnderAFolderWithARandomUuidNameAndReadsAndDeletesIt() throws IOException {
        var storage = new LocalMediaStorage(root.toString());
        byte[] png = {(byte) 0x89, 'P', 'N', 'G'};
        String name = storage.put("stores/12", png, ".png");
        assertTrue(name.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.png"), name);
        assertTrue(Files.isRegularFile(root.resolve("stores/12").resolve(name)));
        var stored = storage.get("stores/12", name);
        assertArrayEquals(png, stored.content());
        assertEquals("image/png", stored.contentType());
        assertNotEquals(name, storage.put("stores/12", png, ".png"), "every write gets a new name");
        assertEquals("image/jpeg", storage.get("stores/12", storage.put("stores/12", png, ".jpg")).contentType());
        storage.delete("stores/12", name);
        assertFalse(Files.exists(root.resolve("stores/12").resolve(name)));
        storage.delete("stores/12", name);
        try (var leftovers = Files.list(root.resolve("stores/12"))) {
            assertTrue(leftovers.noneMatch(p -> p.getFileName().toString().endsWith(".tmp")), "no temporary file is left");
        }
    }

    @Test void refusesUnsupportedTypesAndPathsOutsideTheRoot() {
        var storage = new LocalMediaStorage(root.toString());
        byte[] content = {1, 2, 3};
        assertThrows(IllegalArgumentException.class, () -> storage.put("stores/1", content, ".svg"));
        assertThrows(IllegalArgumentException.class, () -> storage.put("stores/1", content, ".webp"));
        for (String folder : new String[]{"../outside", "/abs", "stores/../..", "Stores", "stores//1", "stores/1/"})
            assertThrows(IOException.class, () -> storage.put(folder, content, ".png"), folder);
        for (String file : new String[]{"../../etc/passwd", "x.png", "01234567-89ab-cdef-0123-456789abcdef.svg", "01234567-89ab-cdef-0123-456789abcdef.png/.."})
            assertThrows(IOException.class, () -> storage.get("stores/1", file), file);
    }

    @Test void avatarStorageKeepsItsBareKeysDirectlyInTheAvatarDirectory() throws IOException {
        Path avatars = root.resolve("avatars");
        var avatarsStorage = new LocalAvatarObjectStorage(avatars.toString());
        byte[] png = {(byte) 0x89, 'P', 'N', 'G'};
        String key = avatarsStorage.put(png, ".png");
        assertTrue(key.matches("[0-9a-f-]{36}\\.png"));
        assertTrue(Files.isRegularFile(avatars.resolve(key)));
        assertArrayEquals(png, avatarsStorage.get(key).content());
        assertThrows(IOException.class, () -> avatarsStorage.get("../" + key));
        avatarsStorage.delete(key);
        assertThrows(IOException.class, () -> avatarsStorage.get(key));
    }
}
