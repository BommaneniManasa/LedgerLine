package com.snovasys.ledgerline.security;

import com.snovasys.ledgerline.domain.Role;
import java.time.Instant;
import java.util.UUID;

/**
 * The authenticated caller. It is built from the DATABASE row on every request,
 * never from anything the client sent (role and id are not taken from the token body or the request).
 */
public record AuthUser(UUID id, String email, String fullName, Role role, String tokenId, Instant tokenExpiresAt) {
}
