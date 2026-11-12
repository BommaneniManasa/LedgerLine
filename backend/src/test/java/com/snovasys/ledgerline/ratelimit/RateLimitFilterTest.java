
package com.snovasys.ledgerline.ratelimit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.snovasys.ledgerline.TestProps;
import jakarta.servlet.FilterChain;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class RateLimitFilterTest {

    private FakeRateLimitStore store;
    private RateLimitProperties props;
    private AtomicInteger chainCalls;
    private FilterChain chain;

    @BeforeEach
    void setUp() {
        store = new FakeRateLimitStore();
        props = new RateLimitProperties();
        props.setEnabled(true);
        props.setLoginIp(new RateLimitProperties.Rule(3, Duration.ofMinutes(5)));
        props.setRegisterIp(new RateLimitProperties.Rule(2, Duration.ofHours(1)));
        chainCalls = new AtomicInteger();
        chain = (req, res) -> chainCalls.incrementAndGet();
    }

    private RateLimitFilter filter(int trustedHops) {
        RateLimitService service = new RateLimitService(props, Optional.of(store), TestProps.create());
        return new RateLimitFilter(service, new ClientIpResolver(trustedHops));
    }

    private static MockHttpServletRequest req(String method, String path) {
        MockHttpServletRequest r = new MockHttpServletRequest(method, path);
        r.setServletPath(path);
        r.setRemoteAddr("203.0.113.7");
        return r;
    }

    private MockHttpServletResponse run(RateLimitFilter f, MockHttpServletRequest r) throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        f.doFilter(r, response, chain);
        return response;
    }

    @Test
    void requestsWithinTheLimitReachTheController() throws Exception {
        RateLimitFilter f = filter(0);
        for (int i = 0; i < 3; i++) {
            assertEquals(200, run(f, req("POST", "/api/auth/login")).getStatus());
        }
        assertEquals(3, chainCalls.get());
    }

    @Test
    void theRequestAboveTheLimitGets429WithRetryAfterAndTheSafeErrorShape() throws Exception {
        RateLimitFilter f = filter(0);
        for (int i = 0; i < 3; i++) {
            run(f, req("POST", "/api/auth/login"));
        }
        MockHttpServletResponse blocked = run(f, req("POST", "/api/auth/login"));
        assertEquals(429, blocked.getStatus());
        String retryAfter = blocked.getHeader("Retry-After");
        assertNotNull(retryAfter);
        long seconds = Long.parseLong(retryAfter);
        assertTrue(seconds >= 1 && seconds <= 300, "Retry-After=" + seconds);
        assertTrue(blocked.getContentType().startsWith("application/json"));
        String body = blocked.getContentAsString();
        assertTrue(body.contains("\"status\":429"), body);
        assertTrue(body.contains("\"code\":\"RATE_LIMITED\""), body);
        assertTrue(body.contains("\"path\":\"/api/auth/login\""), body);
        assertTrue(body.contains("\"timestamp\""), body);
        assertFalse(body.contains("203.0.113.7"), "the client address must not appear in the response");
        assertEquals(3, chainCalls.get(), "the blocked request must not reach the controller");
    }

    @Test
    void registrationIsLimitedSeparatelyFromLogin() throws Exception {
        RateLimitFilter f = filter(0);
        for (int i = 0; i < 4; i++) {
            run(f, req("POST", "/api/auth/login"));
        }
        assertEquals(200, run(f, req("POST", "/api/auth/register")).getStatus());
        assertEquals(200, run(f, req("POST", "/api/auth/register")).getStatus());
        assertEquals(429, run(f, req("POST", "/api/auth/register")).getStatus());
    }

    @Test
    void whenRedisIsDownLoginAndRegisterGet503AndNeverReachTheController() throws Exception {
        store.down = true;
        RateLimitFilter f = filter(0);
        for (String path : new String[] {"/api/auth/login", "/api/auth/register"}) {
            MockHttpServletResponse r = run(f, req("POST", path));
            assertEquals(503, r.getStatus());
            assertNotNull(r.getHeader("Retry-After"));
            assertTrue(r.getContentAsString().contains("\"code\":\"RATE_LIMIT_UNAVAILABLE\""));
        }
        assertEquals(0, chainCalls.get(), "fail closed: nothing may slip through while Redis is down");
    }

    @Test
    void corsPreflightIsNeverCountedOrBlocked() throws Exception {
        RateLimitFilter f = filter(0);
        for (int i = 0; i < 20; i++) {
            MockHttpServletResponse r = run(f, req("OPTIONS", "/api/auth/login"));
            assertEquals(200, r.getStatus());
        }
        assertEquals(20, chainCalls.get());
        assertEquals(0, store.calls);
    }

    @Test
    void onlyPostOnTheTwoAuthPathsIsLimited() throws Exception {
        RateLimitFilter f = filter(0);
        for (int i = 0; i < 10; i++) {
            run(f, req("GET", "/api/auth/login"));
            run(f, req("POST", "/api/auth/logout"));
            run(f, req("POST", "/api/transactions/deposit"));
            run(f, req("GET", "/api/health"));
        }
        assertEquals(40, chainCalls.get());
        assertEquals(0, store.calls);
    }

    @Test
    void trailingSlashAndLetterCaseDoNotDodgeTheLimit() throws Exception {
        RateLimitFilter f = filter(0);
        run(f, req("POST", "/api/auth/login"));
        run(f, req("POST", "/api/auth/login/"));
        run(f, req("POST", "/API/AUTH/LOGIN"));
        assertEquals(429, run(f, req("POST", "/api/auth/login")).getStatus());
    }

    @Test
    void theFilterCountsARequestOnlyOnceDuringNestedInvocation() throws Exception {
        RateLimitFilter f = filter(0);
        MockHttpServletRequest r = req("POST", "/api/auth/login");
        MockHttpServletResponse response = new MockHttpServletResponse();

        FilterChain nestedChain = (request, nestedResponse) ->
                f.doFilter(request, nestedResponse,
                        (ignoredRequest, ignoredResponse) -> { });

        f.doFilter(r, response, nestedChain);

        assertEquals(1, store.calls);
    }

    @Test
    void forgedForwardedForValuesCannotBeUsedToRotateAroundTheLimit() throws Exception {
        RateLimitFilter f = filter(1);
        int blocked = 0;
        for (int i = 0; i < 6; i++) {
            MockHttpServletRequest r = req("POST", "/api/auth/login");
            r.addHeader("X-Forwarded-For", "1.1.1." + i + ", 198.51.100.9");
            if (run(f, r).getStatus() == 429) {
                blocked++;
            }
        }
        assertEquals(3, blocked, "6 attempts from one real client with limit 3 -> 3 blocked");
    }

    @Test
    void withoutTrustedProxiesTheHeaderIsIgnoredEntirely() throws Exception {
        RateLimitFilter f = filter(0);
        int blocked = 0;
        for (int i = 0; i < 6; i++) {
            MockHttpServletRequest r = req("POST", "/api/auth/login");
            r.addHeader("X-Forwarded-For", "1.1.1." + i);
            if (run(f, r).getStatus() == 429) {
                blocked++;
            }
        }
        assertEquals(3, blocked);
    }

    @Test
    void aDisabledLimiterIsATransparentPassThrough() throws Exception {
        props.setEnabled(false);
        store.down = true;
        RateLimitFilter f = filter(0);
        for (int i = 0; i < 30; i++) {
            assertEquals(200, run(f, req("POST", "/api/auth/login")).getStatus());
        }
        assertEquals(30, chainCalls.get());
        assertEquals(0, store.calls);
    }
}
