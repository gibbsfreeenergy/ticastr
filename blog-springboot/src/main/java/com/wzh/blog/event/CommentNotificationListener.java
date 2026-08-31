package com.wzh.blog.event;

import com.wzh.blog.dao.ArticleDao;
import com.wzh.blog.dao.TalkDao;
import com.wzh.blog.dao.UserInfoDao;
import com.wzh.blog.dto.EmailDTO;
import com.wzh.blog.entity.Article;
import com.wzh.blog.entity.Comment;
import com.wzh.blog.entity.Talk;
import com.wzh.blog.entity.UserInfo;
import com.wzh.blog.service.OutboxEventService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import static com.wzh.blog.constant.CommonConst.TRUE;
import static com.wzh.blog.enums.CommentTypeEnum.*;

@Component
public class CommentNotificationListener {

    private final ArticleDao articleDao;
    private final TalkDao talkDao;
    private final UserInfoDao userInfoDao;
    private final OutboxEventService outboxEventService;
    private final String websiteUrl;
    private final int ownerUserId;

    public CommentNotificationListener(ArticleDao articleDao, TalkDao talkDao, UserInfoDao userInfoDao,
                                       OutboxEventService outboxEventService,
                                       @Value("${website.url}") String websiteUrl,
                                       @Value("${app.owner-user-id:1}") int ownerUserId) {
        this.articleDao = articleDao;
        this.talkDao = talkDao;
        this.userInfoDao = userInfoDao;
        this.outboxEventService = outboxEventService;
        this.websiteUrl = websiteUrl;
        this.ownerUserId = ownerUserId;
    }

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT, fallbackExecution = false)
    public void notifyCommentCreated(CommentNotificationEvent event) {
        Comment comment = event.comment();
        Integer recipientId = resolveRecipient(comment);
        UserInfo recipient = recipientId == null ? null : userInfoDao.selectById(recipientId);
        if (recipient == null || recipient.getEmail() == null || recipient.getEmail().isBlank()) {
            return;
        }
        EmailDTO email = buildEmail(comment, recipient.getEmail());
        if (email == null) {
            return;
        }
        outboxEventService.enqueueEmail(email, "comment:" + comment.getId());
    }

    private Integer resolveRecipient(Comment comment) {
        if (comment.getReplyUserId() != null) {
            return comment.getReplyUserId();
        }
        if (ARTICLE.equals(getCommentEnum(comment.getType()))) {
            Article article = articleDao.selectById(comment.getTopicId());
            return article == null ? null : article.getUserId();
        }
        if (TALK.equals(getCommentEnum(comment.getType()))) {
            Talk talk = talkDao.selectById(comment.getTopicId());
            return talk == null ? null : talk.getUserId();
        }
        return ownerUserId;
    }

    private EmailDTO buildEmail(Comment comment, String recipientEmail) {
        EmailDTO email = new EmailDTO();
        if (Integer.valueOf(TRUE).equals(comment.getIsReview())) {
            email.setEmail(recipientEmail);
            email.setSubject("评论提醒");
            String id = comment.getTopicId() == null ? "" : comment.getTopicId().toString();
            email.setContent("您收到了一条新的回复，请前往" + websiteUrl
                    + getCommentPath(comment.getType()) + id + "\n页面查看");
            return email;
        }
        UserInfo administrator = userInfoDao.selectById(ownerUserId);
        if (administrator == null || administrator.getEmail() == null || administrator.getEmail().isBlank()) {
            return null;
        }
        email.setEmail(administrator.getEmail());
        email.setSubject("审核提醒");
        email.setContent("您收到了一条新的回复，请前往后台管理页面审核");
        return email;
    }
}
