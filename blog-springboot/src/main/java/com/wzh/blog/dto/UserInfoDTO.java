package com.wzh.blog.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 管理后台登录响应。
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class UserInfoDTO {

    private Integer id;
    private Integer userInfoId;
    private String email;
    private Integer loginType;
    private String username;
    private List<String> roleList;
    private String nickname;
    private String avatar;
    private String intro;
    private String webSite;
}
