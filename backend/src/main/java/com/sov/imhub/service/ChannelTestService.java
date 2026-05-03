package com.sov.imhub.service;

import com.sov.imhub.domain.BotChannelEntity;
import com.sov.imhub.mapper.BotChannelMapper;
import com.sov.imhub.service.crypto.ChannelCredentialsCrypto;
import com.sov.imhub.service.crypto.EncryptionService;
import com.sov.imhub.service.test.PlatformTester;
import com.sov.imhub.web.NotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 渠道测试服务：向指定渠道发送测试消息，验证凭证和连通性。
 *
 * <p>使用策略模式，每个平台实现 {@link PlatformTester} 接口。
 * 新增平台只需添加一个 {@code @Component} 实现类，无需修改本服务。</p>
 *
 * @see PlatformTester
 */
@Slf4j
@Service
public class ChannelTestService {

    private final BotChannelMapper botChannelMapper;
    private final EncryptionService encryptionService;
    private final Map<String, PlatformTester> testers;

    public ChannelTestService(BotChannelMapper botChannelMapper,
                              EncryptionService encryptionService,
                              List<PlatformTester> testerList) {
        this.botChannelMapper = botChannelMapper;
        this.encryptionService = encryptionService;
        this.testers = testerList.stream()
                .collect(Collectors.toMap(t -> t.platform().toUpperCase(), t -> t));
    }

    /**
     * 向渠道发送测试消息。
     *
     * @param channelId 渠道 ID
     * @param targetId  目标 chat_id / user_id / channel_id（可为 null）
     * @return 测试结果
     */
    public PlatformTester.TestResult testChannel(Long channelId, String targetId) {
        BotChannelEntity channel = botChannelMapper.selectById(channelId);
        if (channel == null) {
            throw new NotFoundException("渠道不存在");
        }
        String platform = channel.getPlatform();
        if (platform == null) {
            return new PlatformTester.TestResult(false, "渠道平台类型为空");
        }

        PlatformTester tester = testers.get(platform.toUpperCase());
        if (tester == null) {
            return new PlatformTester.TestResult(false, "不支持的平台: " + platform);
        }

        String credPlain = ChannelCredentialsCrypto.unwrap(encryptionService, channel.getCredentialsJson());
        String testMsg = "✅ IM Bot Hub 连通性测试成功！\n渠道 ID: " + channelId + "\n平台: " + platform;

        try {
            return tester.test(credPlain, targetId, testMsg);
        } catch (Exception e) {
            log.warn("Channel test failed channelId={}: {}", channelId, e.getMessage());
            return new PlatformTester.TestResult(false, "测试失败: " + e.getMessage());
        }
    }
}
