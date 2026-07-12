package com.wzh.blog.strategy.impl;

import com.baomidou.mybatisplus.core.toolkit.CollectionUtils;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.wzh.blog.dto.ArticleSearchDTO;
import com.wzh.blog.strategy.SearchStrategy;
import lombok.extern.log4j.Log4j2;
import org.springframework.data.elasticsearch.client.elc.ElasticsearchTemplate;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.core.query.HighlightQuery;
import org.springframework.data.elasticsearch.core.query.highlight.Highlight;
import org.springframework.data.elasticsearch.core.query.highlight.HighlightField;
import org.springframework.data.elasticsearch.core.query.highlight.HighlightFieldParameters;
import org.springframework.data.elasticsearch.core.query.highlight.HighlightParameters;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static com.wzh.blog.constant.CommonConst.FALSE;
import static com.wzh.blog.constant.CommonConst.POST_TAG;
import static com.wzh.blog.constant.CommonConst.PRE_TAG;
import static com.wzh.blog.enums.ArticleStatusEnum.PUBLIC;

/** Elasticsearch 8/9 search implementation using the ELC client. */
@Log4j2
@Service("esSearchStrategyImpl")
public class EsSearchStrategyImpl implements SearchStrategy {

    private final ElasticsearchTemplate elasticsearchTemplate;

    public EsSearchStrategyImpl(ElasticsearchTemplate elasticsearchTemplate) {
        this.elasticsearchTemplate = elasticsearchTemplate;
    }

    @Override
    public List<ArticleSearchDTO> searchArticle(String keywords) {
        if (StringUtils.isBlank(keywords)) {
            return new ArrayList<>();
        }
        return search(buildQuery(keywords));
    }

    private NativeQuery buildQuery(String keywords) {
        Highlight highlight = new Highlight(
                HighlightParameters.builder()
                        .withPreTags(PRE_TAG)
                        .withPostTags(POST_TAG)
                        .build(),
                List.of(
                        new HighlightField("articleTitle"),
                        new HighlightField("articleContent", HighlightFieldParameters.builder()
                                .withFragmentSize(200)
                                .build())));

        return NativeQuery.builder()
                .withQuery(query -> query.bool(bool -> bool
                        .must(must -> must.bool(queryBool -> queryBool
                                .should(should -> should.match(match -> match.field("articleTitle").query(keywords)))
                                .should(should -> should.match(match -> match.field("articleContent").query(keywords)))))
                        .must(must -> must.term(term -> term.field("isDelete")
                                .value(value -> value.longValue((long) FALSE))))
                        .must(must -> must.term(term -> term.field("status")
                                .value(value -> value.longValue(PUBLIC.getStatus().longValue()))))))
                .withHighlightQuery(new HighlightQuery(highlight, ArticleSearchDTO.class))
                .build();
    }

    private List<ArticleSearchDTO> search(NativeQuery query) {
        try {
            SearchHits<ArticleSearchDTO> searchHits = elasticsearchTemplate.search(query, ArticleSearchDTO.class);
            return searchHits.getSearchHits().stream().map(hit -> {
                ArticleSearchDTO article = hit.getContent();
                List<String> titleHighlights = hit.getHighlightFields().get("articleTitle");
                if (CollectionUtils.isNotEmpty(titleHighlights)) {
                    article.setArticleTitle(titleHighlights.getFirst());
                }
                List<String> contentHighlights = hit.getHighlightFields().get("articleContent");
                if (CollectionUtils.isNotEmpty(contentHighlights)) {
                    article.setArticleContent(contentHighlights.getLast());
                }
                return article;
            }).collect(Collectors.toList());
        } catch (Exception exception) {
            log.error("Elasticsearch article search failed", exception);
            return new ArrayList<>();
        }
    }
}
