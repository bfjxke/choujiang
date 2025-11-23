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
        QueryWrapper<CardGame> gameQueryWrapper = new QueryWrapper<>();
        Date now=new Date();
        gameQueryWrapper.gt("starttime",now);  gameQueryWrapper.le("starttime",DateUtils.addMinutes(now,1));
        List<CardGame> list = gameService.list(gameQueryWrapper);

        if(list==null||list.size()==0){
            log.info("game list scan : size = 0");
            return;
        }
        log.info("game list scan : size = {}",list.size());
        //遍历列表处理
        for (CardGame game : list) {

            long begin = game.getStarttime().getTime();
            long end = game.getEndtime().getTime();
            //活动总时间
            long duration = end - begin;
            //过期时间（活动剩余时间）
            long expire = (end - now.getTime()) / 1000;
            Map queryMap = new HashMap();
            queryMap.put("gameid",game.getId());

            //活动基本信息
            game.setStatus(1);
            redisUtil.set(RedisKeys.INFO+game.getId(),game,-1);
            log.info("load game info:{}{}{}{}",game.getId(),game.getTitle(),game.getStarttime(),game.getEndtime());

            //奖品基本信息
            List<CardProductDto> products = gameLoadService.getByGameId(game.getId());
            HashMap<Integer, CardProductDto> productsMap = new HashMap<>(products.size());
            for (CardProductDto p : products) {
                productsMap.put(p.getId(),p);
            }
            log.info("load product type:{}",products.size());

            //奖品数量等配置信息
            List<CardGameProduct> gameProducts = gameProductService.listByMap(queryMap);
            log.info("load bind product:{}",gameProducts.size());

            //令牌桶
            List<Long> tokenList = new ArrayList();
            for (CardGameProduct product : gameProducts) {
                for(int i=0;i<product.getAmount();i++){
                    long rnd = begin + new Random().nextInt((int) duration);
                    Long token=rnd*1000+new Random().nextInt(1000);
                    tokenList.add(token);
                    log.info("token -> game : {} -> {}",token/1000 ,productsMap.get(product.getProductid()).getName());
                    redisUtil.set(RedisKeys.TOKEN+product.getGameid()+"_"+token,productsMap.get(product.getProductid()),expire);
                }
            }
            Collections.sort(tokenList);
            log.info("load tkoens:{}",tokenList);

            //从右侧压入队列
            redisUtil.rightPushAll(RedisKeys.TOKENS + game.getId(),tokenList);
            redisUtil.expire(RedisKeys.TOKENS + game.getId(),expire);

            List<CardGameRules> productRelus = gameRulesService.listByMap(queryMap);
            for (CardGameRules r : productRelus) {
                redisUtil.hset(RedisKeys.MAXGOAL+game.getId(),r.getUserlevel()+"",r.getGoalTimes());
                redisUtil.hset(RedisKeys.MAXENTER+game.getId(),r.getUserlevel()+"",r.getEnterTimes());
                redisUtil.hset(RedisKeys.RANDOMRATE+game.getId(),r.getUserlevel()+"",r.getRandomRate());
                log.info("load rules:level={},enter={},goal={},rate={}",
                        r.getUserlevel(),r.getEnterTimes(),r.getGoalTimes(),r.getRandomRate());
            }
            redisUtil.expire(RedisKeys.RANDOMRATE+game.getId(),expire);
            redisUtil.expire(RedisKeys.MAXENTER+game.getId(),expire);
            redisUtil.expire(RedisKeys.MAXGOAL+game.getId(),expire);

            game.setStatus(1);
            gameService.updateById(game);

        }
}}
