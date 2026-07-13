package com.wzh.blog.event;

import com.wzh.blog.entity.Comment;

public record CommentNotificationEvent(Comment comment) {
}
