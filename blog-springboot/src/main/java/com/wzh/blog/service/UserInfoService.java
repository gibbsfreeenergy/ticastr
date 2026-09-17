package com.wzh.blog.service;

import com.baomidou.mybatisplus.spring.service.IService;
import com.wzh.blog.entity.UserInfo;
import com.wzh.blog.vo.UserInfoVO;
import org.springframework.web.multipart.MultipartFile;

/**
 * 管理员个人资料服务。
 */
public interface UserInfoService extends IService<UserInfo> {

    void updateUserInfo(UserInfoVO userInfoVO);

    String updateUserAvatar(MultipartFile file);
}
