package com.itheima.prize.commons.db.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.itheima.prize.commons.db.entity.CardGame;
import com.itheima.prize.commons.db.service.CardGameService;
import com.itheima.prize.commons.db.mapper.CardGameMapper;
import com.itheima.prize.commons.utils.PageBean;
import org.springframework.stereotype.Service;

/**
* @author shawn
* @description 针对表【card_game】的数据库操作Service实现
* @createDate 2023-12-26 11:58:48
*/
@Service
public class CardGameServiceImpl extends ServiceImpl<CardGameMapper, CardGame>
    implements CardGameService{


    /**
     * 返回活动列表
     **/
    @Override
    public PageBean<CardGame> list(int status, int curpage, int limit) {
        QueryWrapper<CardGame> queryWrapper=new QueryWrapper<>();
        if(status!=-1){
            queryWrapper.eq("status",status);
        }
        queryWrapper.orderByDesc("endtime");
        Page<CardGame> p=new Page<>(curpage,limit);
        Page result=this.page(p,queryWrapper);

        return new PageBean<>(result) ;
    }
}




