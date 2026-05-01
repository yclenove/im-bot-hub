package com.sov.imhub.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sov.imhub.domain.UserAllowlistEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface UserAllowlistMapper extends BaseMapper<UserAllowlistEntity> {

    /**
     * One round-trip: allowed if no allowlist rows for bot, or user is listed (enabled rows only).
     *
     * @return 1 if user may query, 0 otherwise
     */
    @Select(
            "SELECT CASE "
                    + "WHEN (SELECT COUNT(*) FROM t_user_allowlist WHERE bot_id = #{botId} AND enabled = 1) = 0 THEN 1 "
                    + "WHEN (SELECT COUNT(*) FROM t_user_allowlist WHERE bot_id = #{botId} AND telegram_user_id = #{telegramUserId} AND enabled = 1) > 0 THEN 1 "
                    + "ELSE 0 END")
    int isTelegramUserAllowed(@Param("botId") long botId, @Param("telegramUserId") long telegramUserId);
}
