package com.wzh.blog.service.impl;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.wzh.blog.dao.UserInfoDao;
import com.wzh.blog.entity.UserInfo;
import com.wzh.blog.media.AssetLifecycleService;
import com.wzh.blog.media.MediaAssetStore;
import com.wzh.blog.security.CurrentUser;
import com.wzh.blog.service.UserInfoService;
import com.wzh.blog.vo.UserInfoVO;
import com.wzh.blog.enums.FilePathEnum;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 管理员个人资料服务实现。
 */
@Service
public class UserInfoServiceImpl extends ServiceImpl<UserInfoDao, UserInfo> implements UserInfoService {

    private final UserInfoDao userInfoDao;
    private final MediaAssetStore mediaAssetStore;
    private final AssetLifecycleService assetLifecycleService;
    private final CurrentUser currentUser;

    public UserInfoServiceImpl(UserInfoDao userInfoDao,
                               MediaAssetStore mediaAssetStore,
                               AssetLifecycleService assetLifecycleService,
                               CurrentUser currentUser) {
        this.userInfoDao = userInfoDao;
        this.mediaAssetStore = mediaAssetStore;
        this.assetLifecycleService = assetLifecycleService;
        this.currentUser = currentUser;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateUserInfo(UserInfoVO userInfoVO) {
        userInfoDao.updateById(UserInfo.builder()
                .id(currentUser.id())
                .nickname(userInfoVO.getNickname())
                .intro(userInfoVO.getIntro())
                .webSite(userInfoVO.getWebSite())
                .build());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String updateUserAvatar(MultipartFile file) {
        Integer userInfoId = currentUser.id();
        UserInfo existingUser = userInfoDao.selectById(userInfoId);
        String previousAvatar = existingUser == null ? null : existingUser.getAvatar();
        String avatar = mediaAssetStore.upload(file, FilePathEnum.AVATAR.getPath());
        assetLifecycleService.deleteAfterRollback(avatar);
        userInfoDao.updateById(UserInfo.builder()
                .id(userInfoId)
                .avatar(avatar)
                .build());
        if (previousAvatar != null && !previousAvatar.equals(avatar)) {
            assetLifecycleService.deleteAfterCommit(List.of(previousAvatar));
        }
        return avatar;
    }
}
