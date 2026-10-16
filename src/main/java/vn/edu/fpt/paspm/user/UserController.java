package vn.edu.fpt.paspm.user;

import java.util.List;
import java.util.Map;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import vn.edu.fpt.paspm.auth.AuthService;
import vn.edu.fpt.paspm.common.Contracts.CreateUser;
import vn.edu.fpt.paspm.common.Contracts.RoleAssignment;
import vn.edu.fpt.paspm.common.Contracts.Status;
import vn.edu.fpt.paspm.common.Contracts.UpdateUser;
import vn.edu.fpt.paspm.common.Contracts.UserView;

@RestController
@PreAuthorize("hasRole('SYSTEM_ADMIN')")
public class UserController {
    private final UserRepository userRepository;
    private final AccountService accounts;
    private final AuthService auth;

    public UserController(UserRepository userRepository, AccountService accounts, AuthService auth) {
        this.userRepository = userRepository;
        this.accounts = accounts;
        this.auth = auth;
    }

    @Operation(
            summary = "Danh sách tài khoản",
            description = "SYSTEM_ADMIN: lọc bằng q (email/tên), role, status; phân trang bằng page bắt đầu từ 0 và size tối đa 100.")
    @GetMapping("/api/users")
    public List<UserView> listUsers(
            @RequestParam(defaultValue = "") String q,
            @RequestParam(defaultValue = "") String role,
            @RequestParam(defaultValue = "") String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return userRepository.searchUsers(q, role, status, page, size);
    }

    @Operation(summary = "Xem tài khoản", description = "SYSTEM_ADMIN: trả thông tin tài khoản theo ID.")
    @GetMapping("/api/users/{id}")
    public UserView getUser(@PathVariable int id) {
        return userRepository.findById(id).view();
    }

    @Operation(
            summary = "Mời tạo tài khoản",
            description = "SYSTEM_ADMIN: tạo tài khoản chờ xác minh theo role/store hợp lệ và gửi OTP invitation đến email.")
    @PostMapping("/api/users")
    @ResponseStatus(HttpStatus.CREATED)
    public UserView createUser(@Valid @RequestBody CreateUser request) {
        return accounts.inviteUser(request);
    }

    @Operation(
            summary = "Cập nhật tên tài khoản",
            description = "SYSTEM_ADMIN: chỉ cập nhật tên; email không đổi để không bỏ qua xác minh.")
    @PutMapping("/api/users/{id}")
    public UserView updateUser(@PathVariable int id, @Valid @RequestBody UpdateUser request) {
        return accounts.updateUser(id, request);
    }

    @Operation(
            summary = "Đổi trạng thái tài khoản",
            description = "SYSTEM_ADMIN: đặt ACTIVE hoặc INACTIVE. Kích hoạt thủ công không thay thế OTP; thay đổi sẽ thu hồi token cũ.")
    @PatchMapping("/api/users/{id}/status")
    public UserView changeStatus(
            @PathVariable int id,
            @Valid @RequestBody Status request,
            @AuthenticationPrincipal Jwt jwt) {
        return accounts.changeStatus(id, request.status(), auth.current(jwt).id());
    }

    @Operation(
            summary = "Đổi role và Store",
            description = "SYSTEM_ADMIN: gán role/store theo phạm vi hợp lệ. Thay đổi sẽ thu hồi token cũ.")
    @PutMapping("/api/users/{id}/role")
    public UserView assignRole(
            @PathVariable int id,
            @Valid @RequestBody RoleAssignment request,
            @AuthenticationPrincipal Jwt jwt) {
        return accounts.assignRole(id, request, auth.current(jwt).id());
    }

    @Operation(summary = "Danh sách role", description = "SYSTEM_ADMIN: trả các role có sẵn để dùng khi quản lý tài khoản.")
    @GetMapping("/api/roles")
    public List<Map<String, Object>> listRoles() {
        return userRepository.listRoles();
    }
}
