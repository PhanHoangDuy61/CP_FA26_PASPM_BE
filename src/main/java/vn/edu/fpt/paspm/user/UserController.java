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

    @GetMapping("/api/users")
    public List<UserView> listUsers(
            @RequestParam(defaultValue = "") String q,
            @RequestParam(defaultValue = "") String role,
            @RequestParam(defaultValue = "") String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return userRepository.searchUsers(q, role, status, page, size);
    }

    @GetMapping("/api/users/{id}")
    public UserView getUser(@PathVariable int id) {
        return userRepository.findById(id).view();
    }

    @PostMapping("/api/users")
    @ResponseStatus(HttpStatus.CREATED)
    public UserView createUser(@Valid @RequestBody CreateUser request) {
        return accounts.inviteUser(request);
    }

    @PutMapping("/api/users/{id}")
    public UserView updateUser(@PathVariable int id, @Valid @RequestBody UpdateUser request) {
        return accounts.updateUser(id, request);
    }

    @PatchMapping("/api/users/{id}/status")
    public UserView changeStatus(
            @PathVariable int id,
            @Valid @RequestBody Status request,
            @AuthenticationPrincipal Jwt jwt) {
        return accounts.changeStatus(id, request.status(), auth.current(jwt).id());
    }

    @PutMapping("/api/users/{id}/role")
    public UserView assignRole(
            @PathVariable int id,
            @Valid @RequestBody RoleAssignment request,
            @AuthenticationPrincipal Jwt jwt) {
        return accounts.assignRole(id, request, auth.current(jwt).id());
    }

    @GetMapping("/api/roles")
    public List<Map<String, Object>> listRoles() {
        return userRepository.listRoles();
    }
}
