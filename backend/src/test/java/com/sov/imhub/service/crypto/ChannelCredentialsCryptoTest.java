package com.sov.imhub.service.crypto;

import com.sov.imhub.config.AppProperties;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.security.SecureRandom;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChannelCredentialsCryptoTest {

    @Test
    void sealPassthroughWhenEncryptionOff() {
        AppProperties props = new AppProperties();
        props.getEncryption().setSecretKeyBase64("");
        EncryptionService enc = new EncryptionService(props);
        ReflectionTestUtils.invokeMethod(enc, "init");
        String json = "{\"appId\":\"x\",\"appSecret\":\"y\"}";
        assertEquals(json, ChannelCredentialsCrypto.seal(enc, json));
        assertEquals(json, ChannelCredentialsCrypto.unwrap(enc, json));
    }

    @Test
    void sealAndUnwrapRoundTripWhenEncryptionOn() {
        byte[] key = new byte[32];
        new SecureRandom().nextBytes(key);
        AppProperties props = new AppProperties();
        props.getEncryption().setSecretKeyBase64(Base64.getEncoder().encodeToString(key));
        EncryptionService enc = new EncryptionService(props);
        ReflectionTestUtils.invokeMethod(enc, "init");
        String json = "{\"appSecret\":\"ding-secret\"}";
        String stored = ChannelCredentialsCrypto.seal(enc, json);
        assertTrue(stored.startsWith(ChannelCredentialsCrypto.PREFIX));
        assertEquals(json, ChannelCredentialsCrypto.unwrap(enc, stored));
    }

    @Test
    void unwrapThrowsWhenStoredEncryptedButKeyMissing() {
        byte[] key = new byte[32];
        new SecureRandom().nextBytes(key);
        AppProperties on = new AppProperties();
        on.getEncryption().setSecretKeyBase64(Base64.getEncoder().encodeToString(key));
        EncryptionService encOn = new EncryptionService(on);
        ReflectionTestUtils.invokeMethod(encOn, "init");
        String stored = ChannelCredentialsCrypto.seal(encOn, "{\"a\":1}");

        AppProperties off = new AppProperties();
        off.getEncryption().setSecretKeyBase64("");
        EncryptionService encOff = new EncryptionService(off);
        ReflectionTestUtils.invokeMethod(encOff, "init");
        assertFalse(encOff.isEnabled());
        assertThrows(IllegalStateException.class, () -> ChannelCredentialsCrypto.unwrap(encOff, stored));
    }
}
