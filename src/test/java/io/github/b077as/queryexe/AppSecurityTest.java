package io.github.b077as.queryexe;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;

import io.github.b077as.jolt.SecurityPolicy;
import org.junit.jupiter.api.Test;

class AppSecurityTest {

    /** The policy is made by cutting a source out of Jolt's default text: fail if that text ever changes shape. */
    @Test
    void thePageMayRunOnlyItsOwnScriptsAndReachNothingElse() {
        SecurityPolicy policy = App.security(false);
        String csp = policy.appContentSecurityPolicy();
        assertFalse(csp.contains("ws:"), csp);
        assertFalse(csp.contains("127.0.0.1"), csp);
        assertTrue(csp.contains("script-src 'self' 'wasm-unsafe-eval'"), csp);
        assertTrue(csp.contains("connect-src 'self' data: blob:;"), csp);
        assertTrue(policy.blockDownloads());
        assertEquals(Set.of(), policy.trustedOrigins());
    }

    @Test
    void onlyDevModeTrustsTheDevServer() {
        assertEquals(Set.of("http://127.0.0.1:5173"), App.security(true).trustedOrigins());
    }
}
