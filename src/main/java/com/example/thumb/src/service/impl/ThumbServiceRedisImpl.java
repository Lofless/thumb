package com.example.thumb.src.service.impl;

import cn.hutool.core.date.DateTime;
import cn.hutool.core.date.DateUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.thumb.src.Util.RedisKeyUtil;
import com.example.thumb.src.constant.RedisLuaScriptConstant;
import com.example.thumb.src.constant.ThumbConstant;
import com.example.thumb.src.domain.Blog;
import com.example.thumb.src.domain.Thumb;
import com.example.thumb.src.domain.User;
import com.example.thumb.src.mapper.ThumbMapper;
import com.example.thumb.src.model.dto.thumb.DoThumbRequest;
import com.example.thumb.src.model.enums.LuaStatusEnum;
import com.example.thumb.src.service.BlogService;
import com.example.thumb.src.service.ThumbService;
import com.example.thumb.src.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Arrays;

/**
* @author yangguang
* @description 针对表【thumb】的数据库操作Service实现
* @createDate 2025-07-29 20:30:18
*/
@Service("thumbServiceRedis")
@Slf4j
@RequiredArgsConstructor
public class ThumbServiceRedisImpl extends ServiceImpl<ThumbMapper, Thumb>
    implements ThumbService{

    private final UserService userService;
    private final RedisTemplate<String, Object> redisTemplate;

    /**
     * 点赞（Redis实现）
     * @param doThumbRequest
     * @param request
     * @return
     */
    @Override
    public Boolean doThumb(DoThumbRequest doThumbRequest, HttpServletRequest request) {
        if(doThumbRequest == null || doThumbRequest.getBlogId() == null){
            throw new RuntimeException("参数错误");
        }

        User loginUser = userService.getLoginUser(request);
        Long blogId = doThumbRequest.getBlogId();

        String timeSlice = getTimeSlice();
        // KEY
        String userThumbKey = RedisKeyUtil.getUserThumbKey(loginUser.getId());
        String tempThumbKey = RedisKeyUtil.getTempThumbKey(timeSlice);

        // 执行lua脚本
        Long result = redisTemplate.execute(
                RedisLuaScriptConstant.THUMB_SCRIPT,
                Arrays.asList(tempThumbKey, userThumbKey),
                loginUser.getId(),
                blogId
        );

        if(LuaStatusEnum.FAIL.getValue() == result){
            throw new RuntimeException("点赞失败");
        }

        // 更新成功才执行
        return LuaStatusEnum.SUCCESS.getValue() == result;
    }

    /**
     * 取消点赞
     * @param doThumbRequest
     * @param request
     * @return
     */
    @Override
    public Boolean undoThumb(DoThumbRequest doThumbRequest, HttpServletRequest request) {
        if(doThumbRequest == null || doThumbRequest.getBlogId() == null){
            throw new RuntimeException("参数错误");
        }
        User loginUser = userService.getLoginUser(request);

        Long blogId = doThumbRequest.getBlogId();
        // 计算时间切片
        String timeSlice = getTimeSlice();
        // RedisKey
        String userThumbKey = RedisKeyUtil.getUserThumbKey(loginUser.getId());
        String tempThumbKey = RedisKeyUtil.getTempThumbKey(timeSlice);

        // 执行Lua脚本
        Long result = redisTemplate.execute(
                RedisLuaScriptConstant.UNTHUMB_SCRIPT,
                Arrays.asList(tempThumbKey, userThumbKey),
                loginUser.getId(),
                blogId
        );

        if(LuaStatusEnum.FAIL.getValue() == result){
            throw new RuntimeException("用户未点赞");
        }

        return LuaStatusEnum.SUCCESS.getValue() == result;

    }

    private String getTimeSlice(){
        DateTime now = DateUtil.date();
        // 获取离当前时间最近的整数秒，如11:30:23，那么获取出来就是11:30:20
        return DateUtil.format(now,"HH:mm") + (DateUtil.second(now) / 10) * 10;
    }

    @Override
    public Boolean hasThumb(Long blogId, Long userId) {
        return redisTemplate.opsForHash().hasKey(ThumbConstant.USER_THUMB_KEY_PREFIX+userId.toString(), blogId.toString());
    }
}




