package vn.edu.fpt.paspm.user;

import java.time.Instant;

import vn.edu.fpt.paspm.common.Contracts.UserView;

public record User(
        int id,
        String email,
        String name,
        String role,
        Integer storeId,
        String status,
        String passwordHash,
        Instant verifiedAt,
        int version,
        String purpose,
        String avatarKey,
        String storeStatus) {

    /** A user row without its Store status (callers that do not need the central Store guard). */
    public User(int id, String email, String name, String role, Integer storeId, String status, String passwordHash,
                Instant verifiedAt, int version, String purpose, String avatarKey) {
        this(id, email, name, role, storeId, status, passwordHash, verifiedAt, version, purpose, avatarKey, null);
    }

    public UserView view() {
        return new UserView(
                id,
                email,
                name,
                role,
                storeId,
                status,
                verifiedAt != null,
                avatarKey == null ? null : "/api/users/" + id + "/avatar"
        );
    }

    public boolean active() {
        return "ACTIVE".equals(status) && verifiedAt != null;
    }

    /** DEC-20261123-01 §4: the account belongs to a Store that a System Admin deactivated. */
    public boolean storeInactive() {
        return storeId != null && "INACTIVE".equals(storeStatus);
    }
}
