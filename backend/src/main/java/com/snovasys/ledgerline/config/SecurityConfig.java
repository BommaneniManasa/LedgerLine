package com.snovasys.ledgerline.config;

import com.snovasys.ledgerline.ratelimit.ClientIpResolver;
import com.snovasys.ledgerline.ratelimit.RateLimitFilter;
import com.snovasys.ledgerline.ratelimit.RateLimitService;
import com.snovasys.ledgerline.security.DbJwtAuthenticationConverter;
import com.snovasys.ledgerline.security.JwtService;
import com.snovasys.ledgerline.security.RestAccessDeniedHandler;
import com.snovasys.ledgerline.security.RestAuthenticationEntryPoint;
import java.util.List;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    /** Plugs our JwtService into Spring's bearer-token filter. */
    @Bean
    public JwtDecoder jwtDecoder(JwtService jwtService) {
        return jwtService::decode;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   DbJwtAuthenticationConverter converter,
                                                   RestAuthenticationEntryPoint entryPoint,
                                                   RestAccessDeniedHandler accessDeniedHandler,
                                                   @Qualifier("corsConfigurationSource") CorsConfigurationSource corsSource,
                                                   RateLimitService rateLimitService,
                                                   ClientIpResolver clientIpResolver) throws Exception {
        http
            // CSRF protection is for cookie-based sessions. This API is stateless and reads the token
            // from the Authorization header (never from a cookie), so a forged cross-site request cannot carry it.
            .csrf(AbstractHttpConfigurer::disable)
            .cors(cors -> cors.configurationSource(corsSource))
            .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .httpBasic(AbstractHttpConfigurer::disable)
            .formLogin(AbstractHttpConfigurer::disable)
            .logout(AbstractHttpConfigurer::disable)
            .headers(headers -> headers
                .contentSecurityPolicy(csp -> csp.policyDirectives("default-src 'none'; frame-ancestors 'none'"))
                .referrerPolicy(ref -> ref.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.NO_REFERRER)))
            // Rate limit for POST /api/auth/login and /register. Runs after the CORS filter and before
            // authentication. Created here (not a @Bean) so it is registered exactly once, in this chain only.
            .addFilterBefore(new RateLimitFilter(rateLimitService, clientIpResolver),
                    BearerTokenAuthenticationFilter.class)
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/health", "/api/auth/register", "/api/auth/login").permitAll()
                .requestMatchers("/api/auth/logout", "/api/users/me", "/api/users/me/**").authenticated()
                .requestMatchers("/api/admin/**").hasRole("ADMIN")
                .requestMatchers("/api/auditor/**").hasRole("AUDITOR")
                .requestMatchers("/api/audit-events", "/api/audit-events/**").hasAnyRole("ADMIN", "AUDITOR")
                .requestMatchers("/api/accounts", "/api/accounts/**",
                                 "/api/transactions", "/api/transactions/**").hasRole("CUSTOMER")
                // Deny by default: anything not listed above is forbidden.
                .anyRequest().denyAll())
            .oauth2ResourceServer(oauth -> oauth
                .jwt(jwt -> jwt.jwtAuthenticationConverter(converter))
                .authenticationEntryPoint(entryPoint)
                .accessDeniedHandler(accessDeniedHandler))
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint(entryPoint)
                .accessDeniedHandler(accessDeniedHandler));
        return http.build();
    }

    /** Only the configured frontend origins may call the API from a browser. Never "*". */
    @Bean
    public CorsConfigurationSource corsConfigurationSource(AppProperties properties) {
        List<String> origins = properties.cors().allowedOrigins();
        if (origins == null || origins.isEmpty() || origins.stream().anyMatch(o -> o.contains("*"))) {
            throw new IllegalStateException(
                    "LEDGERLINE_CORS_ALLOWED_ORIGINS must list explicit origins (wildcards are not allowed)");
        }
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(origins);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "OPTIONS"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type", "Idempotency-Key"));
        config.setExposedHeaders(List.of("Idempotent-Replayed"));
        config.setAllowCredentials(false);
        config.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        return source;
    }
}
