package com.example.thumb.src.service;

import com.example.thumb.src.common.BaseResponse;
import com.example.thumb.src.domain.User;
import com.baomidou.mybatisplus.extension.service.IService;
import jakarta.servlet.http.HttpServletRequest;

/**
* @author yangguang
* @description 针对表【user】的数据库操作Service
* @createDate 2025-07-29 20:30:18
*/
public interface UserService extends IService<User> {
    User getLoginUser(HttpServletRequest request);
}
