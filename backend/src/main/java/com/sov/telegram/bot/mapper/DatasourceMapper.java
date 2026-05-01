package com.sov.telegram.bot.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sov.telegram.bot.domain.DatasourceEntity;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface DatasourceMapper extends BaseMapper<DatasourceEntity> {}
