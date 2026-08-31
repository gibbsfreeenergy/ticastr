package com.wzh.blog.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.wzh.blog.media.MediaAssetStore;
import com.wzh.blog.media.AssetLifecycleService;
import com.wzh.blog.vo.*;
import com.wzh.blog.dto.UserDetailDTO;
import com.wzh.blog.dto.UserOnlineDTO;
import com.wzh.blog.entity.UserInfo;
import com.wzh.blog.dao.UserInfoDao;
import com.wzh.blog.entity.UserRole;
import com.wzh.blog.enums.FilePathEnum;
import com.wzh.blog.exception.BizException;
import com.wzh.blog.service.RedisService;
import com.wzh.blog.service.UserInfoService;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.wzh.blog.service.UserRoleService;
import com.wzh.blog.security.CurrentUser;

import com.wzh.blog.vo.SearchQueryVO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

import static com.wzh.blog.constant.RedisPrefixConst.USER_CODE_KEY;


/**
 * 用户信息服务
 *
 * @author yezhiqiu
 * @date 2021/08/10
 */
@Service
public class UserInfoServiceImpl extends ServiceImpl<UserInfoDao, UserInfo> implements UserInfoService {

    private final UserInfoDao userInfoDao;
    private final UserRoleService userRoleService;
    private final com.wzh.blog.service.OnlineSessionService onlineSessionService;
    private final RedisService redisService;
    private final MediaAssetStore mediaAssetStore;
    private final AssetLifecycleService assetLifecycleService;
    private final CurrentUser currentUser;

    public UserInfoServiceImpl(UserInfoDao userInfoDao,
                               UserRoleService userRoleService,
                               com.wzh.blog.service.OnlineSessionService onlineSessionService,
                               RedisService redisService,
                               MediaAssetStore mediaAssetStore,
                               AssetLifecycleService assetLifecycleService,
                               CurrentUser currentUser) {
        this.userInfoDao = userInfoDao;
        this.userRoleService = userRoleService;
        this.onlineSessionService = onlineSessionService;
        this.redisService = redisService;
        this.mediaAssetStore = mediaAssetStore;
        this.assetLifecycleService = assetLifecycleService;
        this.currentUser = currentUser;
    }


    @Transactional(rollbackFor = Exception.class)



    @Override
    public void updateUserInfo(UserInfoVO userInfoVO) {
        // 封装用户信息
        UserInfo userInfo = UserInfo.builder()
                .id(currentUser.id())
                .nickname(userInfoVO.getNickname())
                .intro(userInfoVO.getIntro())
                .webSite(userInfoVO.getWebSite())
                .build();
        userInfoDao.updateById(userInfo);
    }

    @Transactional(rollbackFor = Exception.class)


    @Override
    public String updateUserAvatar(MultipartFile file) {
        Integer userInfoId = currentUser.id();
        UserInfo existingUser = userInfoDao.selectById(userInfoId);
        String previousAvatar = existingUser == null ? null : existingUser.getAvatar();
        // 头像上传
        String avatar = mediaAssetStore.upload(file, FilePathEnum.AVATAR.getPath());
        assetLifecycleService.deleteAfterRollback(avatar);
        // 更新用户信息
        UserInfo userInfo = UserInfo.builder()
                .id(userInfoId)
                .avatar(avatar)
                .build();
        userInfoDao.updateById(userInfo);
        if (previousAvatar != null && !previousAvatar.equals(avatar)) {
            assetLifecycleService.deleteAfterCommit(List.of(previousAvatar));
        }
        return avatar;
    }

    @Transactional(rollbackFor = Exception.class)


    @Override
    public void saveUserEmail(EmailVO emailVO) {
        String codeKey = USER_CODE_KEY + emailVO.getEmail();
        if (!Boolean.TRUE.equals(redisService.consumeIfEquals(codeKey, emailVO.getCode()))) {
            throw new BizException("验证码错误！");
        }
        UserInfo userInfo = UserInfo.builder()
                .id(currentUser.id())
                .email(emailVO.getEmail())
                .build();
        userInfoDao.updateById(userInfo);
    }

    @Transactional(rollbackFor = Exception.class)


    @Override
    public void updateUserRole(UserRoleVO userRoleVO) {
        // 更新用户角色和昵称
        UserInfo userInfo = UserInfo.builder()
                .id(userRoleVO.getUserInfoId())
                .nickname(userRoleVO.getNickname())
                .build();
        userInfoDao.updateById(userInfo);
        // 删除用户角色重新添加
        userRoleService.remove(new LambdaQueryWrapper<UserRole>()
                .eq(UserRole::getUserId, userRoleVO.getUserInfoId()));
        List<UserRole> userRoleList = userRoleVO.getRoleIdList().stream()
                .map(roleId -> UserRole.builder()
                        .roleId(roleId)
                        .userId(userRoleVO.getUserInfoId())
                        .build())
                .collect(Collectors.toList());
        userRoleService.saveBatch(userRoleList);
    }

    @Transactional(rollbackFor = Exception.class)


    @Override
    public void updateUserDisable(UserDisableVO userDisableVO) {
        // 更新用户禁用状态
        UserInfo userInfo = UserInfo.builder()
                .id(userDisableVO.getId())
                .isDisable(userDisableVO.getIsDisable())
                .build();
        userInfoDao.updateById(userInfo);
    }



    @Override
    public PageResult<UserOnlineDTO> listOnlineUsers(SearchQueryVO conditionVO, com.wzh.blog.web.PageQuery pageQuery) {
        List<UserOnlineDTO> userOnlineDTOList = onlineSessionService.list(conditionVO.getKeywords());
        // 执行分页
        int fromIndex = Math.min(Math.toIntExact(pageQuery.offset()), userOnlineDTOList.size());
        int size = Math.toIntExact(pageQuery.size());
        int toIndex = userOnlineDTOList.size() - fromIndex > size ? fromIndex + size : userOnlineDTOList.size();
        List<UserOnlineDTO> userOnlineList = userOnlineDTOList.subList(fromIndex, toIndex);
        return new PageResult<>(userOnlineList, userOnlineDTOList.size());
    }



    @Override
    public void removeOnlineUser(Integer userInfoId) {
        onlineSessionService.expireForUser(userInfoId);
    }

}
