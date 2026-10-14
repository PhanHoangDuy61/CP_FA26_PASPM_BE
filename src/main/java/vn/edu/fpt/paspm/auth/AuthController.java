package vn.edu.fpt.paspm.auth;

import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import vn.edu.fpt.paspm.common.Contracts.Activate;
import vn.edu.fpt.paspm.common.Contracts.Google;
import vn.edu.fpt.paspm.common.Contracts.Login;
import vn.edu.fpt.paspm.common.Contracts.OtpRequest;
import vn.edu.fpt.paspm.common.Contracts.Registration;
import vn.edu.fpt.paspm.common.Contracts.Token;
import vn.edu.fpt.paspm.common.Contracts.UserView;
import vn.edu.fpt.paspm.common.Contracts.Verify;
import vn.edu.fpt.paspm.user.AccountService;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService auth;
    private final AccountService accounts;
    private final OtpService otp;
    private final GoogleService google;

    public AuthController(AuthService auth, AccountService accounts, OtpService otp, GoogleService google) {
        this.auth = auth;
        this.accounts = accounts;
        this.otp = otp;
        this.google = google;
    }

    @Operation(security = {})
    @PostMapping("/login")
    public Token login(@Valid @RequestBody Login request, HttpServletRequest servletRequest) {
        return auth.login(request, servletRequest.getRemoteAddr());
    }

    @GetMapping("/me")
    public UserView me(@AuthenticationPrincipal Jwt jwt) {
        return auth.current(jwt).view();
    }

    @Operation(security = {})
    @PostMapping("/register")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public Map<String, String> register(@Valid @RequestBody Registration request, HttpServletRequest servletRequest) {
        try {
            accounts.register(request, servletRequest.getRemoteAddr());
        } catch (org.springframework.dao.DuplicateKeyException ignored) {
            // Concurrent duplicate registration: preserve generic response.
        }
        return accepted();
    }

    @Operation(security = {})
    @PostMapping({"/otp/request", "/otp/resend"})
    @ResponseStatus(HttpStatus.ACCEPTED)
    public Map<String, String> request(@Valid @RequestBody OtpRequest request, HttpServletRequest servletRequest) {
        otp.request(request.email(), request.purpose(), servletRequest.getRemoteAddr());
        return accepted();
    }

    @Operation(security = {})
    @PostMapping("/otp/verify")
    public Map<String, String> verify(@Valid @RequestBody Verify request, HttpServletRequest servletRequest) {
        otp.verify(request.email(), request.otp(), "SELF_REGISTER", null, servletRequest.getRemoteAddr());
        return Map.of("message", "Account verified");
    }

    @Operation(security = {})
    @PostMapping("/activate")
    public Map<String, String> activate(@Valid @RequestBody Activate request, HttpServletRequest servletRequest) {
        otp.verify(request.email(), request.otp(), "ADMIN_INVITE", request.password(), servletRequest.getRemoteAddr());
        return Map.of("message", "Account activated");
    }

    @Operation(security = {})
    @PostMapping("/google")
    public Token google(@Valid @RequestBody Google request, HttpServletRequest servletRequest) {
        return google.login(request.idToken(), servletRequest.getRemoteAddr());
    }

    @PostMapping("/google/link")
    public Map<String, String> link(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody Google request) {
        google.link(auth.current(jwt), request.idToken());
        return Map.of("message", "Identity linked");
    }

    private Map<String, String> accepted() {
        return Map.of("message", "If the account is eligible, a verification email will be sent");
    }
}
