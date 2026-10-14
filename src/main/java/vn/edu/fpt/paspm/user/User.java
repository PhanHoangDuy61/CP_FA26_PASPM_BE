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
        String purpose) {

    public UserView view() {
        return new UserView(id, email, name, role, storeId, status, verifiedAt != null);
    }

    public boolean active() {
        return "ACTIVE".equals(status) && verifiedAt != null;
    }
}
