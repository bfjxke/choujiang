package com.itheima.prize.commons.db.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.itheima.prize.commons.db.entity.CardUser;
import com.itheima.prize.commons.db.entity.ViewCardUserHit;
import com.itheima.prize.commons.db.service.ViewCardUserHitService;
import com.itheima.prize.commons.db.mapper.ViewCardUserHitMapper;
import com.itheima.prize.commons.utils.PageBean;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;

/**
* @author shawn
* @description 针对表【view_card_user_hit】的数据库操作Service实现
* @createDate 2023-12-26 11:58:48
*/
@Service
public class ViewCardUserHitServiceImpl extends ServiceImpl<ViewCardUserHitMapper, ViewCardUserHit>
    implements ViewCardUserHitService{


    /**
     * 查询我的获奖
     */
    @Override
    public PageBean<ViewCardUserHit> jiang(int gameid, int curpage, int limit, HttpServletRequest request) {

        HttpSession session = request.getSession();
        CardUser user = (CardUser)session.getAttribute("user");
        QueryWrapper<ViewCardUserHit> queryWrapper=new QueryWrapper<>();
        queryWrapper.eq("userid",user.getId());
        if(gameid!=-1){
            queryWrapper.eq("gameid",gameid);
        }
        queryWrapper.orderByDesc("hittime");

        Page<ViewCardUserHit> p=new Page<>(curpage,limit);
        Page<ViewCardUserHit> result=this.page(p,queryWrapper);

        return new PageBean<ViewCardUserHit>(result);
    }


    //查询中奖列表
    @Override
    public PageBean<ViewCardUserHit> hit(int gameid, int curpage, int limit) {
        QueryWrapper<ViewCardUserHit> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("gameid",gameid);
        Page<ViewCardUserHit> p=this.page(new Page<>(curpage,limit),queryWrapper);
        return new PageBean<ViewCardUserHit>(p);
    }
}




