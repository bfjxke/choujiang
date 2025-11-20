package com.itheima.prize.commons.db.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.itheima.prize.commons.db.entity.CardUser;
import com.itheima.prize.commons.db.entity.ViewCardUserHit;
import com.itheima.prize.commons.db.service.CardUserService;
import com.itheima.prize.commons.db.mapper.CardUserMapper;
import com.itheima.prize.commons.utils.ApiResult;
import com.itheima.prize.commons.utils.PageBean;
import com.itheima.prize.commons.utils.PasswordUtil;
import com.itheima.prize.commons.utils.RedisUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;


import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;

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
        Object num = redisUtil.get(key);
        long ttl = redisUtil.getExpire(key);
        int times = num == null ? 0 : (num instanceof Number ? ((Number) num).intValue() : Integer.parseInt(String.valueOf(num)));
        if (times >= 5 && ttl > 0) {
            return new ApiResult(0, "账号错误五次，请" + ttl + "秒后再重试", null);
        }
        if (num != null) {
            if (times >= 5) {
                redisUtil.expire(key, 300);
                return new ApiResult(0, "账号错误五次，请五分钟后再登录", null);
            }
        }


       //查询用户
        QueryWrapper<CardUser> queryWrapper = new QueryWrapper<>();
        //加密密码，因为保存在user里面的密码也是加密过的，不加密无法比对
        String pw = PasswordUtil.encodePassword(password);
        queryWrapper.eq("uname",account).last("limit 1");
        CardUser user = this.getBaseMapper().selectOne(queryWrapper);

        if(user==null || user.getPasswd()==null || !user.getPasswd().equals(pw)){
            //redis原子自增（没有值的话或创建一个等于1（设置为二就会等于二，同时每次自增二））
            redisUtil.incr(key, 1);
            Object v2 = redisUtil.get(key);
            int t2 = v2 == null ? 0 : (v2 instanceof Number ? ((Number) v2).intValue() : Integer.parseInt(String.valueOf(v2)));
            if (t2 == 1) {
                redisUtil.expire(key, 300);
            }
            return new ApiResult(0,"账号或密码错误",null);
        }
        user.setPasswd(null);   user.setIdcard(null);
        redisUtil.del(key);//删除键
        session.setAttribute("user",user);
        return new ApiResult(1,"登陆成功",user);

    }


}




