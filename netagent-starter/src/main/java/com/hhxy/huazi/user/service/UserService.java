package com.hhxy.huazi.user.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.hhxy.huazi.user.controller.request.ChangePasswordRequest;
import com.hhxy.huazi.user.controller.request.UserCreateRequest;
import com.hhxy.huazi.user.controller.request.UserPageRequest;
import com.hhxy.huazi.user.controller.request.UserUpdateRequest;
import com.hhxy.huazi.user.controller.vo.UserVO;

public interface UserService {

    IPage<UserVO> pageQuery(UserPageRequest requestParam);

    String create(UserCreateRequest requestParam);

    void update(String id, UserUpdateRequest requestParam);

    void delete(String id);

    void changePassword(ChangePasswordRequest requestParam);
}
