package com.sov.imhub.service.slack;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Slack 请求签名验证。
 * @see <a href="https://api.slack.com/authentication/verifying-requests-from-slack">Verifying Requests from Slack</a>
 */
@Slf4j
@Component
public class SlackSignatureVerifier {

    private static final String SLACK_SIGNATURE_VERSION = "v0";

    /**
     * 验证 Slack 请求签名。
     *
     * @param signingSecret  Slack App Signing Secret
     * @param timestamp      请求头 X-Slack-Request-Timestamp
     * @param body           请求体原始内容
     * @param signature      请求头 X-Slack-Signature
     * @return 签名是否有效
     */
    public boolean verify(String signingSecret, String timestamp, String body, String signature) {
        if (signingSecret == null || signingSecret.isBlank()) {
            log.warn("Slack signing secret is empty, skipping signature verification");
            return true;
        }
        if (timestamp == null || signature == null || body == null) {
            return false;
        }

        // 检查时间戳是否在 5 分钟内（防止重放攻击）
        try {
            long requestTime = Long.parseLong(timestamp);
            long currentTime = System.currentTimeMillis() / 1000;
            if (Math.abs(currentTime - requestTime) > 300) {
                log.warn("Slack request timestamp is too old: {}", timestamp);
                return false;
            }
        } catch (NumberFormatException e) {
            log.warn("Invalid Slack request timestamp: {}", timestamp);
            return false;
        }

        // 计算签名
        String baseString = SLACK_SIGNATURE_VERSION + ":" + timestamp + ":" + body;
        String computedSignature = computeSignature(signingSecret, baseString);
        if (computedSignature == null) {
            return false;
        }

        // 比较签名（使用常量时间比较防止时序攻击）
        String expectedSignature = SLACK_SIGNATURE_VERSION + "=" + computedSignature;
        return MessageDigest.isEqual(
                expectedSignature.getBytes(StandardCharsets.UTF_8),
                signature.getBytes(StandardCharsets.UTF_8));
    }

    private String computeSignature(String signingSecret, String baseString) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec keySpec = new SecretKeySpec(signingSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(keySpec);
            byte[] hash = mac.doFinal(baseString.getBytes(StandardCharsets.UTF_8));
            return bytesToHex(hash);
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            log.error("Failed to compute Slack signature: {}", e.getMessage());
            return null;
        }
    }

    private String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
