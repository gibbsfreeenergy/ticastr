package com.wzh.blog.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.wzh.blog.dao.ArticleDao;
import com.wzh.blog.dto.CategoryBackDTO;
import com.wzh.blog.dto.CategoryDTO;
import com.wzh.blog.dto.CategoryOptionDTO;
import com.wzh.blog.util.BeanCopyUtils;
import com.wzh.blog.vo.SearchQueryVO;
import com.wzh.blog.vo.PageResult;
import com.wzh.blog.entity.Article;
import com.wzh.blog.entity.Category;
import com.wzh.blog.dao.CategoryDao;
import com.wzh.blog.exception.BizException;
import com.wzh.blog.service.CategoryService;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.wzh.blog.vo.CategoryVO;
import com.wzh.blog.web.PageQuery;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;


/**
 * 分类服务
 *
 * @author xiaojie
 * @date 2021/07/29
 */
@Service
public class CategoryServiceImpl extends ServiceImpl<CategoryDao, Category> implements CategoryService {

    private final CategoryDao categoryDao;
    private final ArticleDao articleDao;

    public CategoryServiceImpl(CategoryDao categoryDao, ArticleDao articleDao) {
        this.categoryDao = categoryDao;
        this.articleDao = articleDao;
    }




    @Override
    public PageResult<CategoryDTO> listCategories() {
        return new PageResult<>(categoryDao.listCategoryDTO(), categoryDao.selectCount(null));
    }



    @Override
    public PageResult<CategoryBackDTO> listBackCategories(SearchQueryVO condition, PageQuery pageQuery) {
        // 查询分类数量
        Long count = categoryDao.selectCount(new LambdaQueryWrapper<Category>()
                .like(StringUtils.isNotBlank(condition.getKeywords()), Category::getCategoryName, condition.getKeywords()));
        if (count == 0) {
            return new PageResult<>();
        }
        // 分页查询分类列表
        List<CategoryBackDTO> categoryList = categoryDao.listCategoryBackDTO(pageQuery.offset(), pageQuery.size(), condition);
        return new PageResult<>(categoryList, count);
    }



    @Override
    public List<CategoryOptionDTO> listCategoriesBySearch(SearchQueryVO condition) {
        // 搜索分类
        List<Category> categoryList = categoryDao.selectList(new LambdaQueryWrapper<Category>()
                .like(StringUtils.isNotBlank(condition.getKeywords()), Category::getCategoryName, condition.getKeywords())
                .orderByDesc(Category::getId));
        return BeanCopyUtils.copyList(categoryList, CategoryOptionDTO.class);
    }



    @Override
    public void deleteCategory(List<Integer> categoryIdList) {
        // 查询分类id下是否有文章
        Long count = articleDao.selectCount(new LambdaQueryWrapper<Article>()
                .in(Article::getCategoryId, categoryIdList));
        if (count > 0) {
            throw new BizException("删除失败，该分类下存在文章");
        }
        categoryDao.deleteByIds(categoryIdList);
    }



    @Override
    public void saveOrUpdateCategory(CategoryVO categoryVO) {
        // 判断分类名重复
        Category existCategory = categoryDao.selectOne(new LambdaQueryWrapper<Category>()
                .select(Category::getId)
                .eq(Category::getCategoryName, categoryVO.getCategoryName()));
        if (Objects.nonNull(existCategory) && !existCategory.getId().equals(categoryVO.getId())) {
            throw new BizException("分类名已存在");
        }
        Category category = Category.builder()
                .id(categoryVO.getId())
                .categoryName(categoryVO.getCategoryName())
                .build();
        this.saveOrUpdate(category);
    }

}
