package com.sov.telegram.bot.service.crypto;

import com.sov.telegram.bot.config.AppProperties;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.security.SecureRandom;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link EncryptionService} 单元测试：密钥启用时的加解密往返，以及未配置密钥时的明文透传。
 */
class EncryptionServiceTest {

    @Test
    void encryptDecryptRoundTripWhenKeyConfigured() {
        byte[] key = new byte[32];
        new SecureRandom().nextBytes(key);
        AppProperties props = new AppProperties();
        props.getEncryption().setSecretKeyBase64(Base64.getEncoder().encodeToString(key));
        EncryptionService svc = new EncryptionService(props);
        ReflectionTestUtils.invokeMethod(svc, "init");
        assertTrue(svc.isEnabled());
        String plain = "db-secret-测试";
        String enc = svc.encrypt(plain);
        assertNotEquals(plain, enc);
        assertEquals(plain, svc.decrypt(enc));
    }

    @Test
    void plainPassthroughWhenEncryptionDisabled() {
        AppProperties props = new AppProperties();
        props.getEncryption().setSecretKeyBase64("");
        EncryptionService svc = new EncryptionService(props);
        ReflectionTestUtils.invokeMethod(svc, "init");
        assertFalse(svc.isEnabled());
        assertEquals("plain", svc.encrypt("plain"));
        assertEquals("plain", svc.decrypt("plain"));
    }
}
