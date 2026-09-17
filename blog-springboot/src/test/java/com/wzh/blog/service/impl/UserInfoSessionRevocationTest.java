package com.wzh.blog.service.impl;

import com.wzh.blog.dao.UserAuthDao;
import com.wzh.blog.dao.UserInfoDao;
import com.wzh.blog.media.AssetLifecycleService;
import com.wzh.blog.media.MediaAssetStore;
import com.wzh.blog.security.AuthenticatedUserPrincipal;
import com.wzh.blog.security.CurrentUser;
import com.wzh.blog.service.OnlineSessionService;
import com.wzh.blog.service.RedisService;
import com.wzh.blog.service.UserRoleService;
import com.wzh.blog.vo.UserDisableVO;
import com.wzh.blog.vo.UserRoleVO;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.core.session.SessionRegistryImpl;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class UserInfoSessionRevocationTest {
    @ParameterizedTest
    @CsvSource({"true,true", "false,true", "true,false", "false,false"})
    @SuppressWarnings("unchecked")
    void roleChangesAndDisableRevokeExistingSessionsOnlyAfterCommit(boolean roleChange, boolean commit) {
        var registry = new SessionRegistryImpl();
        var principal = mock(AuthenticatedUserPrincipal.class);
        when(principal.getUserInfoId()).thenReturn(7);
        registry.registerNewSession("existing-session", principal);
        var sessions = new OnlineSessionService(mock(ObjectProvider.class), mock(UserAuthDao.class), registry);
        var service = new UserInfoServiceImpl(mock(UserInfoDao.class), mock(UserRoleService.class), sessions,
                mock(RedisService.class), mock(MediaAssetStore.class), mock(AssetLifecycleService.class),
                mock(CurrentUser.class));

        TransactionSynchronizationManager.initSynchronization();
        try {
            if (roleChange) {
                service.updateUserRole(UserRoleVO.builder().userInfoId(7).nickname("user").roleIdList(List.of(2)).build());
            } else {
                service.updateUserDisable(UserDisableVO.builder().id(7).isDisable(1).build());
            }
            assertThat(registry.getSessionInformation("existing-session").isExpired()).isFalse();
            if (commit) {
                TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);
            } else {
                TransactionSynchronizationManager.getSynchronizations().forEach(sync ->
                        sync.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));
            }
            assertThat(registry.getSessionInformation("existing-session").isExpired()).isEqualTo(commit);
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }
}
