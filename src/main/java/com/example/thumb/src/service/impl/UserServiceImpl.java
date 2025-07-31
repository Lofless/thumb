package com.example.thumb.src.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.thumb.src.common.UserConstant;
import com.example.thumb.src.domain.User;
import com.example.thumb.src.service.UserService;
import com.example.thumb.src.mapper.UserMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;

/**
* @author yangguang
* @description 针对表【user】的数据库操作Service实现
* @createDate 2025-07-29 20:30:18
*/
@Service
public class UserServiceImpl extends ServiceImpl<UserMapper, User>
    implements UserService{

    @Override
    public User getLoginUser(HttpServletRequest request){
        return (User) request.getSession().getAttribute(UserConstant.LOGIN_USER);
    }
}




