package vn.edu.fpt.paspm.catalog.store;

import io.swagger.v3.oas.annotations.Operation;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import vn.edu.fpt.paspm.common.ApiException;
import vn.edu.fpt.paspm.common.media.LocalMediaStorage;

/**
 * The only anonymous route (DEC-20261123-01 §5): the current icon or banner of an ACTIVE Store, by Store id and the UUID
 * file name from {@code iconUrl}/{@code bannerUrl}. File names change on every upload, so responses are cached for a year.
 */
@RestController
public class PublicStoreImageController {
    private final StoreImageService images;

    public PublicStoreImageController(StoreImageService images) {
        this.images = images;
    }

    @GetMapping("/api/public/stores/{storeId}/images/{fileName}")
    @io.swagger.v3.oas.annotations.security.SecurityRequirements
    @Operation(summary = "Public: current Store icon or banner (ACTIVE Stores only)", security = {},
            description = "404 for an unknown or INACTIVE Store, or a file name that is not the current icon or banner.")
    public ResponseEntity<byte[]> getPublicStoreImage(@PathVariable int storeId, @PathVariable String fileName) {
        // Only generated UUID file names are ever looked up; anything else (dot segments, other names) is 404.
        if (!LocalMediaStorage.isFileName(fileName)) {
            throw new ApiException(404, "Store image not found");
        }
        var stored = images.readPublic(storeId, fileName);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(stored.contentType()))
                .header("Cache-Control", "public, max-age=31536000, immutable")
                .header("X-Content-Type-Options", "nosniff")
                .body(stored.content());
    }
}
