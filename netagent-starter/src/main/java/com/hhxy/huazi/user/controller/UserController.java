package com.hhxy.huazi.user.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.hhxy.huazi.common.Result;
import com.hhxy.huazi.common.context.LoginInfo;
import com.hhxy.huazi.common.context.UserContext;
import com.hhxy.huazi.user.controller.request.ChangePasswordRequest;
import com.hhxy.huazi.user.controller.request.UserCreateRequest;
import com.hhxy.huazi.user.controller.request.UserPageRequest;
import com.hhxy.huazi.user.controller.request.UserUpdateRequest;
import com.hhxy.huazi.user.controller.vo.CurrentUserVO;
import com.hhxy.huazi.user.controller.vo.UserVO;
import com.hhxy.huazi.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    /**
     * 获取当前登录用户信息
     */
    @GetMapping("/user/me")
    public Result<CurrentUserVO> currentUser() {
        LoginInfo user = UserContext.requireUser();
        return Result.success(new CurrentUserVO(
                user.getUserId(),
                user.getUsername(),
                user.getRole(),
                user.getAvatar()));
    }

    /**
     * 分页查询用户列表
     */
    @GetMapping("/users")
    public Result<IPage<UserVO>> pageQuery(UserPageRequest requestParam) {
        StpUtil.checkRole("admin");
        return Result.success(userService.pageQuery(requestParam));
    }

    /**
     * 创建用户
     */
    @PostMapping("/users")
    public Result<String> create(@RequestBody UserCreateRequest requestParam) {
        StpUtil.checkRole("admin");
        return Result.success(userService.create(requestParam));
    }

    /**
     * 更新用户
     */
    @PutMapping("/users/{id}")
    public Result<Void> update(@PathVariable String id, @RequestBody UserUpdateRequest requestParam) {
        StpUtil.checkRole("admin");
        userService.update(id, requestParam);
        return Result.success();
    }

    /**
     * 删除用户
     */
    @DeleteMapping("/users/{id}")
    public Result<Void> delete(@PathVariable String id) {
        StpUtil.checkRole("admin");
        userService.delete(id);
        return Result.success();
    }

    /**
     * 修改当前用户密码
     */
    @PutMapping("/user/password")
    public Result<Void> changePassword(@RequestBody ChangePasswordRequest requestParam) {
        userService.changePassword(requestParam);
        return Result.success();
    }
}
