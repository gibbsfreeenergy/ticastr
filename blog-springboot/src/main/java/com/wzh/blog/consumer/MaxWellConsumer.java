package com.wzh.blog.consumer;

import com.alibaba.fastjson2.JSON;
import com.wzh.blog.dto.ArticleSearchDTO;
import com.wzh.blog.dto.MaxwellDataDTO;
import com.wzh.blog.entity.Article;
import com.wzh.blog.util.BeanCopyUtils;
import org.springframework.amqp.rabbit.annotation.RabbitHandler;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.elasticsearch.client.elc.ElasticsearchTemplate;
import org.springframework.stereotype.Component;


import static com.wzh.blog.constant.MQPrefixConst.MAXWELL_QUEUE;

/**
 * maxwell监听数据
 *
 * @author yezhiqiu
 * @date 2021/08/02
 */
@Component
@ConditionalOnProperty(name = "search.mode", havingValue = "elasticsearch")
@RabbitListener(queues = MAXWELL_QUEUE)
public class MaxWellConsumer {
    @Autowired
    private ElasticsearchTemplate elasticsearchTemplate;

    @RabbitHandler
    public void process(byte[] data) {
        // 获取监听信息
        MaxwellDataDTO maxwellDataDTO = JSON.parseObject(new String(data), MaxwellDataDTO.class);
        // 获取文章数据
        Article article = JSON.parseObject(JSON.toJSONString(maxwellDataDTO.getData()), Article.class);
        // 判断操作类型
        switch (maxwellDataDTO.getType()) {
            case "insert":
            case "update":
                // 更新es文章
                elasticsearchTemplate.save(BeanCopyUtils.copyObject(article, ArticleSearchDTO.class));
                break;
            case "delete":
                // 删除文章
                elasticsearchTemplate.delete(article.getId().toString(), ArticleSearchDTO.class);
                break;
            default:
                break;
        }
    }

}
