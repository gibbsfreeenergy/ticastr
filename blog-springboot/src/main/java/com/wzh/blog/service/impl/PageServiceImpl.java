package com.wzh.blog.service.impl;

import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.wzh.blog.dao.PageDao;
import com.wzh.blog.entity.Page;
import com.wzh.blog.exception.BizException;
import com.wzh.blog.exception.NotFoundException;
import com.wzh.blog.infrastructure.cache.CacheKeyFactory;
import com.wzh.blog.infrastructure.cache.CacheStore;
import com.wzh.blog.service.PageService;
import com.wzh.blog.util.BeanCopyUtils;
import com.wzh.blog.vo.PageVO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.List;
import java.util.Set;

/**
 * 页面服务
 *
 * @author yezhiqiu
 * @date 2021/08/07
 */
@Service
public class PageServiceImpl extends ServiceImpl<PageDao, Page> implements PageService {
    private static final List<String> CORE_PAGE_LABELS = List.of("home", "archive", "about");
    private static final Set<String> CORE_PAGE_LABEL_SET = Set.copyOf(CORE_PAGE_LABELS);

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
        if (pageVO == null) {
            throw new BizException("页面信息不能为空");
        }
        String pageLabel = pageVO.getPageLabel() == null ? "" : pageVO.getPageLabel().trim();
        if (!CORE_PAGE_LABEL_SET.contains(pageLabel)) {
            throw new BizException("页面标签只能是 home、archive 或 about");
        }

        Page existingPage;
        if (pageVO.getId() == null) {
            existingPage = pageDao.selectOne(new LambdaQueryWrapper<Page>()
                    .eq(Page::getPageLabel, pageLabel));
            if (existingPage != null) {
                throw new BizException("该核心页面已经存在");
            }
        } else {
            existingPage = pageDao.selectById(pageVO.getId());
            if (existingPage == null) {
                throw new NotFoundException("页面不存在");
            }
            if (!pageLabel.equals(existingPage.getPageLabel())) {
                throw new BizException("页面标签不可修改");
            }
        }

        Page page = BeanCopyUtils.copyObject(pageVO, Page.class);
        page.setPageLabel(pageLabel);
        this.saveOrUpdate(page);
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
            pageVOList = cached.stream()
                    .filter(PageVO.class::isInstance)
                    .map(PageVO.class::cast)
                    .filter(page -> CORE_PAGE_LABEL_SET.contains(page.getPageLabel()))
                    .toList();
        } else {
            pageVOList = BeanCopyUtils.copyList(pageDao.selectList(new LambdaQueryWrapper<Page>()
                    .in(Page::getPageLabel, CORE_PAGE_LABELS)
                    .orderByAsc(Page::getId)), PageVO.class);
            cacheStore.put(cacheKeyFactory.pageCover(), pageVOList, Duration.ofMinutes(5));
        }
        return pageVOList;
    }

}




