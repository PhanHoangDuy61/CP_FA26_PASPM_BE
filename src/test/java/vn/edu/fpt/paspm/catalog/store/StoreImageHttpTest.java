package vn.edu.fpt.paspm.catalog.store;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.support.TransactionTemplate;

import com.fasterxml.jackson.databind.ObjectMapper;

import vn.edu.fpt.paspm.auth.AuthService;
import vn.edu.fpt.paspm.user.UserRepository;

/** DEC-20261123-01 §5: Store branding images (icon, banner). */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:storeimages;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa", "spring.datasource.password=", "spring.flyway.enabled=true",
        "paspm.jwt-secret=01234567890123456789012345678901", "paspm.otp-secret=01234567890123456789012345678901",
        "paspm.bootstrap-email=", "paspm.bootstrap-password=",
        "paspm.media.root=${java.io.tmpdir}/paspm-store-images-" + "test", "paspm.avatar.directory=${java.io.tmpdir}/paspm-store-images-test/avatars"})
@AutoConfigureMockMvc
class StoreImageHttpTest {
    static final Path ROOT = Path.of(System.getProperty("java.io.tmpdir"), "paspm-store-images-test");
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired UserRepository users;
    @Autowired AuthService auth;
    @Autowired StoreImageService images;
    @Autowired TransactionTemplate transactions;
    final ObjectMapper json = new ObjectMapper();

    String token(String role, Integer store) {
        var user = users.createPendingUser("Images " + role, UUID.randomUUID() + "@example.test", role, store, null, null);
        jdbc.update("UPDATE app_user SET status='ACTIVE',email_verified_at=CURRENT_TIMESTAMP WHERE user_id=?", user.id());
        return "Bearer " + auth.token(users.findById(user.id())).accessToken();
    }

    int store() {
        jdbc.update("INSERT INTO store(name,slug) VALUES (?,?)", "Images " + UUID.randomUUID(), "images-" + UUID.randomUUID());
        return jdbc.queryForObject("SELECT MAX(store_id) FROM store", Integer.class);
    }

