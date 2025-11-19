package com.itheima.prize.commons.db.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.conditions.query.QueryChainWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.itheima.prize.commons.db.entity.CardUser;
import com.itheima.prize.commons.db.entity.CardUserDto;
import com.itheima.prize.commons.db.service.CardUserService;
import com.itheima.prize.commons.db.mapper.CardUserMapper;
import com.itheima.prize.commons.utils.ApiResult;
import com.itheima.prize.commons.utils.PasswordUtil;
import com.itheima.prize.commons.utils.RedisUtil;
import org.apache.commons.lang3.LocaleUtils;
import org.apache.logging.log4j.util.LoaderUtil;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.concurrent.TimeUnit;

import static com.itheima.prize.commons.config.RedisKeys.USERLOGINTIMES;

/**
* @author shawn
* @description 针对表【card_user(会员信息表)】的数据库操作Service实现
* @createDate 2023-12-26 11:58:48
*/
@Service
public class CardUserServiceImpl extends ServiceImpl<CardUserMapper, CardUser> implements CardUserService{

    @Autowired
    private RedisUtil redisUtil;


    @Override
    public ApiResult login(String account, String password, javax.servlet.http.HttpSession session) {


        String key=USERLOGINTIMES+account;

        if(account==null||account.isEmpty()||password==null||password.isEmpty()){
           return new ApiResult(0,"账号或密码错误",null);
       }
        //拿到当前错误次数
        Object num=redisUtil.get(key);
        if(num!=null){
            if((int)num>=5){
                redisUtil.expire(key,300);
                return new ApiResult(0,"账号错误五次，请五分钟后再登录",null);
            }
        }


       //查询用户
        CardUser user = this.lambdaQuery().select().eq(CardUser::getUname, account).last("limit 1").one();
        //加密密码，因为保存在user里面的密码也是加密过的，不加密无法比对
        String pw = PasswordUtil.encodePassword(password);
        if(user==null || user.getPasswd()==null || !user.getPasswd().equals(pw)){
            //redis原子自增（没有值的话或创建一个等于1（设置为二就会等于二，同时每次自增二））
            redisUtil.incr(key,1);
            return new ApiResult(0,"账号或密码错误",null);
        }
        user.setPasswd(null);   user.setIdcard(null);
        redisUtil.set(key,String.valueOf(0));//错误次数归零
        session.setAttribute("user",user);
        return new ApiResult(1,"登陆成功",user);

    }
}




