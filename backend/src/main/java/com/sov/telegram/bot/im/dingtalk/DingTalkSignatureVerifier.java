package com.sov.telegram.bot.im.dingtalk;

import org.springframework.util.StringUtils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;

public final class DingTalkSignatureVerifier {

    private static final long MAX_SKEW_MS = 3600_000L;

    private DingTalkSignatureVerifier() {}

    /**
     * 钉钉 Outgoing：{@code BASE64(HMAC_SHA256(appSecret, timestamp + "\n" + appSecret))}，与请求头 {@code sign} 比较。
     */
    public static boolean verify(String timestamp, String sign, String appSecret) {
        if (!StringUtils.hasText(timestamp) || !StringUtils.hasText(sign) || !StringUtils.hasText(appSecret)) {
            return false;
        }
        try {
            long ts = Long.parseLong(timestamp.trim());
            long now = System.currentTimeMillis();
            if (Math.abs(now - ts) > MAX_SKEW_MS) {
                return false;
            }
        } catch (NumberFormatException e) {
            return false;
        }
        try {
            byte[] expected = hmacSha256(appSecret.trim(), timestamp.trim() + "\n" + appSecret.trim());
            byte[] actual = Base64.getDecoder().decode(sign.trim());
            return MessageDigest.isEqual(expected, actual);
        } catch (IllegalArgumentException | java.security.GeneralSecurityException e) {
            return false;
        }
    }

    static byte[] hmacSha256(String secret, String message) throws java.security.GeneralSecurityException {
        Mac mac = Mac.getInstance("HmacSHA256");
        SecretKeySpec key = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        mac.init(key);
        return mac.doFinal(message.getBytes(StandardCharsets.UTF_8));
    }
}
