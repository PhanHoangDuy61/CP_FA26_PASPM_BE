package vn.edu.fpt.paspm.security;

import org.springframework.security.authentication.AccountStatusException;

/** Raised by the JWT converter for a valid token whose user belongs to an INACTIVE Store; answered with 403 STORE_INACTIVE. */
public class StoreInactiveAuthenticationException extends AccountStatusException {
    public StoreInactiveAuthenticationException() {
        super(StoreStatusGuard.MESSAGE);
    }
}
