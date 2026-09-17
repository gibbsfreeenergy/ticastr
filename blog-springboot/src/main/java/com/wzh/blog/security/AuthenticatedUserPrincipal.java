package com.wzh.blog.security;

import com.alibaba.fastjson2.annotation.JSONField;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.wzh.blog.dto.UserDetailDTO;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static com.wzh.blog.constant.CommonConst.FALSE;

/**
 * 登录后的无密码会话主体。
 */
public class AuthenticatedUserPrincipal implements UserDetails {

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
    private Integer isDisable;

    protected AuthenticatedUserPrincipal() {
    }

    public AuthenticatedUserPrincipal(Integer id, Integer userInfoId, String email, Integer loginType,
                                      String username, List<String> roleList, String nickname, String avatar,
                                      String intro, String webSite, Integer isDisable) {
        this.id = id;
        this.userInfoId = userInfoId;
        this.email = email;
        this.loginType = loginType;
        this.username = username;
        this.roleList = roleList;
        this.nickname = nickname;
        this.avatar = avatar;
        this.intro = intro;
        this.webSite = webSite;
        this.isDisable = isDisable;
    }

    public static AuthenticatedUserPrincipal from(UserDetailDTO user) {
        return new AuthenticatedUserPrincipal(
                user.getId(), user.getUserInfoId(), user.getEmail(), user.getLoginType(),
                user.getUsername(), user.getRoleList(), user.getNickname(), user.getAvatar(),
                user.getIntro(), user.getWebSite(), user.getIsDisable());
    }

    public Integer getId() {
        return id;
    }

    public Integer getUserInfoId() {
        return userInfoId;
    }

    public String getEmail() {
        return email;
    }

    public Integer getLoginType() {
        return loginType;
    }

    @Override
    public String getUsername() {
        return username;
    }

    public List<String> getRoleList() {
        return roleList;
    }

    public String getNickname() {
        return nickname;
    }

    public String getAvatar() {
        return avatar;
    }

    public String getIntro() {
        return intro;
    }

    public String getWebSite() {
        return webSite;
    }

    public Integer getIsDisable() {
        return isDisable;
    }

    public boolean disabled() {
        return !Integer.valueOf(FALSE).equals(isDisable);
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return Optional.ofNullable(roleList).orElseGet(List::of).stream()
                .map(SimpleGrantedAuthority::new)
                .collect(Collectors.toUnmodifiableSet());
    }

    @Override
    @JsonIgnore
    @JSONField(serialize = false)
    public String getPassword() {
        return null;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return !disabled();
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return !disabled();
    }
}
