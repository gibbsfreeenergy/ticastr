package com.wzh.blog.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.wzh.blog.entity.UserAuth;
import com.wzh.blog.vo.PasswordVO;

/**
 * 管理员账号服务。
 */
public interface UserAuthService extends IService<UserAuth> {

    void updateAdminPassword(PasswordVO passwordVO);
}
