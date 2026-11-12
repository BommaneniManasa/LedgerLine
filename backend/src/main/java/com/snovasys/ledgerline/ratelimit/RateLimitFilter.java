package com.snovasys.ledgerline.ratelimit;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Per-IP rate limit for POST /api/auth/login and POST /api/auth/register.
 *
 * - Registered ONLY inside the Spring Security filter chain (SecurityConfig) and deliberately NOT a Spring
 *   bean, so Spring Boot cannot also register it as a servlet filter. OncePerRequestFilter adds a second guard.
 * - Only POST on those two paths is counted. OPTIONS (CORS preflight), GET and every other path pass untouched.
 * - Runs after the CORS filter, so 429/503 answers still carry CORS headers and the browser can read them.
 * - The per-e-mail counter for login is applied in AuthController, after the body has been parsed and validated.
 */
public class RateLimitFilter extends OncePerRequestFilter {

    static final String LOGIN_PATH = "/api/auth/login";
    static final String REGISTER_PATH = "/api/auth/register";

    private final RateLimitService rateLimitService;
    private final ClientIpResolver clientIpResolver;

    public RateLimitFilter(RateLimitService rateLimitService, ClientIpResolver clientIpResolver) {
        this.rateLimitService = rateLimitService;
        this.clientIpResolver = clientIpResolver;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !rateLimitService.isEnabled()
                || !"POST".equalsIgnoreCase(request.getMethod())
                || target(request) == null;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String path = target(request);
        try {
            String ip = clientIpResolver.resolve(request);
            if (LOGIN_PATH.equals(path)) {
                rateLimitService.checkLoginIp(ip);
            } else {
                rateLimitService.checkRegisterIp(ip);
            }
        } catch (RateLimitException e) {
            write(response, e, path);
            return;
        }
        chain.doFilter(request, response);
    }

    /** Uses the container-decoded path (servletPath + pathInfo), so %-encoded variants cannot dodge the check. */
    private static String target(HttpServletRequest request) {
        String path = request.getServletPath() == null ? "" : request.getServletPath();
        if (request.getPathInfo() != null) {
            path += request.getPathInfo();
        }
        while (path.length() > 1 && path.endsWith("/")) {
            path = path.substring(0, path.length() - 1);
        }
        if (LOGIN_PATH.equalsIgnoreCase(path)) {
            return LOGIN_PATH;
        }
        if (REGISTER_PATH.equalsIgnoreCase(path)) {
            return REGISTER_PATH;
        }
        return null;
    }

    /** Same JSON shape as every other API error (ApiError), plus Retry-After. */
    private static void write(HttpServletResponse response, RateLimitException e, String path) throws IOException {
        response.setStatus(e.getStatus().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setHeader("Retry-After", Long.toString(e.getRetryAfterSeconds()));
        response.setHeader("Cache-Control", "no-store");
        String body = "{\"timestamp\":\"" + Instant.now() + "\",\"status\":" + e.getStatus().value()
                + ",\"code\":\"" + e.getCode() + "\",\"message\":\"" + e.getMessage()
                + "\",\"path\":\"" + path + "\"}";
        response.getWriter().write(body);
    }
}
