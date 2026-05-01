package com.sov.imhub.service.crypto;

import com.sov.imhub.config.AppProperties;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * AES-GCM 加解密：业务数据源密码、IM 渠道 {@code credentials_json}（经 {@link ChannelCredentialsCrypto} 封装）等。未配置密钥时为明文透传（仅开发环境）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EncryptionService {

    private static final String AES = "AES";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int GCM_TAG_BITS = 128;
    private static final int IV_LEN = 12;

    private final AppProperties appProperties;
    private SecretKey aesKey;
    private boolean enabled;

    @PostConstruct
    void init() {
        String b64 = appProperties.getEncryption().getSecretKeyBase64();
        if (b64 == null || b64.isBlank()) {
            enabled = false;
            log.warn("app.encryption.secret-key-base64 is empty; datasource passwords stored as PLAIN. Do not use in production.");
            return;
        }
        byte[] keyBytes = Base64.getDecoder().decode(b64.trim());
        if (keyBytes.length != 16 && keyBytes.length != 24 && keyBytes.length != 32) {
            throw new IllegalStateException("Encryption key must be 128/192/256 bits (Base64 decoded length 16/24/32)");
        }
        aesKey = new SecretKeySpec(keyBytes, AES);
        enabled = true;
    }

    /** 是否已配置 {@code app.encryption.secret-key-base64}（生产环境应对敏感字段加密）。 */
    public boolean isEnabled() {
        return enabled;
    }

    public String encrypt(String plainText) {
        if (!enabled || plainText == null) {
            return plainText;
        }
        try {
            byte[] iv = new byte[IV_LEN];
            SecureRandom.getInstanceStrong().nextBytes(iv);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, aesKey, new GCMParameterSpec(GCM_TAG_BITS, iv));
            byte[] enc = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));
            ByteBuffer buf = ByteBuffer.allocate(iv.length + enc.length);
            buf.put(iv);
            buf.put(enc);
            return Base64.getEncoder().encodeToString(buf.array());
        } catch (Exception e) {
            throw new IllegalStateException("encrypt failed", e);
        }
    }

    public String decrypt(String cipherText) {
        if (!enabled || cipherText == null) {
            return cipherText;
        }
        try {
            byte[] all = Base64.getDecoder().decode(cipherText);
            ByteBuffer buf = ByteBuffer.wrap(all);
            byte[] iv = new byte[IV_LEN];
            buf.get(iv);
            byte[] enc = new byte[buf.remaining()];
            buf.get(enc);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, aesKey, new GCMParameterSpec(GCM_TAG_BITS, iv));
            byte[] plain = cipher.doFinal(enc);
            return new String(plain, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("decrypt failed", e);
        }
    }
}
