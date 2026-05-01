package com.sov.telegram.bot.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sov.telegram.bot.domain.AuditLogEntity;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AuditLogMapper extends BaseMapper<AuditLogEntity> {}
