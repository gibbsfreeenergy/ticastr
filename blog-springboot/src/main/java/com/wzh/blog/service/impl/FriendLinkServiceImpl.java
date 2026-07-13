package com.wzh.blog.service.impl;

import jakarta.annotation.Resource;
import com.wzh.blog.web.PaginationContext;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.wzh.blog.dto.FriendLinkBackDTO;
import com.wzh.blog.dto.FriendLinkDTO;
import com.wzh.blog.vo.SearchQueryVO;
import com.wzh.blog.vo.PageResult;
import com.wzh.blog.entity.FriendLink;
import com.wzh.blog.dao.FriendLinkDao;
import com.wzh.blog.service.FriendLinkService;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.wzh.blog.util.BeanCopyUtils;
import com.wzh.blog.vo.FriendLinkVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 友情链接服务
 *
 * @author xiaojie
 * @date 2021/08/10
 */
@Service
public class FriendLinkServiceImpl extends ServiceImpl<FriendLinkDao, FriendLink> implements FriendLinkService {

    @Resource
    private PaginationContext paginationContext;
    @Autowired
    private FriendLinkDao friendLinkDao;




    @Override
    public List<FriendLinkDTO> listFriendLinks() {
        // 查询友链列表
        List<FriendLink> friendLinkList = friendLinkDao.selectList(null);
        return BeanCopyUtils.copyList(friendLinkList, FriendLinkDTO.class);
    }



    @Override
    public PageResult<FriendLinkBackDTO> listFriendLinkDTO(SearchQueryVO condition) {
        // 分页查询友链列表
        Page<FriendLink> page = new Page<>(paginationContext.getCurrent(), paginationContext.getSize());
        Page<FriendLink> friendLinkPage = friendLinkDao.selectPage(page, new LambdaQueryWrapper<FriendLink>()
                .like(StringUtils.isNotBlank(condition.getKeywords()), FriendLink::getLinkName, condition.getKeywords()));
        // 转换DTO
        List<FriendLinkBackDTO> friendLinkBackDTOList = BeanCopyUtils.copyList(friendLinkPage.getRecords(), FriendLinkBackDTO.class);
        return new PageResult<>(friendLinkBackDTOList, (int) friendLinkPage.getTotal());
    }

    @Transactional(rollbackFor = Exception.class)


    @Override
    public void saveOrUpdateFriendLink(FriendLinkVO friendLinkVO) {
        FriendLink friendLink = BeanCopyUtils.copyObject(friendLinkVO, FriendLink.class);
        this.saveOrUpdate(friendLink);
    }

}
