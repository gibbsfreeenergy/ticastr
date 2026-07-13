package com.wzh.blog.service.impl;

import jakarta.annotation.Resource;
import com.wzh.blog.web.PaginationContext;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.CollectionUtils;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.wzh.blog.event.CommentNotificationEvent;
import com.wzh.blog.dto.*;
import com.wzh.blog.entity.Comment;
import com.wzh.blog.dao.CommentDao;
import com.wzh.blog.service.BlogInfoService;
import com.wzh.blog.service.CommentService;
import com.wzh.blog.service.CommentValidationService;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.wzh.blog.service.EngagementService;
import com.wzh.blog.service.RedisService;
import com.wzh.blog.util.HTMLUtils;
import com.wzh.blog.util.UserUtils;
import com.wzh.blog.vo.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

import static com.wzh.blog.constant.CommonConst.*;
import static com.wzh.blog.constant.RedisPrefixConst.COMMENT_LIKE_COUNT;
import static com.wzh.blog.constant.RedisPrefixConst.COMMENT_USER_LIKE;
import static com.wzh.blog.enums.CommentTypeEnum.*;

/**
 * 评论服务
 *
 * @author yezhiqiu
 * @date 2021/07/31
 * @since 2020-05-18
 */
@Service
public class CommentServiceImpl extends ServiceImpl<CommentDao, Comment> implements CommentService {

    @Resource
    private PaginationContext paginationContext;
    @Autowired
    private CommentDao commentDao;
    @Autowired
    private RedisService redisService;
    @Autowired
    private EngagementService engagementService;
    @Autowired
    private BlogInfoService blogInfoService;
    @Autowired
    private ApplicationEventPublisher eventPublisher;
    @Autowired
    private CommentValidationService commentValidationService;




    @Override
    public PageResult<CommentDTO> listComments(CommentVO commentVO) {
        // 查询评论量
        Long commentCount = commentDao.selectCount(new LambdaQueryWrapper<Comment>()
                .eq(Objects.nonNull(commentVO.getTopicId()), Comment::getTopicId, commentVO.getTopicId())
                .eq(Comment::getType, commentVO.getType())
                .isNull(Comment::getParentId)
                .eq(Comment::getIsReview, TRUE));
        if (commentCount == 0) {
            return new PageResult<>();
        }
        // 分页查询评论数据
        List<CommentDTO> commentDTOList = commentDao.listComments(paginationContext.getOffset(), paginationContext.getSize(), commentVO);
        if (CollectionUtils.isEmpty(commentDTOList)) {
            return new PageResult<>();
        }
        // 查询redis的评论点赞数据
        Map<String, Object> likeCountMap = redisService.hGetAll(COMMENT_LIKE_COUNT);
        // 提取评论id集合
        List<Integer> commentIdList = commentDTOList.stream()
                .map(CommentDTO::getId)
                .collect(Collectors.toList());
        // 根据评论id集合查询回复数据
        List<ReplyDTO> replyDTOList = commentDao.listReplies(commentIdList);
        // 封装回复点赞量
        replyDTOList.forEach(item -> item.setLikeCount((Integer) likeCountMap.get(item.getId().toString())));
        // 根据评论id分组回复数据
        Map<Integer, List<ReplyDTO>> replyMap = replyDTOList.stream()
                .collect(Collectors.groupingBy(ReplyDTO::getParentId));
        // 根据评论id查询回复量
        Map<Integer, Integer> replyCountMap = commentDao.listReplyCountByCommentId(commentIdList)
                .stream().collect(Collectors.toMap(ReplyCountDTO::getCommentId, ReplyCountDTO::getReplyCount));
        // 封装评论数据
        commentDTOList.forEach(item -> {
            item.setLikeCount((Integer) likeCountMap.get(item.getId().toString()));
            item.setReplyDTOList(replyMap.get(item.getId()));
            item.setReplyCount(replyCountMap.get(item.getId()));
        });
        return new PageResult<>(commentDTOList, commentCount);
    }



    @Override
    public List<ReplyDTO> listRepliesByCommentId(Integer commentId) {
        // 转换页码查询评论下的回复
        List<ReplyDTO> replyDTOList = commentDao.listRepliesByCommentId(paginationContext.getOffset(), paginationContext.getSize(), commentId);
        // 查询redis的评论点赞数据
        Map<String, Object> likeCountMap = redisService.hGetAll(COMMENT_LIKE_COUNT);
        // 封装点赞数据
        replyDTOList.forEach(item -> item.setLikeCount((Integer) likeCountMap.get(item.getId().toString())));
        return replyDTOList;
    }



    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveComment(CommentVO commentVO) {
        commentValidationService.validate(commentVO);
        // 判断是否需要审核
        WebsiteConfigVO websiteConfig = blogInfoService.getWebsiteConfig();
        Integer isReview = websiteConfig.getIsCommentReview();
        // 过滤标签
        commentVO.setCommentContent(HTMLUtils.filter(commentVO.getCommentContent()));
        Comment comment = Comment.builder()
                .userId(UserUtils.getLoginUser().getUserInfoId())
                .replyUserId(commentVO.getReplyUserId())
                .topicId(commentVO.getTopicId())
                .commentContent(commentVO.getCommentContent())
                .parentId(commentVO.getParentId())
                .type(commentVO.getType())
                .isReview(isReview == TRUE ? FALSE : TRUE)
                .build();
        commentDao.insert(comment);
        // 判断是否开启邮箱通知,通知用户
        if (websiteConfig.getIsEmailNotice().equals(TRUE)) {
            eventPublisher.publishEvent(new CommentNotificationEvent(comment));
        }
    }



    @Override
    public void saveCommentLike(Integer commentId) {
        engagementService.toggleCommentLike(UserUtils.getLoginUser().getUserInfoId(), commentId);
    }



    @Override
    public void updateCommentsReview(ReviewVO reviewVO) {
        // 修改评论审核状态
        List<Comment> commentList = reviewVO.getIdList().stream().map(item -> Comment.builder()
                        .id(item)
                        .isReview(reviewVO.getIsReview())
                        .build())
                .collect(Collectors.toList());
        this.updateBatchById(commentList);
    }



    @Override
    public PageResult<CommentBackDTO> listCommentBackDTO(CommentQueryVO condition) {
        // 统计后台评论量
        Integer count = commentDao.countCommentDTO(condition);
        if (count == 0) {
            return new PageResult<>();
        }
        // 查询后台评论集合
        List<CommentBackDTO> commentBackDTOList = commentDao.listCommentBackDTO(paginationContext.getOffset(), paginationContext.getSize(), condition);
        return new PageResult<>(commentBackDTOList, count);
    }

}
