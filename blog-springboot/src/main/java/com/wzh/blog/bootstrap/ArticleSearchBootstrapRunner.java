package com.wzh.blog.bootstrap;

import com.wzh.blog.search.ArticleSearchApplicationService;
import lombok.extern.log4j.Log4j2;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/** Rebuilds the disposable local projection after managed storage is initialized. */
@Component
@Order(1)
@Log4j2
public class ArticleSearchBootstrapRunner implements ApplicationRunner {
    private final ArticleSearchApplicationService searchService;

    public ArticleSearchBootstrapRunner(ArticleSearchApplicationService searchService) {
        this.searchService = searchService;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            searchService.rebuildAll();
        } catch (RuntimeException exception) {
            log.warn("Search rebuild failed ({}); existing index retained. Restore storage access and restart to retry.",
                    exception.getClass().getSimpleName());
        }
    }
}
