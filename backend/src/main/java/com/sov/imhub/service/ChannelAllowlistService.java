package com.sov.imhub.service;

import com.sov.imhub.mapper.ChannelAllowlistMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 通用渠道白名单服务：基于 t_channel_allowlist 判断用户是否有权使用查询命令。
 * 若渠道无白名单记录，则所有用户均可使用。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChannelAllowlistService {

    private final ChannelAllowlistMapper channelAllowlistMapper;

    /**
     * 检查指定渠道的用户是否在白名单中。
     * 若渠道无白名单记录，返回 true（允许所有用户）。
     */
    public boolean isAllowed(long channelId, String externalUserId) {
        if (externalUserId == null || externalUserId.isBlank()) {
            return false;
        }
        return channelAllowlistMapper.isUserAllowed(channelId, externalUserId) == 1;
    }
}
