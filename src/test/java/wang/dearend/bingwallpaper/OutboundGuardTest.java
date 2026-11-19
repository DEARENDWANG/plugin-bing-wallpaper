package wang.dearend.bingwallpaper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class OutboundGuardTest {

    @Test
    void rejectsNonHttpSchemes() {
        assertThrows(IllegalArgumentException.class, () -> OutboundGuard.validate("ftp://example.com/a.json"));
        assertThrows(IllegalArgumentException.class, () -> OutboundGuard.validate("file:///etc/passwd"));
        assertThrows(IllegalArgumentException.class, () -> OutboundGuard.validate("example.com/a.json"));
    }

    @Test
    void rejectsMalformedUrls() {
        assertThrows(IllegalArgumentException.class, () -> OutboundGuard.validate("http://"));
        assertThrows(IllegalArgumentException.class, () -> OutboundGuard.validate("not a url"));
    }

    @Test
    void rejectsLoopbackAndPrivateAddresses() {
        assertThrows(IllegalArgumentException.class, () -> OutboundGuard.validate("http://127.0.0.1/a.json"));
        assertThrows(IllegalArgumentException.class, () -> OutboundGuard.validate("http://localhost/a.json"));
        assertThrows(IllegalArgumentException.class, () -> OutboundGuard.validate("http://10.0.0.1/a.json"));
        assertThrows(IllegalArgumentException.class, () -> OutboundGuard.validate("http://192.168.1.1/a.json"));
        assertThrows(IllegalArgumentException.class, () -> OutboundGuard.validate("http://172.16.0.1/a.json"));
        assertThrows(IllegalArgumentException.class, () -> OutboundGuard.validate("http://169.254.169.254/latest/meta-data"));
        assertThrows(IllegalArgumentException.class, () -> OutboundGuard.validate("http://[::1]/a.json"));
        assertThrows(IllegalArgumentException.class, () -> OutboundGuard.validate("http://0.0.0.0/a.json"));
    }

    @Test
    void acceptsPublicHttpsUrl() {
        assertEquals("example.com", OutboundGuard.validate("https://example.com/year_2026.json").getHost());
    }
}