package com.snovasys.ledgerline.ratelimit;

import jakarta.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Finds the client address used for rate limiting, without trusting a header a client can forge.
 *
 * - trusted-proxy-hops = 0 (default): X-Forwarded-For is ignored; the TCP peer address is used.
 * - trusted-proxy-hops = N: exactly N proxies we control append the address of whoever connected to them,
 *   so the real client is the N-th entry counted FROM THE RIGHT. Anything further left was written by the
 *   client and is ignored. If the header is shorter than expected or the entry is not an IP literal,
 *   we fall back to the TCP peer address.
 */
@Component
public class ClientIpResolver {

    private static final String FORWARDED_FOR = "X-Forwarded-For";

    private final int trustedHops;

    @Autowired
    public ClientIpResolver(RateLimitProperties properties) {
        this.trustedHops = properties.getTrustedProxyHops();
    }

    public ClientIpResolver(int trustedHops) {
        this.trustedHops = trustedHops;
    }

    public String resolve(HttpServletRequest request) {
        String peer = request.getRemoteAddr();
        if (trustedHops <= 0) {
            return peer;
        }
        List<String> entries = new ArrayList<>();
        for (String header : Collections.list(request.getHeaders(FORWARDED_FOR))) {
            for (String part : header.split(",")) {
                String trimmed = part.trim();
                if (!trimmed.isEmpty()) {
                    entries.add(trimmed);
                }
            }
        }
        int index = entries.size() - trustedHops;
        if (index < 0) {
            return peer;
        }
        String candidate = entries.get(index);
        return looksLikeIp(candidate) ? candidate.toLowerCase(Locale.ROOT) : peer;
    }

    /** No DNS lookups: just the characters an IPv4/IPv6 literal can contain. */
    static boolean looksLikeIp(String value) {
        if (value.length() > 45 || !value.matches("[0-9a-fA-F:.]+")) {
            return false;
        }
        return value.indexOf('.') >= 0 || value.indexOf(':') >= 0;
    }
}
