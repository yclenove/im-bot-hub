package com.sov.imhub.service.test;

/**
 * 平台测试策略接口：每个 IM 平台实现自己的连通性测试逻辑。
 */
public interface PlatformTester {

    /**
     * 支持的平台标识（大写），如 TELEGRAM、LARK、SLACK 等。
     */
    String platform();

    /**
     * 执行连通性测试。
     *
     * @param credPlain 解密后的凭证 JSON 字符串
     * @param targetId  目标 chat_id / user_id / channel_id（可为 null）
     * @param testMsg   测试消息内容
     * @return 测试结果
     */
    TestResult test(String credPlain, String targetId, String testMsg);

    /**
     * 测试结果。
     */
    record TestResult(boolean success, String message) {}
}
