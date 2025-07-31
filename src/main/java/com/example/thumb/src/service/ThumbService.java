package com.example.thumb.src.service;

import com.example.thumb.model.dto.thumb.DoThumbRequest;
import com.example.thumb.src.domain.Thumb;
import com.baomidou.mybatisplus.extension.service.IService;
import jakarta.servlet.http.HttpServletRequest;

/**
* @author yangguang
* @description 针对表【thumb】的数据库操作Service
* @createDate 2025-07-29 20:30:18
*/
public interface ThumbService extends IService<Thumb> {

    // 点赞
    Boolean doThumb(DoThumbRequest doThumbRequest, HttpServletRequest request);

    // 取消点赞
    Boolean undoThumb(DoThumbRequest doThumbRequest, HttpServletRequest request);
}
