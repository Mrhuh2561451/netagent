package com.hhxy.huazi.user.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import cn.hutool.core.lang.Assert;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hhxy.huazi.common.exception.ClientException;
import com.hhxy.huazi.user.controller.request.ChangePasswordRequest;
import com.hhxy.huazi.user.controller.request.UserCreateRequest;
import com.hhxy.huazi.user.controller.request.UserPageRequest;
import com.hhxy.huazi.user.controller.request.UserUpdateRequest;
import com.hhxy.huazi.user.controller.vo.UserVO;
import com.hhxy.huazi.user.entity.User;
import com.hhxy.huazi.user.mapper.UserMapper;
import com.hhxy.huazi.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UserServiceImple implements UserService {

    private static final String DEFAULT_ADMIN_USERNAME = "admin";
    private static final String ADMIN_ROLE = "admin";
    private static final String USER_ROLE = "user";

    private final UserMapper userMapper;
    @Override
    public IPage<UserVO> pageQuery(UserPageRequest requestParam) {
        Assert.notNull(requestParam, () -> new ClientException("请求不能为空"));
        Assert.isTrue(requestParam.getCurrent() > 0, () -> new ClientException("页码必须大于 0"));
        Assert.isTrue(requestParam.getSize() > 0 && requestParam.getSize() <= 100,
                () -> new ClientException("每页条数必须在 1～100 之间"));
        String keyword = StrUtil.trimToNull(requestParam.getKeyword());
        Page<User> page = new Page<>(requestParam.getCurrent(), requestParam.getSize());
        IPage<User> result = userMapper.selectPage(
                page,
                Wrappers.lambdaQuery(User.class)
                        .eq(User::getDeleted, 0)
                        .and(StrUtil.isNotBlank(keyword), wrapper -> wrapper
                                .like(User::getUsername, keyword)
                                .or()
                                .like(User::getRole, keyword))
                        .orderByDesc(User::getUpdateTime)
        );
        return result.convert(this::toVO);
    }

    @Override
    public String create(UserCreateRequest requestParam) {
        Assert.notNull(requestParam, () -> new ClientException("请求不能为空"));
        String username = StrUtil.trimToNull(requestParam.getUsername());
        String password = StrUtil.trimToNull(requestParam.getPassword());
        Assert.notBlank(username, () -> new ClientException("用户名不能为空"));
        Assert.notBlank(password, () -> new ClientException("密码不能为空"));
        if (DEFAULT_ADMIN_USERNAME.equalsIgnoreCase(username)) {
            throw new ClientException("默认管理员用户名不可用");
        }
        String role = normalizeRole(requestParam.getRole());
        ensureUsernameAvailable(username, null);

        User record = User.builder()
                .username(username)
                .password(password)
                .role(role)
                .avatar(StrUtil.trimToNull(requestParam.getAvatar()))
                .deleted(0)
                .build();
        try {
            userMapper.insert(record);
        } catch (DuplicateKeyException ex) {
            // 表中用户名全局唯一，逻辑删除的账号和并发创建也可能触发冲突。
            throw new ClientException("用户名已存在");
        }
        return record.getId();
    }

    @Override
    public void update(String id, UserUpdateRequest requestParam) {
        Assert.notNull(requestParam, () -> new ClientException("请求不能为空"));
        User record = loadById(id);
        ensureNotDefaultAdmin(record);

        if (requestParam.getUsername() != null) {
            String username = StrUtil.trimToNull(requestParam.getUsername());
            Assert.notBlank(username, () -> new ClientException("用户名不能为空"));
            if (!username.equals(record.getUsername())) {
                if (DEFAULT_ADMIN_USERNAME.equalsIgnoreCase(username)) {
                    throw new ClientException("默认管理员用户名不可用");
                }
                ensureUsernameAvailable(username, record.getId());
            }
            record.setUsername(username);
        }
        if (requestParam.getRole() != null) {
            record.setRole(normalizeRole(requestParam.getRole()));
        }
        if (requestParam.getAvatar() != null) {
            record.setAvatar(StrUtil.trimToNull(requestParam.getAvatar()));
        }
        if (requestParam.getPassword() != null) {
            String password = StrUtil.trimToNull(requestParam.getPassword());
            Assert.notBlank(password, () -> new ClientException("新密码不能为空"));
            record.setPassword(password);
        }
        try {
            userMapper.updateById(record);
        } catch (DuplicateKeyException ex) {
            throw new ClientException("用户名已存在");
        }
    }

    @Override
    public void delete(String id) {
        User record = loadById(id);
        ensureNotDefaultAdmin(record);
        userMapper.deleteById(record.getId());
    }

    @Override
    public void changePassword(ChangePasswordRequest requestParam) {
        Assert.notNull(requestParam, () -> new ClientException("请求不能为空"));
        String current = StrUtil.trimToNull(requestParam.getCurrentPassword());
        String next = StrUtil.trimToNull(requestParam.getNewPassword());
        Assert.notBlank(current, () -> new ClientException("当前密码不能为空"));
        Assert.notBlank(next, () -> new ClientException("新密码不能为空"));

        User record = loadById(StpUtil.getLoginIdAsString());
        if (!current.equals(record.getPassword())) {
            throw new ClientException("当前密码不正确");
        }
        record.setPassword(next);
        userMapper.updateById(record);
    }

    private User loadById(String id) {
        Assert.notBlank(id, () -> new ClientException("用户 ID 不能为空"));
        User record = userMapper.selectOne(
                Wrappers.lambdaQuery(User.class)
                        .eq(User::getId, id)
                        .eq(User::getDeleted, 0)
        );
        Assert.notNull(record, () -> new ClientException("用户不存在"));
        return record;
    }

    private void ensureNotDefaultAdmin(User record) {
        if (DEFAULT_ADMIN_USERNAME.equalsIgnoreCase(record.getUsername())) {
            throw new ClientException("默认管理员不允许修改或删除");
        }
    }

    private void ensureUsernameAvailable(String username, String excludeId) {
        User existing = userMapper.selectOne(
                Wrappers.lambdaQuery(User.class)
                        .eq(User::getUsername, username)
                        .eq(User::getDeleted, 0)
                        .ne(excludeId != null, User::getId, excludeId)
        );
        if (existing != null) {
            throw new ClientException("用户名已存在");
        }
    }

    private String normalizeRole(String role) {
        String value = StrUtil.trimToNull(role);
        if (value == null) {
            return USER_ROLE;
        }
        if (ADMIN_ROLE.equalsIgnoreCase(value)) {
            return ADMIN_ROLE;
        }
        if (USER_ROLE.equalsIgnoreCase(value)) {
            return USER_ROLE;
        }
        throw new ClientException("角色类型不合法");
    }

    private UserVO toVO(User record) {
        return UserVO.builder()
                .id(record.getId())
                .username(record.getUsername())
                .role(record.getRole())
                .avatar(record.getAvatar())
                .createTime(record.getCreateTime())
                .updateTime(record.getUpdateTime())
                .build();
    }
}
