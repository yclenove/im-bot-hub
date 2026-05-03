package com.sov.imhub.service.discord;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.util.HexFormat;

/**
 * Discord 请求签名验证（Ed25519）。
 * @see <a href="https://discord.com/developers/docs/interactions/receiving-and-responding#security-and-authorization">Security and Authorization</a>
 */
@Slf4j
@Component
public class DiscordSignatureVerifier {

    /**
     * 验证 Discord 请求签名。
     *
     * @param publicKeyHex  Discord App 公钥（十六进制字符串）
     * @param timestamp     请求头 X-Signature-Timestamp
     * @param body          请求体原始内容
     * @param signatureHex  请求头 X-Signature-Ed25519
     * @return 签名是否有效
     */
    public boolean verify(String publicKeyHex, String timestamp, String body, String signatureHex) {
        if (publicKeyHex == null || publicKeyHex.isBlank()) {
            log.warn("Discord public key is empty, skipping signature verification");
            return true;
        }
        if (timestamp == null || signatureHex == null || body == null) {
            return false;
        }

        try {
            // 解析公钥
            byte[] publicKeyBytes = HexFormat.of().parseHex(publicKeyHex);
            KeyFactory keyFactory = KeyFactory.getInstance("Ed25519");
            PublicKey publicKey = keyFactory.generatePublic(new X509EncodedKeySpec(publicKeyBytes));

            // 构造签名数据
            String message = timestamp + body;
            byte[] messageBytes = message.getBytes(StandardCharsets.UTF_8);

            // 解析签名
            byte[] signatureBytes = HexFormat.of().parseHex(signatureHex);

            // 验证签名
            Signature sig = Signature.getInstance("Ed25519");
            sig.initVerify(publicKey);
            sig.update(messageBytes);
            return sig.verify(signatureBytes);
        } catch (Exception e) {
            log.error("Failed to verify Discord signature: {}", e.getMessage());
            return false;
        }
    }
}
