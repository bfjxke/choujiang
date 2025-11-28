package com.itheima.prize.msg;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.itheima.prize.commons.config.RedisKeys;
import com.itheima.prize.commons.db.entity.*;
import com.itheima.prize.commons.db.service.CardGameProductService;
import com.itheima.prize.commons.db.service.CardGameRulesService;
import com.itheima.prize.commons.db.service.CardGameService;
import com.itheima.prize.commons.db.service.GameLoadService;
import com.itheima.prize.commons.utils.RedisUtil;
import io.swagger.models.auth.In;
import org.apache.commons.lang3.time.DateUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * 活动信息预热，每隔1分钟执行一次
 * 查找未来1分钟内（含），要开始的活动
 */
@Component
public class GameTask {
    private final static Logger log = LoggerFactory.getLogger(GameTask.class);
    @Autowired
    private CardGameService gameService;
    @Autowired
    private CardGameProductService gameProductService;
    @Autowired
    private CardGameRulesService gameRulesService;
    @Autowired
    private GameLoadService gameLoadService;
    @Autowired
    private RedisUtil redisUtil;

    @Scheduled(cron = "0 * * * * ?")
    public void execute() {
        System.out.printf("scheduled!"+new Date());
        //查询一分钟内的活动
        QueryWrapper<CardGame> queryWrapper = new QueryWrapper<>();
        Date now=  new Date();
        queryWrapper.gt("starttime",now);   queryWrapper.lt("starttime",DateUtils.addMinutes(now,1));
        List<CardGame> list = gameService.list(queryWrapper);

        //遍历活动
        for (CardGame cardGame : list) {
            long begin = cardGame.getStarttime().getTime();
            long end = cardGame.getEndtime().getTime();
            long duration =end-begin;
            long expire =(end-now.getTime())/1000;
            //活动基本信息
            HashMap queryMap = new HashMap<>();
            queryMap.put(cardGame.getId(),cardGame);
            redisUtil.set(RedisKeys.INFO+cardGame.getId(),cardGame);

            //奖品基本信息
            List<CardProductDto> productDtoList = gameLoadService.getByGameId(cardGame.getId());
            HashMap<Integer, CardProductDto> productDTOMap = new HashMap<>(productDtoList.size());
            for (CardProductDto productDTO : productDtoList) {
                productDTOMap.put(productDTO.getId(),productDTO);
            }
            log.info("load product type:{}",productDtoList.size());


            //奖品配置信息
            List<CardGameProduct>productsNumber = gameProductService.listByMap(queryMap);
            log.info("load bind product:{}",productsNumber.size());

            //令牌桶
            List<Long> tokenList=new ArrayList<>();
            for (CardGameProduct product : productsNumber) {
                for(int i=0;i<product.getAmount();i++){
                    Long rnd=begin+new Random().nextInt((int)duration);
                    Long token=rnd*1000+new Random().nextInt(1000);
                    tokenList.add(token);
                    log.info("token -> game : {} -> {}",token/1000 ,productDTOMap.get(product.getProductid()).getName());
                    redisUtil.set(RedisKeys.TOKEN+product.getGameid()+"_"+token,productDTOMap.get(product.getProductid()));
                }
            }
            Collections.sort(tokenList);
            log.info("load tokens:{}",tokenList);


            //从右侧压入队列
            redisUtil.rightPushAll(RedisKeys.TOKENS + cardGame.getId(),tokenList);
            redisUtil.expire(RedisKeys.TOKENS + cardGame.getId(),expire);

            //奖品配置策略
            List<CardGameRules> productRelus = gameRulesService.listByMap(queryMap);
            for (CardGameRules r : productRelus) {
                redisUtil.hset(RedisKeys.MAXGOAL+cardGame.getId(),r.getUserlevel()+"",r.getGoalTimes());
                redisUtil.hset(RedisKeys.MAXENTER+cardGame.getId(),r.getUserlevel()+"",r.getEnterTimes());
                redisUtil.hset(RedisKeys.RANDOMRATE+cardGame.getId(),r.getUserlevel()+"",r.getRandomRate());
                log.info("load rules:level={},enter={},goal={},rate={}",
                        r.getUserlevel(),r.getEnterTimes(),r.getGoalTimes(),r.getRandomRate());
            }
            redisUtil.expire(RedisKeys.RANDOMRATE+cardGame.getId(),expire);
            redisUtil.expire(RedisKeys.MAXENTER+cardGame.getId(),expire);
            redisUtil.expire(RedisKeys.MAXGOAL+cardGame.getId(),expire);

            //活动状态变更为已预热，禁止管理后台再随便变动
            cardGame.setStatus(1);
            gameService.updateById(cardGame);

        }
    }
}
