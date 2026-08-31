package com.wzh.blog.security;

import com.wzh.blog.dto.UserDetailDTO;
import com.alibaba.fastjson2.annotation.JSONField;
import com.fasterxml.jackson.annotation.JsonIgnore;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static com.wzh.blog.constant.CommonConst.FALSE;

/**
 * Password-free principal persisted in the authenticated session.
 * Password verification is performed before this object is created.
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
    private Set<Object> articleLikeSet;
    private Set<Object> commentLikeSet;
    private Set<Object> talkLikeSet;
    private String ipAddress;
    private String ipSource;
    private Integer isDisable;
    private String browser;
    private String os;
    private LocalDateTime lastLoginTime;

    protected AuthenticatedUserPrincipal() {
    }

    public AuthenticatedUserPrincipal(Integer id, Integer userInfoId, String email, Integer loginType,
                                      String username, List<String> roleList, String nickname, String avatar,
                                      String intro, String webSite, Set<Object> articleLikeSet,
                                      Set<Object> commentLikeSet, Set<Object> talkLikeSet, String ipAddress,
                                      String ipSource, Integer isDisable, String browser, String os,
                                      LocalDateTime lastLoginTime) {
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
        this.articleLikeSet = articleLikeSet;
        this.commentLikeSet = commentLikeSet;
        this.talkLikeSet = talkLikeSet;
        this.ipAddress = ipAddress;
        this.ipSource = ipSource;
        this.isDisable = isDisable;
        this.browser = browser;
        this.os = os;
        this.lastLoginTime = lastLoginTime;
    }

    public static AuthenticatedUserPrincipal from(UserDetailDTO user) {
        return new AuthenticatedUserPrincipal(
                user.getId(), user.getUserInfoId(), user.getEmail(), user.getLoginType(),
                user.getUsername(), user.getRoleList(), user.getNickname(), user.getAvatar(),
                user.getIntro(), user.getWebSite(), user.getArticleLikeSet(), user.getCommentLikeSet(),
                user.getTalkLikeSet(), user.getIpAddress(), user.getIpSource(), user.getIsDisable(),
                user.getBrowser(), user.getOs(), user.getLastLoginTime());
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

    public Set<Object> getArticleLikeSet() {
        return articleLikeSet;
    }

    public Set<Object> getCommentLikeSet() {
        return commentLikeSet;
    }

    public Set<Object> getTalkLikeSet() {
        return talkLikeSet;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public String getIpSource() {
        return ipSource;
    }

    public Integer getIsDisable() {
        return isDisable;
    }

    public String getBrowser() {
        return browser;
    }

    public String getOs() {
        return os;
    }

    public LocalDateTime getLastLoginTime() {
        return lastLoginTime;
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

    /** Deliberately returns no credential; this principal is never used for password verification. */
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
