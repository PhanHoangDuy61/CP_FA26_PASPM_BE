package vn.edu.fpt.paspm.catalog.store;

import io.swagger.v3.oas.annotations.Operation;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import vn.edu.fpt.paspm.auth.AuthService;
import vn.edu.fpt.paspm.catalog.store.StoreContracts.StoreView;
import vn.edu.fpt.paspm.common.ApiException;
import vn.edu.fpt.paspm.user.User;

/**
 * Store icon and banner (DEC-20261123-01 §5). SYSTEM_ADMIN: any Store (INACTIVE included). STORE_ADMIN: own Store only.
 * MERCHANDISER: read own Store. A Store user naming another Store gets 404.
 */
@RestController
@RequestMapping("/api/stores/{storeId}/images/{kind}")
public class StoreImageController {
    private final StoreImageService images;
    private final AuthService auth;

    public StoreImageController(StoreImageService images, AuthService auth) {
        this.images = images;
        this.auth = auth;
    }

    private void requireAccess(Jwt jwt, int storeId) {
        User user = auth.current(jwt);
        if (!"SYSTEM_ADMIN".equals(user.role()) && (user.storeId() == null || user.storeId() != storeId)) {
            throw new ApiException(404, "Store not found");
        }
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('SYSTEM_ADMIN','STORE_ADMIN','MERCHANDISER')")
    @Operation(summary = "Read a Store's icon or banner (authenticated; also for an INACTIVE Store)",
            description = "kind = icon or banner. Image bytes with Cache-Control: private, no-store. 404 without an image.")
    public ResponseEntity<byte[]> getStoreImage(@AuthenticationPrincipal Jwt jwt, @PathVariable int storeId, @PathVariable String kind) {
        StoreImageKind imageKind = StoreImageKind.fromPath(kind);
        requireAccess(jwt, storeId);
        var stored = images.read(storeId, imageKind);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(stored.contentType()))
                .header("Cache-Control", "private, no-store")
                .header("X-Content-Type-Options", "nosniff")
                .body(stored.content());
    }

    @PutMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('SYSTEM_ADMIN','STORE_ADMIN')")
    @Operation(summary = "Upload or replace a Store's icon or banner",
            description = "Multipart part 'file'. PNG or JPEG (magic bytes + decode), at most 4000×4000 px; icon ≤ 1 MiB, "
                    + "banner ≤ 5 MiB (413). The old file is deleted after commit.")
    public StoreView putStoreImage(@AuthenticationPrincipal Jwt jwt, @PathVariable int storeId, @PathVariable String kind,
                         @RequestParam("file") MultipartFile file) {
        StoreImageKind imageKind = StoreImageKind.fromPath(kind);
        requireAccess(jwt, storeId);
        return images.replace(storeId, imageKind, file);
    }

    @DeleteMapping
    @PreAuthorize("hasAnyRole('SYSTEM_ADMIN','STORE_ADMIN')")
    @Operation(summary = "Remove a Store's icon or banner (the URL becomes null; the file is deleted after commit)")
    public StoreView deleteStoreImage(@AuthenticationPrincipal Jwt jwt, @PathVariable int storeId, @PathVariable String kind) {
        StoreImageKind imageKind = StoreImageKind.fromPath(kind);
        requireAccess(jwt, storeId);
        return images.remove(storeId, imageKind);
    }
}
