package com.example.thumb.src.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.ObjUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.thumb.src.constant.ThumbConstant;
import com.example.thumb.src.model.vo.BlogVO;
import com.example.thumb.src.domain.Blog;
import com.example.thumb.src.domain.Thumb;
import com.example.thumb.src.domain.User;
import com.example.thumb.src.service.BlogService;
import com.example.thumb.src.mapper.BlogMapper;
import com.example.thumb.src.service.ThumbService;
import com.example.thumb.src.service.UserService;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
* @author yangguang
* @description 针对表【blog】的数据库操作Service实现
* @createDate 2025-07-29 20:30:18
*/
@Service
@Slf4j
public class BlogServiceImpl extends ServiceImpl<BlogMapper, Blog>
    implements BlogService{

    @Resource
    private UserService userService;

    @Resource
    @Lazy
    private ThumbService thumbService;
    @Autowired
    private RedisTemplate<String, Object> redisTemplate;


    /**
     * 根据博客ID获取博客VO对象
     * @param blogId 博客ID
     * @param request HTTP请求对象，用于获取当前登录用户信息
     * @return 博客VO对象，包含博客信息和用户点赞状态
     */
    @Override
    public BlogVO getBlogVOById(long blogId, HttpServletRequest request) {
        Blog blog = this.getById(blogId);
        User loginUser = userService.getLoginUser(request);
        return this.getBlogVO(blog, loginUser);
    }

    /**
     * 获取博客VO对象
     * @param blog 博客对象
     * @param loginUser 当前登录用户对象
     * @return 博客VO对象，包含博客信息和用户点赞状态
     */
    private BlogVO getBlogVO(Blog blog, User loginUser){
        BlogVO blogVO = new BlogVO();
        BeanUtil.copyProperties(blog, blogVO);

        if(loginUser == null){
            return blogVO;
        }
        // 查询是否点赞，如果点赞那么就可以通过点赞表查询到
        // 使用redis优化
        boolean exists = thumbService.hasThumb(blog.getId(), loginUser.getId());
        blogVO.setHasThumb(exists);
        /*Thumb thumb = thumbService.lambdaQuery()
                .eq(Thumb::getUserid, loginUser.getId())
                .eq(Thumb::getBlogid, blog.getId())
                .one();

        blogVO.setHasThumb(thumb != null);*/
        /*log.info("blog.coverImg: {}", blogVO.getCoverimg());*/
        return blogVO;
    }

    /**
     * 将博客列表转换为博客VO列表，并设置当前用户对各博客的点赞状态
     * @param blogList 博客列表
     * @param request HTTP请求对象，用于获取当前登录用户信息
     * @return 博客VO列表，包含博客信息和当前用户点赞状态
     */
    @Override
    public List<BlogVO> getBlogVOList(List<Blog> blogList, HttpServletRequest request) {
        // 获取登陆用户的个人信息
        User loginUser = userService.getLoginUser(request);
        // 获取点赞状态
        Map<Long,Boolean> blogIdHasThumbMap = new HashMap<>();

        // 判断当前用户是否为空
        if(ObjUtil.isNotEmpty(loginUser)){
            // 获取当前用户点赞的博客ID存入到Set集合中
            // redis优化
            List<Object> blogIdList = blogList.stream().map(Blog::getId).collect(Collectors.toList());
            /*Set<Long> blogIdSet = blogList.stream().map(Blog::getId).collect(Collectors.toSet());*/

            // 从数据库中根据blogId和userId来查询出user对这些blog的点赞情况
            // redis优化
            List<String> blogIdStringList = blogIdList.stream()
                    .map(Object::toString)
                    .collect(Collectors.toList());
            List<Object> thumbList = redisTemplate.opsForHash().multiGet(ThumbConstant.USER_THUMB_KEY_PREFIX+loginUser.getId().toString(), (Collection<Object>)(Collection<?>)blogIdStringList);
            for(int i = 0 ; i < blogIdList.size(); i ++ ){
                if(blogIdList.get(i) == null){
                    continue;
                }
                blogIdHasThumbMap.put(Long.valueOf(blogIdList.get(i).toString()), true);
            }
            /*List<Thumb> thumbList = thumbService.lambdaQuery()
                    .in(Thumb::getBlogid, blogIdSet)
                    .eq(Thumb::getUserid, loginUser.getId())
                    .list();*/

            // 获取thumbList中的数据，逐条处理，映射到map中，blogThumb指的是thumbList中的单条数据
            /*thumbList.forEach(blogThumb -> blogIdHasThumbMap.put(blogThumb.getBlogid(), true));*/
        }

        // 创建一个空的BlogVO列表
        List<BlogVO> blogVOList = new ArrayList<>();

        // 遍历每篇博客
        for (Blog blog : blogList) {
            // 创建BlogVO对象并复制属性
            BlogVO blogVO = new BlogVO();
            BeanUtil.copyProperties(blog, blogVO);

            // 设置点赞状态
            Boolean hasThumb = blogIdHasThumbMap.get(blog.getId());
            blogVO.setHasThumb(hasThumb);

            // 将BlogVO添加到列表中
            blogVOList.add(blogVO);
        }

        // 返回BlogVO列表
        return blogVOList;


        // 作用同上
//        return blogList.stream()
//                .map(blog -> {
//                    BlogVO blogVO = BeanUtil.copyProperties(blog, BlogVO.class);
//                    blogVO.setHasThumb(blogIdHasThumbMap.get(blog.getId()));
//                    return blogVO;
//                })
//                .toList();
    }
}




