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
import vn.edu.fpt.paspm.common.Contracts.EmailRequest;
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

    @Operation(
            summary = "Đăng nhập bằng email",
            description = "Chỉ tài khoản đã xác minh email và đang ACTIVE mới nhận PASPM access token.",
            security = {})
    @PostMapping("/login")
    public Token login(@Valid @RequestBody Login request, HttpServletRequest servletRequest) {
        return auth.login(request, servletRequest.getRemoteAddr());
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
            description = "Chỉ nhập email để tạo tài khoản END_CUSTOMER đang chờ xác minh và gửi OTP qua SMTP. Phản hồi chung không tiết lộ email đã tồn tại.",
            security = {})
    @PostMapping("/register")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public Map<String, String> register(@Valid @RequestBody EmailRequest request, HttpServletRequest servletRequest) {
        try {
            accounts.requestRegistration(request.email(), servletRequest.getRemoteAddr());
        } catch (org.springframework.dao.DuplicateKeyException ignored) {
            // Concurrent duplicate registration: preserve generic response.
        }
        return accepted();
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
            summary = "Yêu cầu hoặc gửi lại OTP",
            description = "Gửi OTP cho tài khoản đang chờ xác minh. purpose là SELF_REGISTER hoặc ADMIN_INVITE. Đăng ký mới chỉ cần gọi POST /api/auth/register.",
            security = {})
    @PostMapping({"/otp/request", "/otp/resend"})
    @ResponseStatus(HttpStatus.ACCEPTED)
    public Map<String, String> request(@Valid @RequestBody OtpRequest request, HttpServletRequest servletRequest) {
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
    @PostMapping("/google")
    public Token google(@Valid @RequestBody Google request, HttpServletRequest servletRequest) {
        return google.login(request.idToken(), servletRequest.getRemoteAddr());
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
}
