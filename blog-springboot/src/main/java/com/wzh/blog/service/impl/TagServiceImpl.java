package com.wzh.blog.service.impl;

import jakarta.annotation.Resource;
import com.wzh.blog.web.PaginationContext;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.wzh.blog.dao.ArticleTagDao;
import com.wzh.blog.dto.TagBackDTO;
import com.wzh.blog.vo.SearchQueryVO;
import com.wzh.blog.vo.PageResult;
import com.wzh.blog.dto.TagDTO;
import com.wzh.blog.entity.ArticleTag;
import com.wzh.blog.entity.Tag;
import com.wzh.blog.dao.TagDao;
import com.wzh.blog.exception.BizException;
import com.wzh.blog.service.TagService;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.wzh.blog.util.BeanCopyUtils;
import com.wzh.blog.vo.TagVO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

/**
 * 标签服务
 *
 * @author yezhiqiu
 * @date 2021/07/28
 */
@Service
public class TagServiceImpl extends ServiceImpl<TagDao, Tag> implements TagService {

    @Resource
    private PaginationContext paginationContext;
    @Autowired
    private TagDao tagDao;
    @Autowired
    private ArticleTagDao articleTagDao;




    @Override
    public PageResult<TagDTO> listTags() {
        // 查询标签列表
        List<Tag> tagList = tagDao.selectList(null);
        // 转换DTO
        List<TagDTO> tagDTOList = BeanCopyUtils.copyList(tagList, TagDTO.class);
        // 查询标签数量
        Long count = tagDao.selectCount(null);
        return new PageResult<>(tagDTOList, count);
    }



    @Override
    public PageResult<TagBackDTO> listTagBackDTO(SearchQueryVO condition) {
        // 查询标签数量
        Long count = tagDao.selectCount(new LambdaQueryWrapper<Tag>()
                .like(StringUtils.isNotBlank(condition.getKeywords()), Tag::getTagName, condition.getKeywords()));
        if (count == 0) {
            return new PageResult<>();
        }
        // 分页查询标签列表
        List<TagBackDTO> tagList = tagDao.listTagBackDTO(paginationContext.getOffset(), paginationContext.getSize(), condition);
        return new PageResult<>(tagList, count);
    }



    @Override
    public List<TagDTO> listTagsBySearch(SearchQueryVO condition) {
        // 搜索标签
        List<Tag> tagList = tagDao.selectList(new LambdaQueryWrapper<Tag>()
                .like(StringUtils.isNotBlank(condition.getKeywords()), Tag::getTagName, condition.getKeywords())
                .orderByDesc(Tag::getId));
        return BeanCopyUtils.copyList(tagList, TagDTO.class);
    }



    @Override
    public void deleteTag(List<Integer> tagIdList) {
        // 查询标签下是否有文章
        Long count = articleTagDao.selectCount(new LambdaQueryWrapper<ArticleTag>()
                .in(ArticleTag::getTagId, tagIdList));
        if (count > 0) {
            throw new BizException("删除失败，该标签下存在文章");
        }
        tagDao.deleteByIds(tagIdList);
    }

    @Transactional(rollbackFor = Exception.class)


    @Override
    public void saveOrUpdateTag(TagVO tagVO) {
        // 查询标签名是否存在
        Tag existTag = tagDao.selectOne(new LambdaQueryWrapper<Tag>()
                .select(Tag::getId)
                .eq(Tag::getTagName, tagVO.getTagName()));
        if (Objects.nonNull(existTag) && !existTag.getId().equals(tagVO.getId())) {
            throw new BizException("标签名已存在");
        }
        Tag tag = BeanCopyUtils.copyObject(tagVO, Tag.class);
        this.saveOrUpdate(tag);
    }

}
