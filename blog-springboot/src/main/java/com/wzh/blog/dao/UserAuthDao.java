package com.wzh.blog.dao;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.wzh.blog.entity.UserAuth;
import org.springframework.stereotype.Repository;

/**
 * 管理员账号数据访问。
 */
@Repository
public interface UserAuthDao extends BaseMapper<UserAuth> {
}
