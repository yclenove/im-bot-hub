package com.sov.imhub.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.sov.imhub.domain.AdminUserEntity;
import com.sov.imhub.mapper.AdminUserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 认证服务：登录、注册、权限验证。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final AdminUserMapper userMapper;
    private final JwtService jwtService;
    private final PasswordEncoder passwordEncoder;

    /**
     * 用户登录。
     */
    public Map<String, Object> login(String username, String password) {
        AdminUserEntity user = userMapper.selectOne(
                new LambdaQueryWrapper<AdminUserEntity>()
                        .eq(AdminUserEntity::getUsername, username)
                        .eq(AdminUserEntity::getEnabled, true));

        if (user == null || !passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new IllegalArgumentException("用户名或密码错误");
        }

        // 更新最后登录时间
        user.setLastLoginAt(LocalDateTime.now());
        userMapper.updateById(user);

        String token = jwtService.generateToken(user.getId(), user.getUsername(), user.getRole());

        log.info("user login success userId={} username={}", user.getId(), user.getUsername());

        return Map.of(
                "token", token,
                "userId", user.getId(),
                "username", user.getUsername(),
                "displayName", user.getDisplayName() != null ? user.getDisplayName() : user.getUsername(),
                "role", user.getRole());
    }

    /**
     * 创建用户。
     */
    public AdminUserEntity createUser(String username, String password, String displayName, String role) {
        AdminUserEntity existing = userMapper.selectOne(
                new LambdaQueryWrapper<AdminUserEntity>()
                        .eq(AdminUserEntity::getUsername, username));
        if (existing != null) {
            throw new IllegalArgumentException("用户名已存在");
        }

        AdminUserEntity user = new AdminUserEntity();
        user.setUsername(username);
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setDisplayName(displayName);
        user.setRole(role != null ? role : "VIEWER");
        user.setEnabled(true);
        userMapper.insert(user);

        log.info("create user success userId={} username={}", user.getId(), username);
        return user;
    }

    /**
     * 修改密码。
     */
    public void changePassword(Long userId, String oldPassword, String newPassword) {
        AdminUserEntity user = userMapper.selectById(userId);
        if (user == null) {
            throw new IllegalArgumentException("用户不存在");
        }

        if (!passwordEncoder.matches(oldPassword, user.getPasswordHash())) {
            throw new IllegalArgumentException("原密码错误");
        }

        user.setPasswordHash(passwordEncoder.encode(newPassword));
        userMapper.updateById(user);

        log.info("change password success userId={}", userId);
    }
}
