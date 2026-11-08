package com.snovasys.ledgerline.security;

import java.util.List;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

/** Spring Security authentication object whose principal is our {@link AuthUser}. */
public class AuthUserToken extends AbstractAuthenticationToken {

    private final AuthUser principal;
    private final Jwt jwt;

    public AuthUserToken(AuthUser principal, Jwt jwt) {
        super(List.of(new SimpleGrantedAuthority("ROLE_" + principal.role().name())));
        this.principal = principal;
        this.jwt = jwt;
        setAuthenticated(true);
    }

    @Override
    public Object getCredentials() {
        return jwt;
    }

    @Override
    public Object getPrincipal() {
        return principal;
    }

    @Override
    public String getName() {
        return principal.id().toString();
    }
}
