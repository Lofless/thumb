package com.example.thumb.src.controller;

import com.example.thumb.src.common.BaseResponse;
import com.example.thumb.src.common.ResultUtils;
import com.example.thumb.src.common.UserConstant;
import com.example.thumb.src.domain.User;
import com.example.thumb.src.service.UserService;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("user")
public class UserController {

    @Resource
    private UserService userService;

    // 登陆
    @GetMapping("/login")
    public BaseResponse<User> login(long userId, HttpServletRequest request){
        User user = userService.getById(userId);
        request.getSession().setAttribute(UserConstant.LOGIN_USER, user);
        return ResultUtils.success(user);
    }

    // 获取登陆用户
    @GetMapping("/get/login")
    public BaseResponse<User> getLoginUser(HttpServletRequest request){
        User loginUser = (User) request.getSession().getAttribute(UserConstant.LOGIN_USER);
        return ResultUtils.success(loginUser);
    }
}
