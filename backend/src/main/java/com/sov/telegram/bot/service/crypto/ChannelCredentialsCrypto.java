package com.sov.telegram.bot.service.crypto;

/**
 * IM 渠道 {@code credentials_json}：启用全局加密时整段 JSON AES-GCM 封装，避免 AppSecret 等明文落库。
 * 无前缀的旧数据仍按明文 JSON 解析。
 */
public final class ChannelCredentialsCrypto {

    static final String PREFIX = "enc:v1:";

    private ChannelCredentialsCrypto() {}

    public static String seal(EncryptionService enc, String plainJson) {
        if (plainJson == null) {
            return null;
        }
        if (!enc.isEnabled()) {
            return plainJson;
        }
        return PREFIX + enc.encrypt(plainJson);
    }

    /**
     * @throws IllegalStateException 数据为加密封装但未配置解密密钥
     */
    public static String unwrap(EncryptionService enc, String stored) {
        if (stored == null) {
            return "";
        }
        String s = stored.trim();
        if (!s.startsWith(PREFIX)) {
            return s;
        }
        if (!enc.isEnabled()) {
            throw new IllegalStateException(
                    "渠道凭据以 enc:v1 加密存储，但未配置 app.encryption.secret-key-base64，无法解密");
        }
        return enc.decrypt(s.substring(PREFIX.length()));
    }
}
