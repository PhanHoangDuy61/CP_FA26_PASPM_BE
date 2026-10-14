package vn.edu.fpt.paspm.user;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import vn.edu.fpt.paspm.auth.AuthService;
import vn.edu.fpt.paspm.auth.OtpService;
import vn.edu.fpt.paspm.common.ApiException;
import vn.edu.fpt.paspm.common.Contracts.CreateUser;
import vn.edu.fpt.paspm.common.Contracts.Registration;
import vn.edu.fpt.paspm.common.Contracts.RoleAssignment;
import vn.edu.fpt.paspm.common.Contracts.UpdateUser;
import vn.edu.fpt.paspm.common.Contracts.UserView;
import vn.edu.fpt.paspm.security.RateLimits;

@Service
public class AccountService {
    private final UserRepository userRepository;
    private final AuthService auth;
    private final OtpService otp;
    private final RateLimits limits;

    public AccountService(UserRepository userRepository, AuthService auth, OtpService otp, RateLimits limits) {
        this.userRepository = userRepository;
        this.auth = auth;
        this.otp = otp;
        this.limits = limits;
    }

    @Transactional
    public UserView inviteUser(CreateUser request) {
        User user = userRepository.createPendingUser(
                request.name(),
                request.email(),
                request.roleCode(),
                request.storeId(),
                null,
                "ADMIN_INVITE"
        );
        otp.issue(user);
        return user.view();
    }

    @Transactional
    public void register(Registration request, String ip) {
        limits.hit("register:" + ip, 10, 3600);
        auth.validatePasswordLength(request.password());
        if (userRepository.findByEmail(request.email()).isPresent()) {
            return;
        }

        User user = userRepository.createPendingUser(
                request.name(),
                request.email(),
                "END_CUSTOMER",
                null,
                auth.encodePassword(request.password()),
                "SELF_REGISTER"
        );
        otp.issue(user);
    }

    @Transactional
    public UserView updateUser(int id, UpdateUser request) {
        userRepository.lockById(id);
        userRepository.updateName(id, request.name().trim());
        return userRepository.findById(id).view();
    }

    @Transactional
    public UserView changeStatus(int id, String status, int actor) {
        User user = userRepository.lockById(id);

        if (actor == id && "INACTIVE".equals(status)) {
            throw new ApiException(400, "Cannot disable your own admin account");
        }
        if ("ACTIVE".equals(status) && (user.verifiedAt() == null || user.passwordHash() == null)) {
            throw new ApiException(400, "Account must complete OTP activation first");
        }

        userRepository.updateStatus(id, status);
        return userRepository.findById(id).view();
    }

    @Transactional
    public UserView assignRole(int id, RoleAssignment request, int actor) {
        userRepository.lockById(id);
        userRepository.validateRoleStoreScope(request.roleCode(), request.storeId());

        if (actor == id && !"SYSTEM_ADMIN".equals(request.roleCode())) {
            throw new ApiException(400, "Cannot remove your own admin access");
        }

        userRepository.updateRole(
                id,
                userRepository.findRoleId(request.roleCode()),
                request.storeId()
        );
        return userRepository.findById(id).view();
    }
}
