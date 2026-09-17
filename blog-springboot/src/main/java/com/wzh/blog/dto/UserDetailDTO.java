package com.wzh.blog.dto;

import com.alibaba.fastjson2.annotation.JSONField;
import com.wzh.blog.security.AuthenticatedUserPrincipal;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import org.springframework.security.core.GrantedAuthority;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static com.wzh.blog.constant.CommonConst.FALSE;

/**
 * 仅在密码校验阶段使用的用户详情。
 */
@Data
@Builder
@EqualsAndHashCode(callSuper = false)
public class UserDetailDTO extends AuthenticatedUserPrincipal {

    private Integer id;
    private Integer userInfoId;
    private String email;
    private Integer loginType;
    private String username;

    @JSONField(serialize = false, deserialize = false)
    private transient String password;

    private List<String> roleList;
    private String nickname;
    private String avatar;
    private String intro;
    private String webSite;
    private Integer isDisable;

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return Optional.ofNullable(roleList).orElseGet(List::of).stream()
                .map(org.springframework.security.core.authority.SimpleGrantedAuthority::new)
                .collect(Collectors.toUnmodifiableSet());
    }

    @Override
    public String getPassword() {
        return password;
    }

    public void eraseCredentials() {
        password = null;
    }

    @Override
    public boolean isAccountNonLocked() {
        return Integer.valueOf(FALSE).equals(isDisable);
    }

    @Override
    public boolean isEnabled() {
        return true;
    }
}
