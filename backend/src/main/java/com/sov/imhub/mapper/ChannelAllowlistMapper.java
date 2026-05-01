package com.sov.imhub.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sov.imhub.domain.ChannelAllowlistEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface ChannelAllowlistMapper extends BaseMapper<ChannelAllowlistEntity> {

    /**
     * One round-trip: allowed if no allowlist rows for channel, or user is listed (enabled rows only).
     *
     * @return 1 if user may query, 0 otherwise
     */
    @Select(
            "SELECT CASE "
                    + "WHEN (SELECT COUNT(*) FROM t_channel_allowlist WHERE channel_id = #{channelId} AND enabled = 1) = 0 THEN 1 "
                    + "WHEN (SELECT COUNT(*) FROM t_channel_allowlist WHERE channel_id = #{channelId} AND external_user_id = #{externalUserId} AND enabled = 1) > 0 THEN 1 "
                    + "ELSE 0 END")
    int isUserAllowed(@Param("channelId") long channelId, @Param("externalUserId") String externalUserId);
}
