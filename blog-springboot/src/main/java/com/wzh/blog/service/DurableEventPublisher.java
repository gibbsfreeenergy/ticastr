package com.wzh.blog.service;

import com.wzh.blog.dto.EmailDTO;
import com.wzh.blog.dto.DurableEventEnvelope;

/** Durable event boundary used by notification and integration producers. */
public interface DurableEventPublisher {

    String publishEmail(EmailDTO email, String aggregateId);

    String publishEmail(DurableEventEnvelope<EmailDTO> event);
}
