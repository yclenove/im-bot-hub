package com.sov.telegram.bot.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sov.telegram.bot.domain.Bot;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface BotMapper extends BaseMapper<Bot> {}
