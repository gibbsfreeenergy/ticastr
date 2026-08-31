package com.wzh.blog.strategy.context;

import com.wzh.blog.dto.ArticleSearchDTO;
import com.wzh.blog.search.ArticleSearchApplicationService;
import com.wzh.blog.web.PageQuery;
import com.wzh.blog.web.CursorPageQuery;
import com.wzh.blog.web.CursorPageResult;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 搜索策略上下文
 *
 * @author yezhiqiu
 * @date 2021/07/27
 */
@Service
public class SearchStrategyContext {
    private final ArticleSearchApplicationService searchService;

    public SearchStrategyContext(ArticleSearchApplicationService searchService) {
        this.searchService = searchService;
    }

    /**
     * 执行搜索策略
     *
     * @param keywords 关键字
     * @return {@link List<ArticleSearchDTO>} 搜索文章
     */
    public List<ArticleSearchDTO> executeSearchStrategy(String keywords, PageQuery pageQuery) {
        return searchService.search(keywords, pageQuery);
    }

    public CursorPageResult<ArticleSearchDTO> executeSearchStrategy(String keywords, CursorPageQuery pageQuery) {
        return searchService.search(keywords, pageQuery);
    }

}
