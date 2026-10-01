package com.hhxy.huazi.user.config;

import cn.dev33.satoken.model.wrapperInfo.SaDisableWrapperInfo;
import cn.dev33.satoken.stp.StpInterface;
import cn.hutool.core.util.StrUtil;
import com.hhxy.huazi.user.entity.User;
import com.hhxy.huazi.user.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
/**
 * Sa-Token 权限认证接口实现类
 * 用于实现 Sa-Token 框架的权限和角色验证逻辑
 */
@Component
@RequiredArgsConstructor
public class SaTokenStpInterfaceImpl implements StpInterface {

    private final UserMapper userMapper;
    /**
     * 获取用户权限列表
     *
     * @param loginId   登录用户ID
     * @param loginType 登录类型
     * @return 权限列表（当前实现返回空列表）
     */

    @Override
    public List<String> getPermissionList(Object loginId, String loginType) {
        return Collections.emptyList();
    }

    /**
     * 获取用户角色列表
     *
     * @param loginId   登录用户ID
     * @param loginType 登录类型
     * @return 角色列表（当前实现返回空列表）
     */
    @Override
    public List<String> getRoleList(Object loginId, String loginType) {
        if (loginId == null) {
            return Collections.emptyList();
        }

        String loginIdStr = loginId.toString();
        if (!StrUtil.isNumeric(loginIdStr)) {
            return Collections.emptyList();
        }

        User user = userMapper.selectById(loginIdStr);
        if (user == null || StrUtil.isBlank(user.getRole())) {
            return Collections.emptyList();
        }

        return List.of(user.getRole());
    }
}
