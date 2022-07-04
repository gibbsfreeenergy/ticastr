package com.wzh.blog.dao;

import com.wzh.blog.entity.UserInfo;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.springframework.stereotype.Repository;


/**
 * 用户信息
 *
 * @author yezhiqiu
 * @date 2021/08/10
 */
@Repository
public interface UserInfoDao extends BaseMapper<UserInfo> {

}
