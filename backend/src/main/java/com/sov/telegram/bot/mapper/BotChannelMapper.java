package com.sov.telegram.bot.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sov.telegram.bot.domain.BotChannelEntity;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface BotChannelMapper extends BaseMapper<BotChannelEntity> {}
