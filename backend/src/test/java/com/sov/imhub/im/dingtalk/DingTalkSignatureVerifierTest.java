package com.sov.imhub.im.dingtalk;

import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DingTalkSignatureVerifierTest {

    @Test
    void verify_acceptsValidSignature() throws Exception {
        String secret = "test-app-secret";
        String ts = String.valueOf(System.currentTimeMillis());
        byte[] raw = DingTalkSignatureVerifier.hmacSha256(secret, ts + "\n" + secret);
        String sign = Base64.getEncoder().encodeToString(raw);
        assertTrue(DingTalkSignatureVerifier.verify(ts, sign, secret));
    }

    @Test
    void verify_rejectsWrongSign() {
        String secret = "s";
        String ts = String.valueOf(System.currentTimeMillis());
        assertFalse(DingTalkSignatureVerifier.verify(ts, Base64.getEncoder().encodeToString(new byte[32]), secret));
    }

    @Test
    void verify_rejectsStaleTimestamp() {
        String secret = "s";
        String ts = String.valueOf(System.currentTimeMillis() - 4_000_000L);
        assertFalse(DingTalkSignatureVerifier.verify(ts, "x", secret));
    }
}
