package com.sov.imhub.service.test;

import com.sov.imhub.im.dingtalk.DingTalkCredentials;
import org.springframework.stereotype.Component;

/**
 * 钉钉连通性测试：仅验证凭证格式（Outgoing 模式不支持主动发消息）。
 */
@Component
public class DingTalkTester implements PlatformTester {

    @Override
    public String platform() {
        return "DINGTALK";
    }

    @Override
    public TestResult test(String credPlain, String targetId, String testMsg) {
        try {
            DingTalkCredentials creds = DingTalkCredentials.fromJson(credPlain);
            if (creds.getAppSecret() == null || creds.getAppSecret().isBlank()) {
                return new TestResult(false, "App Secret 为空");
            }
            return new TestResult(true, "钉钉凭证格式验证通过（Outgoing 模式不支持主动发消息）");
        } catch (Exception e) {
            return new TestResult(false, "凭证解析失败: " + e.getMessage());
        }
    }
}
