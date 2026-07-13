package com.wzh.blog.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.wzh.blog.dao.ArticleTagDao;
import com.wzh.blog.dao.CategoryDao;
import com.wzh.blog.dao.TagDao;
import com.wzh.blog.entity.ArticleTag;
import com.wzh.blog.entity.Category;
import com.wzh.blog.entity.Tag;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.List;

import static com.wzh.blog.enums.ArticleStatusEnum.DRAFT;

/** Owns the category/tag invariants used while saving an article. */
@Service
public class ArticleTaxonomyService {

    private final CategoryDao categoryDao;
    private final TagDao tagDao;
    private final ArticleTagDao articleTagDao;

    public ArticleTaxonomyService(CategoryDao categoryDao, TagDao tagDao, ArticleTagDao articleTagDao) {
        this.categoryDao = categoryDao;
        this.tagDao = tagDao;
        this.articleTagDao = articleTagDao;
    }

    public Category resolveCategory(String categoryName, Integer articleStatus) {
        if (categoryName == null || categoryName.isBlank()) {
            return null;
        }
        String normalizedName = categoryName.trim();
        Category category = findCategory(normalizedName);
        if (category == null && !DRAFT.getStatus().equals(articleStatus)) {
            categoryDao.insertIgnoreByName(normalizedName);
            category = findCategory(normalizedName);
        }
        return category;
    }

    public void replaceTags(Integer articleId, Collection<String> requestedNames) {
        articleTagDao.delete(new LambdaQueryWrapper<ArticleTag>().eq(ArticleTag::getArticleId, articleId));
        if (requestedNames == null || requestedNames.isEmpty()) {
            return;
        }
        List<String> tagNames = requestedNames.stream()
                .filter(name -> name != null && !name.isBlank())
                .map(String::trim)
                .distinct()
                .toList();
        if (tagNames.isEmpty()) {
            return;
        }
        tagNames.forEach(tagDao::insertIgnoreByName);
        tagDao.selectList(new LambdaQueryWrapper<Tag>().in(Tag::getTagName, tagNames))
                .forEach(tag -> articleTagDao.insertIgnore(ArticleTag.builder()
                        .articleId(articleId)
                        .tagId(tag.getId())
                        .build()));
    }

    private Category findCategory(String categoryName) {
        return categoryDao.selectOne(new LambdaQueryWrapper<Category>()
                .eq(Category::getCategoryName, categoryName));
    }
}
