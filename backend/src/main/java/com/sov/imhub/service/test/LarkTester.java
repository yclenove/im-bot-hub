package com.sov.imhub.service.test;

import com.sov.imhub.im.lark.LarkApiClient;
import com.sov.imhub.im.lark.LarkCredentials;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 飞书连通性测试：调用飞书开放平台发送文本消息。
 */
@Component
@RequiredArgsConstructor
public class LarkTester implements PlatformTester {

    private final LarkApiClient larkApiClient;

    @Override
    public String platform() {
        return "LARK";
    }

    @Override
    public TestResult test(String credPlain, String targetId, String testMsg) {
        try {
            LarkCredentials creds = LarkCredentials.fromJson(credPlain);
            if (creds.getAppId() == null || creds.getAppSecret() == null) {
                return new TestResult(false, "App ID 或 App Secret 为空");
            }
            // 先验证凭证有效性（获取 tenant_access_token）
            String token = larkApiClient.getTenantAccessToken(creds.getAppId(), creds.getAppSecret());
            if (token == null) {
                return new TestResult(false, "飞书凭证无效：无法获取 tenant_access_token");
            }
            if (targetId == null || targetId.isBlank()) {
                return new TestResult(true, "飞书凭证验证成功！tenant_access_token 已获取。请填写目标 ID 发送测试消息。");
            }
            String receiveIdType = targetId.startsWith("oc_") ? "chat_id" : "open_id";
            larkApiClient.sendText(creds.getAppId(), creds.getAppSecret(), targetId.trim(), receiveIdType, testMsg);
            return new TestResult(true, "测试消息已发送到 " + receiveIdType + "=" + targetId);
        } catch (Exception e) {
            return new TestResult(false, "测试失败: " + e.getMessage());
        }
    }
}
