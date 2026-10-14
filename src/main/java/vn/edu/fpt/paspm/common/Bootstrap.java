package vn.edu.fpt.paspm.common;

import java.time.Instant;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import vn.edu.fpt.paspm.auth.AuthService;
import vn.edu.fpt.paspm.user.User;
import vn.edu.fpt.paspm.user.UserRepository;

@Component
public class Bootstrap implements ApplicationRunner {
    private final UserRepository userRepository;
    private final AuthService auth;
    private final String email;
    private final String password;

    public Bootstrap(
            UserRepository userRepository,
            AuthService auth,
            @Value("${paspm.bootstrap-email}") String email,
            @Value("${paspm.bootstrap-password}") String password) {
        this.userRepository = userRepository;
        this.auth = auth;
        this.email = email;
        this.password = password;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (email.isBlank() && password.isBlank()) {
            return;
        }
        if (email.isBlank()
                || password.length() < 12
                || !password.matches("(?s)(?=.*[a-z])(?=.*[A-Z])(?=.*[0-9]).+")) {
            throw new IllegalStateException("Invalid bootstrap account configuration");
        }

        if (userRepository.findByEmail(email).isPresent()) {
            return;
        }

        User user = userRepository.createPendingUser(
                "Bootstrap Administrator",
                email,
                "SYSTEM_ADMIN",
                null,
                auth.encodePassword(password),
                null
        );
        userRepository.activateBootstrapAdmin(user.id(), Instant.now());
    }
}
