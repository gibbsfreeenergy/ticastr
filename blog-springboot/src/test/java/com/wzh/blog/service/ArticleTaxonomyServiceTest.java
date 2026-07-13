package com.wzh.blog.service;

import com.wzh.blog.dao.ArticleTagDao;
import com.wzh.blog.dao.CategoryDao;
import com.wzh.blog.dao.TagDao;
import com.wzh.blog.entity.ArticleTag;
import com.wzh.blog.entity.Category;
import com.wzh.blog.entity.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeAll;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;

import static com.wzh.blog.enums.ArticleStatusEnum.PUBLIC;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ArticleTaxonomyServiceTest {

    @BeforeAll
    static void initializeMybatisMetadata() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "");
        TableInfoHelper.initTableInfo(assistant, Category.class);
        TableInfoHelper.initTableInfo(assistant, Tag.class);
        TableInfoHelper.initTableInfo(assistant, ArticleTag.class);
    }

    @Mock
    private CategoryDao categoryDao;
    @Mock
    private TagDao tagDao;
    @Mock
    private ArticleTagDao articleTagDao;

    @Test
    void resolvesCategoryThroughAnIdempotentInsert() {
        Category stored = Category.builder().id(7).categoryName("Java").build();
        when(categoryDao.selectOne(any())).thenReturn(null, stored);
        ArticleTaxonomyService service = new ArticleTaxonomyService(categoryDao, tagDao, articleTagDao);

        Category result = service.resolveCategory(" Java ", PUBLIC.getStatus());

        assertThat(result).isSameAs(stored);
        verify(categoryDao).insertIgnoreByName("Java");
    }

    @Test
    void normalizesTagsWithoutMutatingTheRequest() {
        List<String> requested = new ArrayList<>(List.of(" Spring ", "Spring", "Java"));
        when(tagDao.selectList(any())).thenReturn(List.of(
                Tag.builder().id(1).tagName("Spring").build(),
                Tag.builder().id(2).tagName("Java").build()));
        ArticleTaxonomyService service = new ArticleTaxonomyService(categoryDao, tagDao, articleTagDao);

        service.replaceTags(10, requested);

        assertThat(requested).containsExactly(" Spring ", "Spring", "Java");
        verify(tagDao).insertIgnoreByName("Spring");
        verify(tagDao).insertIgnoreByName("Java");
        ArgumentCaptor<ArticleTag> links = ArgumentCaptor.forClass(ArticleTag.class);
        verify(articleTagDao, times(2)).insertIgnore(links.capture());
        assertThat(links.getAllValues()).extracting(ArticleTag::getArticleId).containsOnly(10);
    }
}
