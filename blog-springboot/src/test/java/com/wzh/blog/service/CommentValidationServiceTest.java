package com.wzh.blog.service;

import com.wzh.blog.dao.ArticleDao;
import com.wzh.blog.dao.CommentDao;
import com.wzh.blog.dao.TalkDao;
import com.wzh.blog.dao.UserInfoDao;
import com.wzh.blog.entity.Article;
import com.wzh.blog.entity.Comment;
import com.wzh.blog.exception.BizException;
import com.wzh.blog.exception.NotFoundException;
import com.wzh.blog.vo.CommentVO;
import org.junit.jupiter.api.Test;

import static com.wzh.blog.enums.CommentTypeEnum.ARTICLE;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CommentValidationServiceTest {

    private final ArticleDao articleDao = mock(ArticleDao.class);
    private final TalkDao talkDao = mock(TalkDao.class);
    private final CommentDao commentDao = mock(CommentDao.class);
    private final UserInfoDao userInfoDao = mock(UserInfoDao.class);
    private final CommentValidationService service =
            new CommentValidationService(articleDao, talkDao, commentDao, userInfoDao);

    @Test
    void rejectsMissingPolymorphicTarget() {
        CommentVO comment = CommentVO.builder().type(ARTICLE.getType()).topicId(99).build();

        assertThatThrownBy(() -> service.validate(comment))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("文章不存在");
    }

    @Test
    void rejectsParentFromAnotherTopic() {
        when(articleDao.selectById(10)).thenReturn(Article.builder().id(10).isDelete(0).build());
        when(commentDao.selectById(5)).thenReturn(
                Comment.builder().id(5).type(ARTICLE.getType()).topicId(11).build());
        CommentVO comment = CommentVO.builder()
                .type(ARTICLE.getType()).topicId(10).parentId(5).build();

        assertThatThrownBy(() -> service.validate(comment))
                .isInstanceOf(BizException.class)
                .hasMessage("父评论与当前评论主题不一致");
    }
}
