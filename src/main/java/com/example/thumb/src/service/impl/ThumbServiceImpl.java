package com.example.thumb.src.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.example.thumb.src.constant.ThumbConstant;
import com.example.thumb.src.manager.cache.CacheManager;
import com.example.thumb.src.model.dto.thumb.DoThumbRequest;
import com.example.thumb.src.domain.Blog;
import com.example.thumb.src.domain.Thumb;
import com.example.thumb.src.domain.User;
import com.example.thumb.src.service.BlogService;
import com.example.thumb.src.service.ThumbService;
import com.example.thumb.src.mapper.ThumbMapper;
import com.example.thumb.src.service.UserService;
import com.github.benmanes.caffeine.cache.Cache;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
* @author yangguang
* @description 针对表【thumb】的数据库操作Service实现
* @createDate 2025-07-29 20:30:18
*/
@Service("thumbService")
@Slf4j
@RequiredArgsConstructor
public class ThumbServiceImpl extends ServiceImpl<ThumbMapper, Thumb>
    implements ThumbService{

    private final UserService userService;
    private final TransactionTemplate transactionTemplate;
    private final BlogService blogService;
    private final RedisTemplate<String, Object> redisTemplate;
    private final CacheManager cacheManager;
    private final Cache cache;

    /**
     * 点赞
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
        // 加锁，使用用户的id来使用synchronized锁
        synchronized (loginUser.getId().toString().intern()){

            // 编程式事物
            return transactionTemplate.execute(status -> {
                /*
                要让锁的作用域完全包裹住事物，不然就会出现数据库数据不一致的情况
                如果A在锁释放后才提交事物，但是B在A提交事物前，B会获取到锁，因为先释放锁了，那么B获取到的数据库数据就是A未修改的，因为还未提交事务
                 */
                // 获取要点赞的帖子id
                Long blogId = doThumbRequest.getBlogId();
                // 判断数据库是否存在这条记录
                // redis三级缓存优化
                Boolean exists = this.hasThumb(blogId, loginUser.getId());
                /*boolean exists = this.lambdaQuery()
                        .eq(Thumb::getUserid, loginUser.getId())
                        .eq(Thumb::getBlogid, blogId)
                        .exists();*/

                log.info("exists的状态: {}", exists);
                // 已经点赞过了，那么throw出去
                if(exists){
                    throw new RuntimeException("用户已点赞");
                }

                // 要在blog表中添加上点赞数
                boolean update = blogService.lambdaUpdate()
                        .eq(Blog::getId, blogId)
                        .setSql("thumbcount = thumbcount + 1")
                        .update();

                Thumb thumb = new Thumb();
                thumb.setBlogid(blogId);
                thumb.setUserid(loginUser.getId());

                boolean success = update && this.save(thumb);
                // 如果数据库保存成功，那么就保存到redis缓存中
                if(success){
                    String hashKey = ThumbConstant.USER_THUMB_KEY_PREFIX + loginUser.getId();
                    String fieldKey = blogId.toString();
                    Long realThumbId = thumb.getId();
                    redisTemplate.opsForHash().put(hashKey, fieldKey, realThumbId);
                    cacheManager.putIfPresent(hashKey, fieldKey, realThumbId);
                }
                return success;

            });
        }
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
        synchronized(loginUser.getId().toString().intern()){
            return transactionTemplate.execute(status -> {
                Long blogId = doThumbRequest.getBlogId();

                // 从redis中获取点赞情况
                /*Object thumbObj = redisTemplate.opsForHash().get(ThumbConstant.USER_THUMB_KEY_PREFIX+loginUser.getId(), blogId.toString());*/
                Object thumbObj = cacheManager
                        .get(ThumbConstant.USER_THUMB_KEY_PREFIX+loginUser.getId(), blogId.toString());
                if(thumbObj == null || thumbObj.equals(ThumbConstant.UN_THUMB_CONSTANT)){
                    throw new RuntimeException("用户没有点赞");
                }

                // 从redis获取到了 value -> thumb中的 id
                Long thumbId = Long.valueOf(thumbObj.toString());

               /*Thumb thumb = this.lambdaQuery()
                       .eq(Thumb::getBlogid, blogId)
                       .eq(Thumb::getUserid, loginUser.getId())
                       .one();*/

               /*// 不存在这个点赞记录，直接throw
               if(thumb == null) {
                   throw new RuntimeException("用户没有点赞");
               }*/

                boolean update = blogService.lambdaUpdate()
                        .eq(Blog::getId, blogId)
                        .setSql("thumbcount = thumbcount - 1")
                        .update();

                boolean success = update && this.removeById(thumbId);
                // 删除redis中的数据
                if(success){
                    /*redisTemplate.opsForHash().delete(ThumbConstant.USER_THUMB_KEY_PREFIX+loginUser.getId(), blogId.toString());*/
                    String hashKey = ThumbConstant.USER_THUMB_KEY_PREFIX+loginUser.getId();
                    String fieldKey = blogId.toString();
                    redisTemplate.opsForHash().delete(hashKey, fieldKey);
                    cacheManager.putIfPresent(hashKey, fieldKey, ThumbConstant.UN_THUMB_CONSTANT);
                }

                return success;
            });
        }
    }

    @Override
    public Boolean hasThumb(Long blogId, Long userId) {
        Object obj = cacheManager.get(ThumbConstant.USER_THUMB_KEY_PREFIX+userId, blogId.toString());
        if(obj == null){
            return false;
        }
        Long thumbId = Long.valueOf(obj.toString());
        return !thumbId.equals(ThumbConstant.UN_THUMB_CONSTANT);
    }
}




