package com.wzh.blog.service;

import com.wzh.blog.dao.ArticleDao;
import com.wzh.blog.dao.CommentDao;
import com.wzh.blog.dao.TalkDao;
import com.wzh.blog.dao.UserInfoDao;
import com.wzh.blog.entity.Article;
import com.wzh.blog.entity.Comment;
import com.wzh.blog.entity.Talk;
import com.wzh.blog.exception.BizException;
import com.wzh.blog.exception.NotFoundException;
import com.wzh.blog.vo.CommentVO;
import org.springframework.stereotype.Service;

import java.util.Objects;

import static com.wzh.blog.enums.CommentTypeEnum.*;

/** Enforces integrity for the polymorphic comment target model. */
@Service
public class CommentValidationService {

    private final ArticleDao articleDao;
    private final TalkDao talkDao;
    private final CommentDao commentDao;
    private final UserInfoDao userInfoDao;

    public CommentValidationService(ArticleDao articleDao, TalkDao talkDao,
                                    CommentDao commentDao, UserInfoDao userInfoDao) {
        this.articleDao = articleDao;
        this.talkDao = talkDao;
        this.commentDao = commentDao;
        this.userInfoDao = userInfoDao;
    }

    public void validate(CommentVO comment) {
        if (getCommentEnum(comment.getType()) == null) {
            throw new BizException("评论类型不正确");
        }
        validateTarget(comment);
        validateParent(comment);
        if (comment.getReplyUserId() != null && userInfoDao.selectById(comment.getReplyUserId()) == null) {
            throw new NotFoundException("回复用户不存在");
        }
    }

    private void validateTarget(CommentVO comment) {
        if (LINK.equals(getCommentEnum(comment.getType()))) {
            return;
        }
        if (comment.getTopicId() == null) {
            throw new BizException("评论主题不能为空");
        }
        if (ARTICLE.equals(getCommentEnum(comment.getType()))) {
            Article article = articleDao.selectById(comment.getTopicId());
            if (article == null || Integer.valueOf(1).equals(article.getIsDelete())) {
                throw new NotFoundException("文章不存在");
            }
        } else if (TALK.equals(getCommentEnum(comment.getType()))) {
            Talk talk = talkDao.selectById(comment.getTopicId());
            if (talk == null) {
                throw new NotFoundException("说说不存在");
            }
        }
    }

    private void validateParent(CommentVO comment) {
        if (comment.getParentId() == null) {
            return;
        }
        Comment parent = commentDao.selectById(comment.getParentId());
        if (parent == null || !Objects.equals(parent.getType(), comment.getType())
                || !Objects.equals(parent.getTopicId(), comment.getTopicId())) {
            throw new BizException("父评论与当前评论主题不一致");
        }
    }
}
