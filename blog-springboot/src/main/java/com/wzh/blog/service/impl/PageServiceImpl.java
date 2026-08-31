package com.wzh.blog.service.impl;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.wzh.blog.dao.PageDao;
import com.wzh.blog.entity.Page;
import com.wzh.blog.infrastructure.cache.CacheKeyFactory;
import com.wzh.blog.infrastructure.cache.CacheStore;
import com.wzh.blog.service.PageService;
import com.wzh.blog.util.BeanCopyUtils;
import com.wzh.blog.vo.PageVO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


import java.util.List;
import java.time.Duration;

/**
 * 页面服务
 *
 * @author yezhiqiu
 * @date 2021/08/07
 */
@Service
public class PageServiceImpl extends ServiceImpl<PageDao, Page> implements PageService {
    private final CacheStore cacheStore;
    private final CacheKeyFactory cacheKeyFactory;
    private final PageDao pageDao;

    public PageServiceImpl(CacheStore cacheStore, PageDao pageDao) {
        this.cacheStore = cacheStore;
        this.cacheKeyFactory = new CacheKeyFactory();
        this.pageDao = pageDao;
    }

    @Transactional(rollbackFor = Exception.class)



    @Override
    public void saveOrUpdatePage(PageVO pageVO) {
        Page page = BeanCopyUtils.copyObject(pageVO, Page.class);
        this.saveOrUpdate(page);
        // 删除缓存
        cacheStore.evict(cacheKeyFactory.pageCover());
    }

    @Transactional(rollbackFor = Exception.class)


    @Override
    public void deletePage(Integer pageId) {
        pageDao.deleteById(pageId);
        // 删除缓存
        cacheStore.evict(cacheKeyFactory.pageCover());
    }

    @Transactional(rollbackFor = Exception.class)


    @Override
    public List<PageVO> listPages() {
        List<PageVO> pageVOList;
        // 查找缓存信息，不存在则从mysql读取，更新缓存
        Object pageList = cacheStore.get(cacheKeyFactory.pageCover());
        if (pageList instanceof List<?> cached) {
            pageVOList = (List<PageVO>) cached;
        } else {
            pageVOList = BeanCopyUtils.copyList(pageDao.selectList(null), PageVO.class);
            cacheStore.put(cacheKeyFactory.pageCover(), pageVOList, Duration.ofMinutes(5));
        }
        return pageVOList;
    }

}




