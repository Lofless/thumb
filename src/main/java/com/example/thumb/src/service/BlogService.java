package com.example.thumb.src.service;

import com.example.thumb.src.model.vo.BlogVO;
import com.example.thumb.src.domain.Blog;
import com.baomidou.mybatisplus.extension.service.IService;
import jakarta.servlet.http.HttpServletRequest;

import java.util.List;

/**
* @author yangguang
* @description 针对表【blog】的数据库操作Service
* @createDate 2025-07-29 20:30:18
*/
public interface BlogService extends IService<Blog> {

    // 获取博客，包含用户点赞状态
    BlogVO getBlogVOById(long blogId, HttpServletRequest request);

    List<BlogVO> getBlogVOList(List<Blog> blogList, HttpServletRequest request);
}