    static byte[] image(String format, int width, int height) throws Exception {
        var out = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB), format, out);
        return out.toByteArray();
    }

    ResultActions upload(int store, String kind, String bearer, byte[] content, String fileName) throws Exception {
        var request = multipart("/api/stores/" + store + "/images/" + kind).file(new MockMultipartFile("file", fileName, "application/octet-stream", content));
        request.with(r -> { r.setMethod("PUT"); return r; });
        if (bearer != null) request.header("Authorization", bearer);
        return mvc.perform(request);
    }

    String key(int store, String column) {
        return jdbc.queryForObject("SELECT " + column + " FROM store WHERE store_id=?", String.class, store);
    }

    @Test void systemAdminAndOwnStoreAdminUploadPngOrJpegStoredUnderAUuidName() throws Exception {
        int store = store();
        String system = token("SYSTEM_ADMIN", null), storeAdmin = token("STORE_ADMIN", store);
        byte[] png = image("png", 64, 64), jpeg = image("jpg", 1200, 300);
        var body = json.readTree(upload(store, "icon", system, png, "../../evil.svg").andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(2))
                .andExpect(jsonPath("$.bannerUrl").value(nullValue()))
                .andReturn().getResponse().getContentAsString());
        String iconKey = key(store, "icon_key");
        assertTrue(iconKey.matches("stores/" + store + "/[0-9a-f-]{36}\\.png"), iconKey);
        assertEquals("/api/public/stores/" + store + "/images/" + iconKey.substring(iconKey.lastIndexOf('/') + 1), body.path("iconUrl").asText());
        assertArrayEquals(png, Files.readAllBytes(ROOT.resolve(iconKey)));

        upload(store, "banner", storeAdmin, jpeg, "banner.jpeg").andExpect(status().isOk())
                .andExpect(jsonPath("$.bannerUrl", matchesRegex("/api/public/stores/" + store + "/images/[0-9a-f-]{36}\\.jpg")));
        assertTrue(key(store, "banner_key").endsWith(".jpg"));
    }

    @Test void rejectsWrongTypesSizesAndDimensions() throws Exception {
        int store = store();
        String system = token("SYSTEM_ADMIN", null);
        byte[] gif = image("gif", 10, 10);
        byte[] svg = "<svg xmlns=\"http://www.w3.org/2000/svg\"/>".getBytes(StandardCharsets.UTF_8);
        byte[] fakePng = "not really a png".getBytes(StandardCharsets.UTF_8);
        byte[] truncated = java.util.Arrays.copyOf(image("png", 50, 50), 40);
        for (byte[] bad : new byte[][]{gif, svg, fakePng, truncated, new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0}})
            upload(store, "icon", system, bad, "image.png").andExpect(status().isBadRequest());
        upload(store, "icon", system, new byte[0], "empty.png").andExpect(status().isBadRequest());
        upload(store, "icon", system, image("png", 4001, 1), "wide.png").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(containsString("4000")));
        byte[] overIcon = new byte[1024 * 1024 + 1];
        System.arraycopy(image("png", 10, 10), 0, overIcon, 0, 8);
        upload(store, "icon", system, overIcon, "big.png").andExpect(status().isPayloadTooLarge());
        upload(store, "banner", system, new byte[5 * 1024 * 1024 + 1], "big.png").andExpect(status().isPayloadTooLarge());
        upload(store, "logo", system, image("png", 10, 10), "x.png").andExpect(status().isNotFound());
        assertNull(key(store, "icon_key"));
        assertNull(key(store, "banner_key"));
    }

    @Test void permissionsFollowRoleAndOwnStore() throws Exception {
        int store = store(), other = store();
        byte[] png = image("png", 8, 8);
        upload(store, "icon", token("STORE_ADMIN", other), png, "x.png").andExpect(status().isNotFound());
        upload(store, "icon", token("MERCHANDISER", store), png, "x.png").andExpect(status().isForbidden());
        upload(store, "icon", token("END_CUSTOMER", null), png, "x.png").andExpect(status().isForbidden());
        upload(store, "icon", null, png, "x.png").andExpect(status().isUnauthorized());
        upload(999999, "icon", token("SYSTEM_ADMIN", null), png, "x.png").andExpect(status().isNotFound());
        mvc.perform(delete("/api/stores/" + store + "/images/icon").header("Authorization", token("MERCHANDISER", store))).andExpect(status().isForbidden());
        mvc.perform(delete("/api/stores/" + store + "/images/icon").header("Authorization", token("STORE_ADMIN", other))).andExpect(status().isNotFound());
    }

    @Test void replacingDeletesTheOldFileAfterCommitAndDeleteClearsTheUrl() throws Exception {
        int store = store();
        String system = token("SYSTEM_ADMIN", null);
        upload(store, "icon", system, image("png", 8, 8), "a.png").andExpect(status().isOk());
        String first = key(store, "icon_key");
        upload(store, "icon", system, image("png", 9, 9), "b.png").andExpect(status().isOk());
        String second = key(store, "icon_key");
        assertNotEquals(first, second);
        assertFalse(Files.exists(ROOT.resolve(first)), "the replaced file is deleted after commit");
        assertTrue(Files.exists(ROOT.resolve(second)));
        mvc.perform(delete("/api/stores/" + store + "/images/icon").header("Authorization", system))
                .andExpect(status().isOk()).andExpect(jsonPath("$.iconUrl").value(nullValue()));
        assertNull(key(store, "icon_key"));
        assertFalse(Files.exists(ROOT.resolve(second)));
    }

    @Test void aRolledBackUploadDeletesTheNewFileAndKeepsTheOldKey() throws Exception {
        int store = store();
        upload(store, "banner", token("SYSTEM_ADMIN", null), image("png", 8, 8), "a.png").andExpect(status().isOk());
        String kept = key(store, "banner_key");
        long before;
        try (var files = Files.list(ROOT.resolve("stores/" + store))) { before = files.count(); }
        byte[] png = image("png", 7, 7);
        transactions.executeWithoutResult(tx -> {
            images.replace(store, StoreImageKind.BANNER, new MockMultipartFile("file", "x.png", "image/png", png));
            tx.setRollbackOnly();
        });
        assertEquals(kept, key(store, "banner_key"));
        assertTrue(Files.exists(ROOT.resolve(kept)), "the committed file is kept");
        try (var files = Files.list(ROOT.resolve("stores/" + store))) { assertEquals(before, files.count(), "the rolled-back file is deleted"); }
    }

    @Test void publicRouteServesOnlyTheCurrentImagesOfActiveStoresWithLongCaching() throws Exception {
        int store = store();
        String system = token("SYSTEM_ADMIN", null);
        byte[] png = image("png", 12, 12);
        String url = json.readTree(upload(store, "icon", system, png, "i.png").andReturn().getResponse().getContentAsString()).path("iconUrl").asText();
        mvc.perform(get(url)).andExpect(status().isOk())
                .andExpect(content().contentType("image/png")).andExpect(content().bytes(png))
                .andExpect(header().string("Cache-Control", allOf(containsString("public"), containsString("max-age=31536000"), containsString("immutable"))))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"));
        String fileName = url.substring(url.lastIndexOf('/') + 1);
        upload(store, "icon", system, image("png", 13, 13), "j.png").andExpect(status().isOk());
        mvc.perform(get(url)).andExpect(status().isNotFound());
        mvc.perform(get("/api/public/stores/999999/images/" + fileName)).andExpect(status().isNotFound());
        mvc.perform(get("/api/public/stores/" + store + "/images/not-a-file.png")).andExpect(status().isNotFound());
        String current = key(store, "icon_key");
        String currentUrl = "/api/public/stores/" + store + "/images/" + current.substring(current.lastIndexOf('/') + 1);
        mvc.perform(get(currentUrl)).andExpect(status().isOk());
        jdbc.update("UPDATE store SET status='INACTIVE' WHERE store_id=?", store);
        mvc.perform(get(currentUrl)).andExpect(status().isNotFound());
        mvc.perform(post(currentUrl)).andExpect(status().is4xxClientError());
    }

    @Test void onlyGetOnUuidImageFileNamesIsPublicAndPathTraversalNeverReachesTheDisk() throws Exception {
        int store = store();
        upload(store, "icon", token("SYSTEM_ADMIN", null), image("png", 8, 8), "i.png").andExpect(status().isOk());
        // Same Store, ACTIVE, but names that are not a UUID file name: 404 before any file access.
        for (String name : new String[]{"passwd", "x.png", "..png", "01234567-89ab-cdef-0123-456789abcdef.svg",
                "01234567-89AB-CDEF-0123-456789ABCDEF.png", "%2e%2e%2f%2e%2e%2fetc%2fpasswd"})
            mvc.perform(get("/api/public/stores/" + store + "/images/" + name)).andExpect(status().is4xxClientError())
                    .andExpect(result -> assertNotEquals(200, result.getResponse().getStatus(), name));
        // Encoded slashes / dot segments are rejected by the firewall or fall outside the public pattern.
        for (String path : new String[]{"/api/public/stores/" + store + "/images/..%2F..%2Fapplication.yml",
                "/api/public/stores/" + store + "/images/a/b.png", "/api/public/stores/" + store + "/other",
                "/api/public/stores/" + store, "/api/public/anything"})
            mvc.perform(get(path)).andExpect(result -> assertTrue(result.getResponse().getStatus() == 400 || result.getResponse().getStatus() == 401, path + " -> " + result.getResponse().getStatus()));
        // Only GET is anonymous.
        String current = key(store, "icon_key");
        String url = "/api/public/stores/" + store + "/images/" + current.substring(current.lastIndexOf('/') + 1);
        mvc.perform(get(url)).andExpect(status().isOk());
        for (var method : new org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder[]{post(url), put(url), delete(url), patch(url)})
            mvc.perform(method).andExpect(status().isUnauthorized());
    }

    @Test void storeAdminOfOneStoreCannotReachAnotherStoresImagesOrProfile() throws Exception {
        int a = store(), b = store();
        String adminA = token("STORE_ADMIN", a);
        upload(b, "icon", token("SYSTEM_ADMIN", null), image("png", 8, 8), "b.png").andExpect(status().isOk());
        // Store isolation rule: another Store's resource answers 404 (not 403), so its existence is not revealed.
        mvc.perform(get("/api/stores/" + b + "/images/icon").header("Authorization", adminA)).andExpect(status().isNotFound());
        upload(b, "icon", adminA, image("png", 8, 8), "x.png").andExpect(status().isNotFound());
        mvc.perform(delete("/api/stores/" + b + "/images/icon").header("Authorization", adminA)).andExpect(status().isNotFound());
        // The profile route has no Store parameter: it always resolves to the caller's own Store.
        mvc.perform(get("/api/store/profile").header("Authorization", adminA)).andExpect(status().isOk()).andExpect(jsonPath("$.storeId").value(a));
        mvc.perform(get("/api/store/profile?storeId=" + b).header("Authorization", adminA)).andExpect(status().isOk()).andExpect(jsonPath("$.storeId").value(a));
        assertNotNull(key(b, "icon_key"));
    }

    @Test void authenticatedRouteLetsSystemAdminPreviewAnInactiveStoreAndStoreUsersTheirOwn() throws Exception {
        int store = store(), other = store();
        String system = token("SYSTEM_ADMIN", null), merchandiser = token("MERCHANDISER", store);
        byte[] png = image("png", 14, 14);
        mvc.perform(get("/api/stores/" + store + "/images/icon").header("Authorization", system)).andExpect(status().isNotFound());
        upload(store, "icon", system, png, "i.png").andExpect(status().isOk());
        jdbc.update("UPDATE store SET status='INACTIVE' WHERE store_id=?", store);
        mvc.perform(get("/api/stores/" + store + "/images/icon").header("Authorization", system)).andExpect(status().isOk())
                .andExpect(content().bytes(png)).andExpect(header().string("Cache-Control", containsString("no-store")))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"));
        jdbc.update("UPDATE store SET status='ACTIVE' WHERE store_id=?", store);
        mvc.perform(get("/api/stores/" + store + "/images/icon").header("Authorization", merchandiser)).andExpect(status().isOk());
        mvc.perform(get("/api/stores/" + store + "/images/icon").header("Authorization", token("STORE_ADMIN", other))).andExpect(status().isNotFound());
        mvc.perform(get("/api/stores/" + store + "/images/icon").header("Authorization", token("END_CUSTOMER", null))).andExpect(status().isForbidden());
    }
}
