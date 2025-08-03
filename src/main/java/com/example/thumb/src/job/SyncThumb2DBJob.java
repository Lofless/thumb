package com.example.thumb.src.job;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.date.DateTime;
import cn.hutool.core.date.DateUtil;
import java.util.Date;
import cn.hutool.core.text.StrPool;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.thumb.src.Util.RedisKeyUtil;
import com.example.thumb.src.domain.Thumb;
import com.example.thumb.src.mapper.BlogMapper;
import com.example.thumb.src.model.enums.ThumbTypeEnum;
import com.example.thumb.src.service.ThumbService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/**
 * 定时将 Redis 中的点赞记录同步到数据库中
 */
@Component
@Slf4j
public class SyncThumb2DBJob {

    private final RedisTemplate<String, Object> redisTemplate;
    private final ThumbService thumbService;
    private final BlogMapper blogMapper;

    public SyncThumb2DBJob(RedisTemplate<String, Object> redisTemplate, ThumbService thumbService, BlogMapper blogMapper) {
        this.redisTemplate = redisTemplate;
        this.thumbService = thumbService;
        this.blogMapper = blogMapper;
    }

    @Scheduled(fixedRate = 10000)
    @Transactional(rollbackFor = Exception.class)
    public void run(){
        log.info("定时任务开始执行");
        DateTime nowDate = DateUtil.date();
        // 如果秒数为0～9，那么就是上一分钟的50秒
        int second = (DateUtil.second(nowDate) / 10 - 1 ) * 10;
        if(second == -10){
            second = 50;
            nowDate = DateUtil.offsetMinute(nowDate, -1);
        }

        String date = DateUtil.format(nowDate, "HH:mm") + second;
        log.info("尝试同步时间点{}的数据到数据库", date);
        syncThumb2DBByDate(date);
        log.info("定时任务执行结束");
    }

    public void syncThumb2DBByDate(String date){
        // 根据时间戳获取临时点赞和取消点赞数据
        String tempThumbKey = RedisKeyUtil.getTempThumbKey(date);
        log.info("获取Redis键: {}", tempThumbKey);
        Map<Object, Object> allTempThumpMap = redisTemplate.opsForHash().entries(tempThumbKey);
        boolean thumbMapEmpty = CollUtil.isEmpty(allTempThumpMap);
        
        log.info("获取到{}条临时点赞数据", allTempThumpMap.size());

        if(thumbMapEmpty){
            log.info("没有需要同步的临时数据");
            return;
        }

        Map<Long, Long> blogThumbCountMap = new HashMap<>();
        // 向Thumb表中添加点赞数据
        ArrayList<Thumb> thumbList = new ArrayList<>();
        LambdaQueryWrapper<Thumb> wrapper = new LambdaQueryWrapper<>();
        boolean needRemove = false;
        for(Object userIdBlogIdObj : allTempThumpMap.keySet()){
            String userIdBlogId = String.valueOf(userIdBlogIdObj);
            // 分割，0是userId，1是BlogId
            String[] userIdAndBlogId = userIdBlogId.split(StrPool.COLON);
            Long userId = Long.valueOf(userIdAndBlogId[0]);
            Long BlogId = Long.valueOf(userIdAndBlogId[1]);
            // -1 取消点赞，1 点赞
            Integer thumbType = Integer.valueOf(allTempThumpMap.get(userIdBlogId).toString());
            if(thumbType == ThumbTypeEnum.INCR.getValue()){
                Thumb thumb = new Thumb();
                thumb.setUserid(userId);
                thumb.setBlogid(BlogId);
                thumb.setCreatetime(new Date());
                thumbList.add(thumb);
                log.info("准备添加点赞记录: 用户{}点赞博客{}", userId, BlogId);
            }else if(thumbType == ThumbTypeEnum.DECR.getValue()){
                needRemove = true;
                wrapper.or().eq(Thumb::getUserid, userId).eq(Thumb::getBlogid, BlogId);
                log.info("准备删除点赞记录: 用户{}取消点赞博客{}", userId, BlogId);
            }else{
                if(thumbType != ThumbTypeEnum.NON.getValue()){
                    log.warn("数据异常：{}",userId + "," + BlogId + "," + thumbType);
                }
                continue;
            }
            blogThumbCountMap.put(BlogId, blogThumbCountMap.getOrDefault(BlogId, 0L) + thumbType);
        }

        // 批量增加
        if (!thumbList.isEmpty()) {
            log.info("批量保存{}条点赞记录到数据库", thumbList.size());
            boolean saveResult = thumbService.saveBatch(thumbList);
            log.info("保存结果: {}", saveResult);
        }
        
        // 批量删除
        if(needRemove){
            log.info("批量删除取消点赞的记录");
            boolean removeResult = thumbService.remove(wrapper);
            log.info("删除结果: {}", removeResult);
        }
        
        // 批量更新博客点赞量
        if(!blogThumbCountMap.isEmpty()){
            log.info("批量更新{}个博客的点赞数", blogThumbCountMap.size());
            int updateCount = blogMapper.batchUpdateThumbCount(blogThumbCountMap);
            log.info("更新了{}个博客的点赞数", updateCount);
        }

        // 异步删除
        new Thread(()->{
            redisTemplate.delete(tempThumbKey);
            log.info("已删除Redis临时数据键: {}", tempThumbKey);
        }).start();
//        Thread.startVirualThread(() -> {
//           redisTemplate.delete(tempThumbKey);
//        });
    }
}
