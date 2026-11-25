package vn.edu.fpt.paspm.common.media;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
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

import vn.edu.fpt.paspm.auth.AuthService;
import vn.edu.fpt.paspm.user.UserRepository;

/** DEC-20261123-01 (Checkpoint 3): a database key whose media file is gone answers 404, never 503 (avatars and Store images). */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:mediamissing;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa", "spring.datasource.password=", "spring.flyway.enabled=true",
        "paspm.jwt-secret=01234567890123456789012345678901", "paspm.otp-secret=01234567890123456789012345678901",
        "paspm.bootstrap-email=", "paspm.bootstrap-password=",
        "paspm.media.root=${java.io.tmpdir}/paspm-media-missing-test", "paspm.avatar.directory=${java.io.tmpdir}/paspm-media-missing-test-avatars"})
@AutoConfigureMockMvc
class MediaFileMissingHttpTest {
    static final Path MEDIA = Path.of(System.getProperty("java.io.tmpdir"), "paspm-media-missing-test");
    static final Path AVATARS = Path.of(System.getProperty("java.io.tmpdir"), "paspm-media-missing-test-avatars");
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired UserRepository users;
    @Autowired AuthService auth;

    String token(String role, Integer store) {
        var user = users.createPendingUser("Missing " + role, UUID.randomUUID() + "@example.test", role, store, null, null);
        jdbc.update("UPDATE app_user SET status='ACTIVE',email_verified_at=CURRENT_TIMESTAMP WHERE user_id=?", user.id());
        return "Bearer " + auth.token(users.findById(user.id())).accessToken();
    }

    static byte[] png() throws Exception {
        var out = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(6, 6, BufferedImage.TYPE_INT_RGB), "png", out);
        return out.toByteArray();
    }

    @Test void aMissingAvatarFileIs404NotStorageUnavailable() throws Exception {
        String bearer = token("END_CUSTOMER", null);
        int userId = jdbc.queryForObject("SELECT MAX(user_id) FROM app_user", Integer.class);
        var avatar = multipart("/api/users/me/avatar").file(new MockMultipartFile("file", "a.png", "image/png", png()));
        avatar.with(r -> { r.setMethod("PUT"); return r; });
        mvc.perform(avatar.header("Authorization", bearer)).andExpect(status().isOk());
        String key = users.findById(userId).avatarKey();
        Files.delete(AVATARS.resolve(key));
        mvc.perform(get("/api/users/" + userId + "/avatar").header("Authorization", bearer))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.error").value("Avatar not found"));
    }

    @Test void aMissingStoreImageFileIs404OnTheAuthenticatedAndThePublicRoute() throws Exception {
        jdbc.update("INSERT INTO store(name,slug) VALUES (?,?)", "Missing " + UUID.randomUUID(), "missing-" + UUID.randomUUID());
        int store = jdbc.queryForObject("SELECT MAX(store_id) FROM store", Integer.class);
        String system = token("SYSTEM_ADMIN", null);
        var upload = multipart("/api/stores/" + store + "/images/icon").file(new MockMultipartFile("file", "i.png", "image/png", png()));
        upload.with(r -> { r.setMethod("PUT"); return r; });
        mvc.perform(upload.header("Authorization", system)).andExpect(status().isOk());
        String key = jdbc.queryForObject("SELECT icon_key FROM store WHERE store_id=?", String.class, store);
        Files.delete(MEDIA.resolve(key));
        mvc.perform(get("/api/stores/" + store + "/images/icon").header("Authorization", system)).andExpect(status().isNotFound());
        mvc.perform(get("/api/public/stores/" + store + "/images/" + key.substring(key.lastIndexOf('/') + 1))).andExpect(status().isNotFound());
    }
}
