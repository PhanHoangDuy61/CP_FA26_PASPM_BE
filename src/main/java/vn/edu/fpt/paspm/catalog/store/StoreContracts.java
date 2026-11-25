package vn.edu.fpt.paspm.catalog.store;

import java.time.Instant;
import java.util.List;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** DEC-20261123-01: Store management contracts (F01). Request records are strict: unknown fields return 400. */
public final class StoreContracts {
    /** Lowercase letters, digits and single hyphens (the F09 {@code storeSlug}). */
    public static final String SLUG = "[a-z0-9]+(-[a-z0-9]+)*";
    /** Digits, spaces and {@code + ( ) . -} only. */
    public static final String PHONE = "[0-9+(). \\-]*";

    private StoreContracts() {
    }

    /**
     * A Store as every role sees it. {@code iconUrl}/{@code bannerUrl} are the public image URLs
     * ({@code /api/public/stores/{storeId}/images/{fileName}}), {@code null} without an image; they only serve while the
     * Store is ACTIVE.
     */
    public record StoreView(int storeId, String name, String slug, String status, String address, String phone,
                            String iconUrl, String bannerUrl, long version, Instant createdAt, Instant updatedAt,
                            Instant deactivatedAt) {
    }

    /** Read-only list of the accounts assigned to a Store (assignment itself stays in F01 account management). */
    public record AssignedUser(int userId, String name, String email, String role, String status) {
    }

    /** System Admin detail: the Store, who deactivated it, and its assigned users (read-only). */
    public record StoreDetailView(int storeId, String name, String slug, String status, String address, String phone,
                                  String iconUrl, String bannerUrl, long version, Instant createdAt, Instant updatedAt,
                                  Instant deactivatedAt, Integer deactivatedBy, int assignedUserCount,
                                  List<AssignedUser> assignedUsers) {
    }

    public record CreateStore(
            @NotNull @Size(max = 150) String name,
            @NotBlank @Size(max = 50) @Pattern(regexp = SLUG) String slug,
            @Size(max = 255) String address,
            @Size(max = 30) @Pattern(regexp = PHONE) String phone) {
    }

    public record UpdateStore(
            @NotNull @Min(1) Long expectedVersion,
            @NotNull @Size(max = 150) String name,
            @NotBlank @Size(max = 50) @Pattern(regexp = SLUG) String slug,
            @Size(max = 255) String address,
            @Size(max = 30) @Pattern(regexp = PHONE) String phone) {
    }

    public record ChangeStatus(
            @NotNull @Min(1) Long expectedVersion,
            @NotNull @Pattern(regexp = "ACTIVE|INACTIVE") String status) {
    }

    /** Store Admin: contact fields only. {@code name}, {@code slug} and {@code status} are unknown fields here (400). */
    public record UpdateProfile(
            @NotNull @Min(1) Long expectedVersion,
            @Size(max = 255) String address,
            @Size(max = 30) @Pattern(regexp = PHONE) String phone) {
    }
}
