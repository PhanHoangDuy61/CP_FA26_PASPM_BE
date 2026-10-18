package vn.edu.fpt.paspm.user;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import io.swagger.v3.oas.annotations.Operation;
import vn.edu.fpt.paspm.auth.AuthService;
import vn.edu.fpt.paspm.common.Contracts.UserView;

@RestController
public class ProfileAvatarController {
    private final AvatarService avatars;
    private final AuthService auth;

    public ProfileAvatarController(AvatarService avatars, AuthService auth) {
        this.avatars = avatars;
        this.auth = auth;
    }

    @Operation(
            summary = "Xem ảnh profile của chính mình",
            description = "Chỉ người dùng sở hữu tài khoản có thể đọc ảnh; response là JPEG hoặc PNG.")
    @GetMapping("/api/users/{id}/avatar")
    public ResponseEntity<byte[]> getAvatar(
            @PathVariable int id,
            @AuthenticationPrincipal Jwt jwt,
            HttpServletResponse response) {
        AvatarObjectStorage.StoredAvatar avatar = avatars.get(id, auth.current(jwt).id());
        response.setHeader("X-Content-Type-Options", "nosniff");
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(avatar.contentType()))
                .cacheControl(CacheControl.noStore())
                .body(avatar.content());
    }

    @Operation(
            summary = "Tải/cập nhật ảnh profile của chính mình",
            description = "Multipart field `file`; chỉ JPEG/PNG đã xác thực nội dung, tối đa 5 MiB, 4096 px mỗi chiều và 12 megapixel.")
    @PutMapping(value = "/api/users/me/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public UserView updateAvatar(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam("file") MultipartFile file) {
        return avatars.replace(auth.current(jwt).id(), file);
    }

    @Operation(summary = "Xóa ảnh profile của chính mình")
    @DeleteMapping("/api/users/me/avatar")
    public UserView deleteAvatar(@AuthenticationPrincipal Jwt jwt) {
        return avatars.delete(auth.current(jwt).id());
    }
}
