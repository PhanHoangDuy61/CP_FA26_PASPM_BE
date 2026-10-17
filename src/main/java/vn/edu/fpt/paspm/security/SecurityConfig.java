package vn.edu.fpt.paspm.security;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import javax.crypto.spec.SecretKeySpec;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;

import vn.edu.fpt.paspm.user.User;
import vn.edu.fpt.paspm.user.UserRepository;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {
    @Bean
    public PasswordEncoder passwords() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    public SecretKeySpec key(@Value("${paspm.jwt-secret}") String secret) {
        byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < 32) {
            throw new IllegalStateException("JWT_SECRET must contain at least 32 bytes");
        }
        return new SecretKeySpec(bytes, "HmacSHA256");
    }

    @Bean
    public JwtEncoder encoder(SecretKeySpec key) {
        return new NimbusJwtEncoder(new ImmutableSecret<>(key));
    }

    @Bean
    public JwtDecoder decoder(SecretKeySpec key) {
        var decoder = NimbusJwtDecoder.withSecretKey(key)
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
        decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer("paspm"));
        return decoder;
    }

    @Bean
    public SecurityFilterChain chain(HttpSecurity http, UserRepository userRepository) throws Exception {
        var converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(jwt -> {
            try {
                User user = userRepository.findById(Integer.parseInt(jwt.getSubject()));
                Number version = jwt.getClaim("ver");
                if (!user.active() || version == null || version.intValue() != user.version()) {
                    throw new IllegalArgumentException();
                }
                return List.of(new SimpleGrantedAuthority("ROLE_" + user.role()));
            } catch (Exception e) {
                throw new OAuth2AuthenticationException("invalid_token");
            }
        });

        return http
                .csrf(c -> c.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/swagger", "/swagger-ui/**", "/v3/api-docs/**").permitAll()
                        .requestMatchers(
                                "/api/auth/login",
                                "/api/auth/register",
                                "/api/auth/register/complete",
                                "/api/auth/otp/**",
                                "/api/auth/activate",
                                "/api/auth/google/register",
                                "/api/auth/google"
                        ).permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(o -> o.jwt(j -> j.jwtAuthenticationConverter(converter)))
                .build();
    }

    @Bean
    public OpenAPI openapi() {
        return new OpenAPI()
                .info(new io.swagger.v3.oas.models.info.Info().title("PASPM Backend API").version("0.1.0"))
                .components(new Components()
                        .addSecuritySchemes("bearerAuth",
                                new SecurityScheme()
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList("bearerAuth"));
    }

    @Bean
    public org.springdoc.core.customizers.OpenApiCustomizer errors() {
        return api -> api.getPaths().values().forEach(path ->
                path.readOperations().forEach(operation -> {
                    for (var entry : Map.of(
                            "400", "Invalid request / verification",
                            "401", "Authentication required / invalid",
                            "403", "Permission denied",
                            "404", "Resource not found or outside the authenticated Store",
                            "409", "Conflict",
                            "429", "Rate limit exceeded",
                            "503", "Service configuration or required dependency unavailable"
                    ).entrySet()) {
                        operation.getResponses().addApiResponse(
                                entry.getKey(),
                                new io.swagger.v3.oas.models.responses.ApiResponse().description(entry.getValue())
                        );
                    }
                })
        );
    }
}
