package com.wzh.blog.strategy.context;

import com.wzh.blog.dto.ArticleSearchDTO;
import com.wzh.blog.search.ArticleSearchApplicationService;
import com.wzh.blog.web.CursorPageQuery;
import com.wzh.blog.web.CursorPageResult;
import org.springframework.stereotype.Service;

/**
 * 文章搜索上下文。
 */
@Service
public class SearchStrategyContext {

    private final ArticleSearchApplicationService searchService;

    public SearchStrategyContext(ArticleSearchApplicationService searchService) {
        this.searchService = searchService;
    }

    public CursorPageResult<ArticleSearchDTO> executeSearchStrategy(
            String keywords, CursorPageQuery pageQuery) {
        return searchService.search(keywords, pageQuery);
    }
}
