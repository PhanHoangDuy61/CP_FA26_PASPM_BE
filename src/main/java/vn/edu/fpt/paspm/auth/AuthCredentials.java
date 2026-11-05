package vn.edu.fpt.paspm.auth;

import java.time.Instant;

import vn.edu.fpt.paspm.common.Contracts.Token;

public record AuthCredentials(Token token, String refreshToken, Instant refreshExpiresAt) {
}
