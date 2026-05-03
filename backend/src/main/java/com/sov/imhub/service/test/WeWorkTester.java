package com.sov.imhub.service.test;

import com.sov.imhub.im.wework.WeWorkCredentials;
import org.springframework.stereotype.Component;

/**
 * 企业微信连通性测试：仅验证凭证格式（回调模式不支持主动发消息）。
 */
@Component
public class WeWorkTester implements PlatformTester {

    @Override
    public String platform() {
        return "WEWORK";
    }

    @Override
    public TestResult test(String credPlain, String targetId, String testMsg) {
        try {
            WeWorkCredentials creds = WeWorkCredentials.fromJson(credPlain);
            if (creds.getCorpId() == null || creds.getCorpId().isBlank()) {
                return new TestResult(false, "CorpID 为空");
            }
            if (creds.getAgentId() == null) {
                return new TestResult(false, "AgentId 为空");
            }
            return new TestResult(true, "企业微信凭证格式验证通过（回调模式不支持主动发消息）");
        } catch (Exception e) {
            return new TestResult(false, "凭证解析失败: " + e.getMessage());
        }
    }
}
