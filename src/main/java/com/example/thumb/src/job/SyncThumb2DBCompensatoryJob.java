package com.example.thumb.src.job;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.ObjUtil;
import com.example.thumb.src.Util.RedisKeyUtil;
import com.example.thumb.src.constant.ThumbConstant;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * 定时将 Redis 中的临时点赞数据同步到数据库的补偿措施
 */
@Component
@Slf4j
public class SyncThumb2DBCompensatoryJob {

    private final RedisTemplate<String, Object> redisTemplate;
    private final SyncThumb2DBJob syncThumb2DBJob;

    public SyncThumb2DBCompensatoryJob(RedisTemplate<String, Object> redisTemplate, SyncThumb2DBJob syncThumb2DBJob) {
        this.redisTemplate = redisTemplate;
        this.syncThumb2DBJob = syncThumb2DBJob;
    }

    @Scheduled(cron = "0 0 2 * * *")
    public void run(){
        log.info("开始补偿数据");
        Set<String> thumbKeys = redisTemplate.keys(RedisKeyUtil.getTempThumbKey("") + "*");
        Set<String> needHandleDateSet = new HashSet<>();
        thumbKeys.stream().filter(ObjUtil::isNotNull)
                .forEach(thumbKey -> {
                    needHandleDateSet.add(
                            thumbKey.replace(ThumbConstant.TEMP_THUMB_KEY_PREFIX.formatted(""),"")
                    );
                });

        if(CollUtil.isEmpty(needHandleDateSet)){
            log.info("没有需要补偿的临时数据");
            return;
        }

        for(String date : needHandleDateSet){
            syncThumb2DBJob.syncThumb2DBByDate(date);
        }
        log.info("临时数据补偿完成");
    }
}
