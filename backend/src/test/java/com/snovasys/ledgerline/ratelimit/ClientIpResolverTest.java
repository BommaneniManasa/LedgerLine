package com.snovasys.ledgerline.ratelimit;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class ClientIpResolverTest {

    private static MockHttpServletRequest request(String peer, String... forwardedFor) {
        MockHttpServletRequest r = new MockHttpServletRequest("POST", "/api/auth/login");
        r.setRemoteAddr(peer);
        for (String h : forwardedFor) {
            r.addHeader("X-Forwarded-For", h);
        }
        return r;
    }

    @Test
    void byDefaultTheForwardedHeaderIsIgnored() {
        assertEquals("10.0.0.5", new ClientIpResolver(0).resolve(request("10.0.0.5", "6.6.6.6")));
    }

    @Test
    void oneTrustedProxyUsesTheLastEntry() {
        assertEquals("198.51.100.9", new ClientIpResolver(1).resolve(request("10.0.0.5", "198.51.100.9")));
    }

    @Test
    void aForgedLeftmostValueIsIgnored() {
        // the client sent "6.6.6.6"; our proxy appended the real address
        assertEquals("198.51.100.9",
                new ClientIpResolver(1).resolve(request("10.0.0.5", "6.6.6.6, 198.51.100.9")));
    }

    @Test
    void twoTrustedProxiesUseTheSecondFromTheRight() {
        assertEquals("198.51.100.9",
                new ClientIpResolver(2).resolve(request("10.0.0.5", "6.6.6.6, 198.51.100.9, 172.16.0.3")));
    }

    @Test
    void multipleHeaderLinesAreTreatedAsOneList() {
        assertEquals("198.51.100.9",
                new ClientIpResolver(1).resolve(request("10.0.0.5", "6.6.6.6", "198.51.100.9")));
    }

    @Test
    void aMissingOrTooShortHeaderFallsBackToThePeerAddress() {
        assertEquals("10.0.0.5", new ClientIpResolver(1).resolve(request("10.0.0.5")));
        assertEquals("10.0.0.5", new ClientIpResolver(2).resolve(request("10.0.0.5", "198.51.100.9")));
    }

    @Test
    void garbageInTheHeaderFallsBackToThePeerAddress() {
        assertEquals("10.0.0.5", new ClientIpResolver(1).resolve(request("10.0.0.5", "not-an-ip")));
        assertEquals("10.0.0.5", new ClientIpResolver(1).resolve(request("10.0.0.5", "1.2.3.4<script>")));
        assertEquals("10.0.0.5", new ClientIpResolver(1).resolve(request("10.0.0.5", "")));
    }

    @Test
    void ipv6IsAcceptedAndLowerCased() {
        assertEquals("2001:db8::1", new ClientIpResolver(1).resolve(request("10.0.0.5", "2001:DB8::1")));
    }
}
