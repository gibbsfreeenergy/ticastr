package com.wzh.blog.search;

import com.wzh.blog.config.SearchProperties;
import com.wzh.blog.infrastructure.search.LuceneArticleSearchIndex;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LuceneArticleSearchIndexTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void searchesTitleTagsAndMarkdownBody() {
        LuceneArticleSearchIndex index = new LuceneArticleSearchIndex(temporaryDirectory.resolve("index"));
        index.upsert(new ArticleSearchDocument(1, "Spring 性能优化", 2,
                List.of("Java", "数据库"), "使用索引优化查询和缓存策略"));

        assertThat(index.search("性能", 0, 20)).extracting(ArticleSearchResult::articleId).containsExactly(1);
        assertThat(index.search("数据库", 0, 20)).extracting(ArticleSearchResult::articleId).containsExactly(1);
        assertThat(index.search("缓存", 0, 20)).extracting(ArticleSearchResult::articleId).containsExactly(1);
    }

    @Test
    void replacingAndDeletingAreAtomicFromTheReaderPerspective() {
        LuceneArticleSearchIndex index = new LuceneArticleSearchIndex(temporaryDirectory.resolve("index"));
        index.upsert(new ArticleSearchDocument(1, "旧标题", null, List.of(), "旧正文"));
        index.upsert(new ArticleSearchDocument(1, "新标题", null, List.of(), "新正文"));

        assertThat(index.search("旧标题", 0, 20)).isEmpty();
        assertThat(index.search("新标题", 0, 20)).hasSize(1);

        index.delete(1);
        assertThat(index.search("新标题", 0, 20)).isEmpty();
        assertThat(index.documentCount()).isZero();
    }

    @Test
    void rebuildReplacesTheExistingDirectoryAndEscapesQuerySyntax() {
        LuceneArticleSearchIndex index = new LuceneArticleSearchIndex(temporaryDirectory.resolve("index"));
        index.upsert(new ArticleSearchDocument(1, "旧", null, List.of(), "旧内容"));
        index.rebuild(List.of(
                new ArticleSearchDocument(2, "新文章", null, List.of("标签"), "正文内容"),
                new ArticleSearchDocument(3, "另一篇", null, List.of(), "更多内容")));

        assertThat(index.search("旧", 0, 20)).isEmpty();
        assertThat(index.search("+新文章", 0, 20)).extracting(ArticleSearchResult::articleId).containsExactly(2);
        assertThat(index.documentCount()).isEqualTo(2);
    }

    @Test
    void resolvesRelativeIndexPathInsideConfiguredDataRoot() {
        SearchProperties properties = new SearchProperties();
        properties.setDataRoot(temporaryDirectory.resolve("data").toString());
        properties.setIndexPath("search-index");

        LuceneArticleSearchIndex index = new LuceneArticleSearchIndex(properties);

        assertThat(index.indexPath())
                .isEqualTo(temporaryDirectory.resolve("data/search-index").toAbsolutePath().normalize());
    }

    @Test
    void rejectsIndexPathOutsideConfiguredDataRoot() {
        SearchProperties properties = new SearchProperties();
        properties.setDataRoot(temporaryDirectory.resolve("data").toString());
        properties.setIndexPath("../outside");

        assertThatThrownBy(() -> new LuceneArticleSearchIndex(properties))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("inside app.search.data-root");
    }
}
