package com.example.thumb.src.mapper;

import com.example.thumb.src.domain.Blog;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Param;

import java.util.Map;

/**
* @author yangguang
* @description 针对表【blog】的数据库操作Mapper
* @createDate 2025-07-29 20:30:18
* @Entity generator.domain.Blog
*/
public interface BlogMapper extends BaseMapper<Blog> {
    int batchUpdateThumbCount(@Param("countMap") Map<Long, Long> countMap);
}
