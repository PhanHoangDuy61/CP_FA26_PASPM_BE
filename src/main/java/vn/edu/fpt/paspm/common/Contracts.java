package vn.edu.fpt.paspm.common;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public final class Contracts {
    private Contracts() {
    }

    public record Login(
            @NotBlank @Email String email,
            @NotBlank @Size(max = 72) String password) {
    }

    public record Registration(
            @NotBlank @Size(max = 150) String name,
            @NotBlank @Email @Size(max = 254) String email,
            @NotBlank @Size(min = 12, max = 72) @Pattern(regexp = "(?s)(?=.*[a-z])(?=.*[A-Z])(?=.*[0-9]).+") String password) {
    }

    public record CreateUser(
            @NotBlank @Size(max = 150) String name,
            @NotBlank @Email @Size(max = 254) String email,
            @NotBlank String roleCode,
            Integer storeId) {
    }

    public record UpdateUser(@NotBlank @Size(max = 150) String name) {
    }

    public record Status(@NotBlank @Pattern(regexp = "ACTIVE|INACTIVE") String status) {
    }

    public record RoleAssignment(@NotBlank String roleCode, Integer storeId) {
    }

    public record OtpRequest(
            @NotBlank @Email String email,
            @NotBlank @Pattern(regexp = "SELF_REGISTER|ADMIN_INVITE") String purpose) {
    }

    public record Verify(
            @NotBlank @Email String email,
            @NotBlank @Pattern(regexp = "[0-9]{6}") String otp) {
    }

    public record Activate(
            @NotBlank @Email String email,
            @NotBlank @Pattern(regexp = "[0-9]{6}") String otp,
            @NotBlank @Size(min = 12, max = 72) @Pattern(regexp = "(?s)(?=.*[a-z])(?=.*[A-Z])(?=.*[0-9]).+") String password) {
    }

    public record Google(@NotBlank @Size(max = 8192) String idToken) {
    }

    public record UserView(
            int userId,
            String email,
            String name,
            String roleCode,
            Integer storeId,
            String status,
            boolean emailVerified) {
    }

    public record Token(String accessToken, long expiresIn, String tokenType) {
    }
}
