package vn.edu.fpt.paspm.auth;

import java.util.Map;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.Cookie;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.mail.MailException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import vn.edu.fpt.paspm.common.Contracts.Activate;
import vn.edu.fpt.paspm.common.Contracts.ChangePassword;
import vn.edu.fpt.paspm.common.Contracts.EmailRequest;
import vn.edu.fpt.paspm.common.Contracts.Google;
import vn.edu.fpt.paspm.common.Contracts.Login;
import vn.edu.fpt.paspm.common.Contracts.OtpRequest;
import vn.edu.fpt.paspm.common.Contracts.PasswordReset;
import vn.edu.fpt.paspm.common.Contracts.Registration;
import vn.edu.fpt.paspm.common.Contracts.Token;
import vn.edu.fpt.paspm.common.Contracts.UserView;
import vn.edu.fpt.paspm.common.Contracts.Verify;
import vn.edu.fpt.paspm.user.AccountService;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private static final String COOKIE_REFRESH_SECURE = "__Host-PASPM-REFRESH";
    private static final String COOKIE_REFRESH_LOCAL = "PASPM-REFRESH";
    private static final String COOKIE_CSRF_SECURE = "__Host-PASPM-CSRF";
    private static final String COOKIE_CSRF_LOCAL = "PASPM-CSRF";
    private static final String CSRF_HEADER = "X-CSRF-Token";
    private static final Logger logger = LoggerFactory.getLogger(AuthController.class);

    private final AuthService auth;
    private final AccountService accounts;
    private final OtpService otp;
    private final GoogleService google;
    private final boolean refreshCookieSecure;
    private final SessionOriginGuard origins;

    public AuthController(
            AuthService auth,
            AccountService accounts,
            OtpService otp,
            GoogleService google,
            @Value("${paspm.refresh-cookie-secure}") boolean refreshCookieSecure, SessionOriginGuard origins) {
        this.auth = auth;
        this.accounts = accounts;
        this.otp = otp;
        this.google = google;
        this.refreshCookieSecure = refreshCookieSecure;
        this.origins = origins;
    }

    @Operation(
            summary = "Đăng nhập bằng email",
            description = "Chỉ tài khoản đã xác minh email và đang ACTIVE mới nhận PASPM access token.",
            security = {})
    @io.swagger.v3.oas.annotations.security.SecurityRequirements
    @PostMapping("/login")
    public ResponseEntity<Token> login(@Valid @RequestBody Login request, HttpServletRequest servletRequest) {
        origins.validate(servletRequest);
        return tokenResponse(auth.login(request, servletRequest.getRemoteAddr()));
    }

    @Operation(
            summary = "Làm mới credentials",
            description = "Xoay vòng opaque refresh token một lần; expiresIn là thời hạn access token mới tính bằng giây.",
            security = {})
    @io.swagger.v3.oas.annotations.security.SecurityRequirements
    @PostMapping("/refresh")
    public ResponseEntity<Token> refresh(
            HttpServletRequest servletRequest, HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store");
        response.setHeader("Pragma", "no-cache");
        origins.validate(servletRequest);
        requireCsrf(servletRequest);
        String refreshToken = cookieValue(servletRequest, refreshCookieName());
        try {
            if (refreshToken == null || refreshToken.isBlank()) {
                throw new vn.edu.fpt.paspm.common.ApiException(401, "Invalid or expired refresh token");
            }
            return tokenResponse(auth.refresh(refreshToken, servletRequest.getHeader(CSRF_HEADER), servletRequest.getRemoteAddr()));
        } catch (vn.edu.fpt.paspm.common.ApiException exception) {
            if (exception.status() == 401) {
                clearSessionCookies(Map.of()).getHeaders().getOrEmpty(HttpHeaders.SET_COOKIE)
                        .forEach(cookie -> response.addHeader(HttpHeaders.SET_COOKIE, cookie));
            }
            throw exception;
        }
    }

    @Operation(summary = "Đăng xuất thiết bị hiện tại", description = "Thu hồi refresh session gắn với access token hiện tại.")
    @PostMapping("/logout")
    public ResponseEntity<Map<String, String>> logout(
            @AuthenticationPrincipal Jwt jwt,
            HttpServletRequest servletRequest) {
        origins.validate(servletRequest);
        requireCsrf(servletRequest);
        auth.logout(jwt, servletRequest.getHeader(CSRF_HEADER));
        return clearSessionCookies(Map.of("message", "Logged out"));
    }

    @Operation(
            summary = "Đăng xuất mọi thiết bị",
            description = "Thu hồi mọi refresh session và tăng token_version để vô hiệu hóa toàn bộ access token.")
    @PostMapping("/logout-all")
    public ResponseEntity<Map<String, String>> logoutAll(
            @AuthenticationPrincipal Jwt jwt,
            HttpServletRequest servletRequest) {
        origins.validate(servletRequest);
        requireCsrf(servletRequest);
        auth.logoutAll(jwt, servletRequest.getHeader(CSRF_HEADER));
        return clearSessionCookies(Map.of("message", "Logged out from all sessions"));
    }

    @Operation(
            summary = "Xem tài khoản hiện tại",
            description = "Trả thông tin tài khoản gắn với PASPM Bearer access token hiện tại; cần xác thực.")
    @GetMapping("/me")
    public UserView me(@AuthenticationPrincipal Jwt jwt) {
        return auth.current(jwt).view();
    }

    @Operation(
            summary = "Yêu cầu OTP đăng ký",
            description = "Chỉ nhập email để tạo tài khoản END_CUSTOMER đang chờ xác minh và gửi OTP qua SMTP. Trả 202 nếu email mới, 409 nếu email đã tồn tại; điều này tiết lộ trạng thái đăng ký của email.",
            security = {})
    @PostMapping("/register")
    public ResponseEntity<Map<String, String>> register(
            @Valid @RequestBody EmailRequest request,
            HttpServletRequest servletRequest) {
        try {
            boolean created = accounts.requestRegistration(request.email(), servletRequest.getRemoteAddr());
            if (!created) {
                return ResponseEntity.status(HttpStatus.CONFLICT)
                        .body(Map.of("error", "Email already exists"));
            }
        } catch (org.springframework.dao.DuplicateKeyException ignored) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("error", "Email already exists"));
        }
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(Map.of("message", "Verification email sent"));
    }

    @Operation(
            summary = "Hoàn tất đăng ký email",
            description = "Chỉ gọi sau POST /api/auth/otp/verify thành công. Gửi cùng email, tên và mật khẩu; mật khẩu cần ít nhất 12 ký tự, có chữ hoa, chữ thường và số.",
            security = {})
    @PostMapping("/register/complete")
    public Map<String, String> completeRegistration(@Valid @RequestBody Registration request) {
        accounts.completeRegistration(request);
        return Map.of("message", "Registration complete");
    }

    @Operation(
            summary = "Đổi mật khẩu đang đăng nhập",
            description = "Chỉ đổi mật khẩu tài khoản trong JWT sau khi xác minh mật khẩu hiện tại. JWT cũ bị thu hồi; cần đăng nhập lại.")
    @PostMapping("/password/change")
    public Map<String, String> changePassword(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody ChangePassword request) {
        auth.changePassword(jwt, request);
        return Map.of("message", "Password changed");
    }

    @Operation(
            summary = "Yêu cầu OTP",
            description = "Gửi OTP xác minh cho tài khoản đang chờ xác minh email. Dùng purpose SELF_REGISTER hoặc ADMIN_INVITE. Đăng ký tài khoản mới bắt đầu bằng POST /api/auth/register, endpoint này chỉ dùng khi cần yêu cầu OTP cho tài khoản đang chờ.",
            security = {})
    @PostMapping("/otp/request")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public Map<String, String> requestOtp(
            @Valid @RequestBody OtpRequest request,
            HttpServletRequest servletRequest) {
        return issueOtp(request, servletRequest);
    }

    @Operation(
            summary = "Gửi lại OTP",
            description = "Gửi mã OTP mới cho tài khoản đang chờ xác minh khi mã trước đó thất lạc hoặc hết hạn. Dùng cùng email và purpose SELF_REGISTER hoặc ADMIN_INVITE; mã cũ bị vô hiệu và phải qua thời gian chờ cooldown trước khi gửi lại.",
            security = {})
    @PostMapping("/otp/resend")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public Map<String, String> resendOtp(
            @Valid @RequestBody OtpRequest request,
            HttpServletRequest servletRequest) {
        return issueOtp(request, servletRequest);
    }

    @Operation(
            summary = "Yêu cầu OTP đặt lại mật khẩu",
            description = "Luôn trả cùng thông báo cho mọi email hợp lệ, không tiết lộ tài khoản có tồn tại hoặc đủ điều kiện hay không.",
            security = {})
    @PostMapping("/password-reset/request")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public Map<String, String> requestPasswordReset(
            @Valid @RequestBody EmailRequest request,
            HttpServletRequest servletRequest) {
        try {
            otp.request(request.email(), "PASSWORD_RESET", servletRequest.getRemoteAddr());
        } catch (MailException exception) {
            logger.error("Password reset email delivery failed ({})", exception.getClass().getSimpleName());
        }
        return accepted();
    }

    @Operation(
            summary = "Xác minh OTP và đặt mật khẩu mới",
            description = "OTP PASSWORD_RESET còn hạn và chưa sử dụng; mật khẩu mới theo cùng chính sách với đăng ký.",
            security = {})
    @PostMapping("/password-reset/confirm")
    public Map<String, String> confirmPasswordReset(
            @Valid @RequestBody PasswordReset request,
            HttpServletRequest servletRequest) {
        otp.verify(request.email(), request.otp(), "PASSWORD_RESET", request.password(), servletRequest.getRemoteAddr());
        return Map.of("message", "Password reset complete");
    }

    private Map<String, String> issueOtp(OtpRequest request, HttpServletRequest servletRequest) {
        otp.request(request.email(), request.purpose(), servletRequest.getRemoteAddr());
        return accepted();
    }

    @Operation(
            summary = "Xác minh OTP",
            description = "Xác minh OTP 6 chữ số. Đăng ký email tiếp tục bằng POST /api/auth/register/complete; tài khoản được mời tiếp tục bằng POST /api/auth/activate.",
            security = {})
    @PostMapping("/otp/verify")
    public Map<String, String> verify(@Valid @RequestBody Verify request, HttpServletRequest servletRequest) {
        otp.verify(request.email(), request.otp(), "SELF_REGISTER", null, servletRequest.getRemoteAddr());
        return Map.of("message", "Account verified");
    }

    @Operation(
            summary = "Kích hoạt tài khoản được mời",
            description = "Dành cho nhân viên được quản trị viên mời: nhập email, OTP và mật khẩu mới để kích hoạt.",
            security = {})
    @PostMapping("/activate")
    public Map<String, String> activate(@Valid @RequestBody Activate request, HttpServletRequest servletRequest) {
        otp.verify(request.email(), request.otp(), "ADMIN_INVITE", request.password(), servletRequest.getRemoteAddr());
        return Map.of("message", "Account activated");
    }

    @Operation(
            summary = "Đăng ký bằng Google",
            description = "Nhận Google ID token hợp lệ, tạo END_CUSTOMER chờ xác minh và gửi OTP đến email Google. Sau khi xác minh OTP, gọi POST /api/auth/google.",
            security = {})
    @PostMapping("/google/register")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public Map<String, String> registerWithGoogle(
            @Valid @RequestBody Google request,
            HttpServletRequest servletRequest) {
        try {
            google.register(request.idToken(), servletRequest.getRemoteAddr());
        } catch (org.springframework.dao.DuplicateKeyException ignored) {
            // Concurrent registration: preserve the generic response.
        }
        return accepted();
    }

    @Operation(
            summary = "Đăng nhập bằng Google",
            description = "Đổi Google ID token của identity đã liên kết lấy PASPM access token. Tài khoản phải đang ACTIVE.",
            security = {})
    @io.swagger.v3.oas.annotations.security.SecurityRequirements
    @PostMapping("/google")
    public ResponseEntity<Token> google(@Valid @RequestBody Google request, HttpServletRequest servletRequest) {
        origins.validate(servletRequest);
        return tokenResponse(google.login(request.idToken(), servletRequest.getRemoteAddr()));
    }

    @Operation(
            summary = "Liên kết tài khoản Google",
            description = "Liên kết Google ID token với tài khoản PASPM đang đăng nhập. Không tự liên kết chỉ vì email Google trùng email PASPM.")
    @PostMapping("/google/link")
    public Map<String, String> link(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody Google request) {
        google.link(auth.current(jwt), request.idToken());
        return Map.of("message", "Identity linked");
    }

    private Map<String, String> accepted() {
        return Map.of("message", "If the account is eligible, a verification email will be sent");
    }

    private ResponseEntity<Token> tokenResponse(AuthCredentials credentials) {
        long maxAge = Math.max(0, Duration.between(Instant.now(), credentials.refreshExpiresAt()).getSeconds());
        ResponseCookie refreshCookie = ResponseCookie.from(refreshCookieName(), credentials.refreshToken())
                .httpOnly(true)
                .secure(refreshCookieSecure)
                .sameSite("Strict")
                .path("/")
                .maxAge(maxAge)
                .build();
        ResponseCookie csrfCookie = ResponseCookie.from(csrfCookieName(), credentials.token().csrfToken())
                .httpOnly(false)
                .secure(refreshCookieSecure)
                .sameSite("Strict")
                .path("/")
                .maxAge(maxAge)
                .build();
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.SET_COOKIE, refreshCookie.toString(), csrfCookie.toString())
                .body(credentials.token());
    }

    private ResponseEntity<Map<String, String>> clearSessionCookies(Map<String, String> body) {
        ResponseCookie expiredRefresh = ResponseCookie.from(refreshCookieName(), "")
                .httpOnly(true)
                .secure(refreshCookieSecure)
                .sameSite("Strict")
                .path("/")
                .maxAge(0)
                .build();
        ResponseCookie expiredCsrf = ResponseCookie.from(csrfCookieName(), "")
                .httpOnly(false)
                .secure(refreshCookieSecure)
                .sameSite("Strict")
                .path("/")
                .maxAge(0)
                .build();
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.SET_COOKIE, expiredRefresh.toString(), expiredCsrf.toString())
                .body(body);
    }

    private void requireCsrf(HttpServletRequest request) {
        String cookie = cookieValue(request, csrfCookieName());
        String header = request.getHeader(CSRF_HEADER);
        if (cookie == null || header == null
                || !MessageDigest.isEqual(
                        cookie.getBytes(StandardCharsets.UTF_8),
                        header.getBytes(StandardCharsets.UTF_8))) {
            throw new vn.edu.fpt.paspm.common.ApiException(403, "CSRF validation failed");
        }
    }

    private String cookieValue(HttpServletRequest request, String name) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        String value = null;
        for (Cookie cookie : cookies) {
            if (name.equals(cookie.getName())) {
                if (value != null) throw new vn.edu.fpt.paspm.common.ApiException(403, "Ambiguous session cookie");
                value = cookie.getValue();
            }
        }
        return value;
    }

    private String refreshCookieName() {
        return refreshCookieSecure ? COOKIE_REFRESH_SECURE : COOKIE_REFRESH_LOCAL;
    }

    private String csrfCookieName() {
        return refreshCookieSecure ? COOKIE_CSRF_SECURE : COOKIE_CSRF_LOCAL;
    }
}
