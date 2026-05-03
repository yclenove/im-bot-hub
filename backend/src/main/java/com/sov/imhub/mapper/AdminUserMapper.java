package com.sov.imhub.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sov.imhub.domain.AdminUserEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 管理用户 Mapper。
 */
@Mapper
public interface AdminUserMapper extends BaseMapper<AdminUserEntity> {
}
