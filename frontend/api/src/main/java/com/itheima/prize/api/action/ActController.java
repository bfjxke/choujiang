package com.itheima.prize.api.action;

import com.alibaba.fastjson.JSON;
import com.itheima.prize.api.config.LuaScript;
import com.itheima.prize.commons.config.RabbitKeys;
import com.itheima.prize.commons.config.RedisKeys;
import com.itheima.prize.commons.db.entity.*;
import com.itheima.prize.commons.db.mapper.CardGameMapper;
import com.itheima.prize.commons.db.service.CardGameService;
import com.itheima.prize.commons.utils.ApiResult;
import com.itheima.prize.commons.utils.RedisUtil;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiImplicitParam;
import io.swagger.annotations.ApiImplicitParams;
import io.swagger.annotations.ApiOperation;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import java.text.SimpleDateFormat;
import java.util.*;

@RestController
@RequestMapping("/api/act")
@Api(tags = {"抽奖模块"})
public class ActController {

    @Autowired
    private RedisUtil redisUtil;
    @Autowired
    private RabbitTemplate rabbitTemplate;
    @Autowired
    private LuaScript luaScript;
    @Autowired
    private CardGameService cardGameService;

    @GetMapping("/go/{gameid}")
    @ApiOperation(value = "抽奖")
    @ApiImplicitParams({
            @ApiImplicitParam(name="gameid",value = "活动id",example = "1",required = true)
    })
    public ApiResult<Object> act(@PathVariable int gameid, HttpServletRequest request){
        Date now=new Date();
        CardGame game = cardGameService.getById(gameid);
        if(game==null|| game.getStarttime().after(now)){
            return new ApiResult<>(-1,"活动未开始",null);
        }
        if(now.after(game.getEndtime())){
            return new ApiResult<>(-1,"活动已结束",null);
        }
        HttpSession session = request.getSession();
        CardUser user =(CardUser) session.getAttribute("user");
        if(user==null){
            return new ApiResult<>(-1,"未登录",null);
        }
        if(redisUtil.setNx(RedisKeys.USERGAME+user.getId()+"_"+gameid,1)){
            CardUserGame userGame=new CardUserGame();
            userGame.setCreatetime(new Date());
            userGame.setUserid(user.getId());
            userGame.setGameid(gameid);
            rabbitTemplate.convertAndSend(RabbitKeys.EXCHANGE_DIRECT,RabbitKeys.QUEUE_PLAY,JSON.toJSONString(userGame));
        }


        //获取用户已抽奖次数
        Integer enter = (Integer) redisUtil.get(RedisKeys.MAXENTER + gameid + "_" + user.getId());
        if(enter==null){
            enter=0;
            redisUtil.set(RedisKeys.MAXENTER+gameid+"_"+user.getId(),enter,(game.getEndtime().getTime()-game.getStarttime().getTime())/1000);
        }
        //根据会员等级获取用户最大可抽奖数
        Integer maxenter =(Integer) redisUtil.hget(RedisKeys.MAXENTER + gameid, user.getLevel() + "");
        //若没设置则不限制次数 设为0
        maxenter=maxenter==null?0:maxenter;
        if(maxenter>0&&enter>=maxenter){
            return new ApiResult<>(-1,"您的抽奖次数已用完",null);
        }else{
            redisUtil.incr(RedisKeys.MAXENTER+gameid+"_"+user.getId(),1);
        }


        //用户已中奖次数
        Integer count = (Integer) redisUtil.get(RedisKeys.MAXGOAL + gameid + "_" + user.getId());
        if(count==null){
            count=0;
            redisUtil.set(RedisKeys.MAXGOAL+gameid+"_"+user.getId(),count,(game.getEndtime().getTime()-game.getStarttime().getTime())/1000);
        }
        //用户最大中奖数
        Integer maxcount=(Integer)redisUtil.get(RedisKeys.MAXGOAL+gameid+"_"+user.getId());
        maxcount=maxcount==null?0:maxcount;
        if(maxcount>0&&count>=maxcount){
            return new ApiResult<>(-1,"你的中奖次数已用完",null);
        }


        //全部校验完成开始拿token
        Long token;
        switch(game.getType()){
            case 1://看时间
//                token =(Long) redisUtil.leftPop(RedisKeys.TOKENS + gameid);
//                if(token==null){
//                    return new ApiResult<>(-1,"奖品已抽完，真慢！",null);
//                }
//                if(now.getTime()<token/1000){
//                    //未中奖
//                    redisUtil.leftPush(RedisKeys.TOKENS+gameid,token);
//                    return new ApiResult<>(-1,"未中奖，你运气有点差啊~",null);
//                }
                //通过lua
                token=luaScript.tokenCheck(RedisKeys.TOKENS+gameid,String.valueOf(new Date().getTime()));
                if(token==0){
                    return new ApiResult(-1,"奖品已抽光",null);
                }else if(token == 1){
                    return new ApiResult<>(-1,"未中奖，你运气有点差啊~",null);
                }

                break;


            case 2://拼手速
                token=(Long)redisUtil.leftPop(RedisKeys.TOKENS+gameid);
                if(token==null){
                    return new ApiResult<>(-1,"奖品已抽完，真慢！",null);
                }

                break;


            case 3://纯运气
                Integer randomRate =(Integer) redisUtil.hget(RedisKeys.RANDOMRATE + gameid, user.getLevel() + "");
                if(randomRate==null){
                    randomRate=100;
                }
                if(new Random().nextInt(100)>randomRate){
                    return new ApiResult<>(-1,"未中奖，你运气有点差啊~",null);
                }
                token=(Long)redisUtil.get(RedisKeys.TOKENS+gameid);
                if(token==null){
                    return new ApiResult<>(-1,"奖品已抽完，真慢！",null);
                }

                break;

            default:
                return new ApiResult<>(-1,"不支持此类型",null);
        }


        //到这里说明中奖了
        CardProduct product =(CardProduct) redisUtil.get(RedisKeys.TOKEN + gameid + "_" + token);
        redisUtil.incr(RedisKeys.USERHIT+gameid+"_"+user.getId(),1);

        //交给消息队列
        CardUserHit hit=new CardUserHit();
        hit.setGameid(gameid);
        hit.setHittime(now);
        hit.setProductid(product.getId());
        hit.setUserid(user.getId());
        rabbitTemplate.convertAndSend(RabbitKeys.EXCHANGE_DIRECT,RabbitKeys.QUEUE_HIT,JSON.toJSONString(hit));
        return new ApiResult<>(1,"恭喜中奖",product);
    }

    @GetMapping("/info/{gameid}")
    @ApiOperation(value = "缓存信息")
    @ApiImplicitParams({
            @ApiImplicitParam(name="gameid",value = "活动id",example = "1",required = true)
    })
    public ApiResult info(@PathVariable int gameid){
        //TODO
        return null;
    }
}
