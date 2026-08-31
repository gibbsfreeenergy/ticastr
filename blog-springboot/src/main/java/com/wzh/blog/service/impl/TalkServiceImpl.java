package com.wzh.blog.service.impl;

import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.wzh.blog.dao.CommentDao;
import com.wzh.blog.dto.CommentCountDTO;
import com.wzh.blog.dto.TalkBackDTO;
import com.wzh.blog.dto.TalkDTO;
import com.wzh.blog.entity.Talk;
import com.wzh.blog.exception.BizException;
import com.wzh.blog.exception.NotFoundException;
import com.wzh.blog.service.RedisService;
import com.wzh.blog.service.EngagementService;
import com.wzh.blog.service.TalkService;
import com.wzh.blog.dao.TalkDao;
import com.wzh.blog.security.CurrentUser;
import com.wzh.blog.util.*;
import com.wzh.blog.vo.StatusQueryVO;
import com.wzh.blog.vo.PageResult;
import com.wzh.blog.vo.TalkVO;
import com.wzh.blog.web.PageQuery;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import static com.wzh.blog.constant.RedisPrefixConst.*;
import static com.wzh.blog.enums.TalkStatusEnum.PUBLIC;

/**
 * 说说服务
 *
 * @author yezhiqiu
 * @date 2022/01/23
 */
@Service
public class TalkServiceImpl extends ServiceImpl<TalkDao, Talk> implements TalkService {

    private final TalkDao talkDao;
    private final CommentDao commentDao;
    private final RedisService redisService;
    private final EngagementService engagementService;
    private final CurrentUser currentUser;

    public TalkServiceImpl(TalkDao talkDao,
                           CommentDao commentDao,
                           RedisService redisService,
                           EngagementService engagementService,
                           CurrentUser currentUser) {
        this.talkDao = talkDao;
        this.commentDao = commentDao;
        this.redisService = redisService;
        this.engagementService = engagementService;
        this.currentUser = currentUser;
    }




    @Override
    public List<String> listHomeTalks() {
        // 查询最新10条说说
        return talkDao.selectList(new LambdaQueryWrapper<Talk>()
                        .eq(Talk::getStatus, PUBLIC.getStatus())
                        .orderByDesc(Talk::getIsTop)
                        .orderByDesc(Talk::getId)
                        .last("limit 10"))
                .stream()
                .map(item -> item.getContent().length() > 200 ? HTMLUtils.deleteHMTLTag(item.getContent().substring(0, 200)) : HTMLUtils.deleteHMTLTag(item.getContent()))
                .collect(Collectors.toList());
    }



    @Override
    public PageResult<TalkDTO> listTalks(PageQuery pageQuery) {
        // 查询说说总量
        Long count = talkDao.selectCount((new LambdaQueryWrapper<Talk>()
                .eq(Talk::getStatus, PUBLIC.getStatus())));
        if (count == 0) {
            return new PageResult<>();
        }
        // 分页查询说说
        List<TalkDTO> talkDTOList = talkDao.listTalks(pageQuery.offset(), pageQuery.size());
        // 查询说说评论量
        List<Integer> talkIdList = talkDTOList.stream()
                .map(TalkDTO::getId)
                .collect(Collectors.toList());
        Map<Integer, Integer> commentCountMap = commentDao.listCommentCountByTopicIds(talkIdList)
                .stream()
                .collect(Collectors.toMap(CommentCountDTO::getId, CommentCountDTO::getCommentCount));
        // 查询说说点赞量
        Map<String, Object> likeCountMap = redisService.hGetAll(TALK_LIKE_COUNT);
        talkDTOList.forEach(item -> {
            item.setLikeCount((Integer) likeCountMap.get(item.getId().toString()));
            item.setCommentCount(commentCountMap.get(item.getId()));
            // 转换图片格式
            if (Objects.nonNull(item.getImages())) {
                item.setImgList(CommonUtils.castList(JSON.parseObject(item.getImages(), List.class), String.class));
            }
        });
        return new PageResult<>(talkDTOList, count);
    }



    @Override
    public TalkDTO getTalkById(Integer talkId) {
        // 查询说说信息
        TalkDTO talkDTO = talkDao.getTalkById(talkId);
        if (Objects.isNull(talkDTO)) {
            throw new NotFoundException("说说不存在");
        }
        // 查询说说点赞量
        talkDTO.setLikeCount((Integer) redisService.hGet(TALK_LIKE_COUNT, talkId.toString()));
        // 转换图片格式
        if (Objects.nonNull(talkDTO.getImages())) {
            talkDTO.setImgList(CommonUtils.castList(JSON.parseObject(talkDTO.getImages(), List.class), String.class));
        }
        return talkDTO;
    }



    @Override
    public void saveTalkLike(Integer talkId) {
        engagementService.toggleTalkLike(currentUser.id(), talkId);
    }



    @Override
    public void saveOrUpdateTalk(TalkVO talkVO) {
        talkVO.setContent(HTMLUtils.sanitizeRichText(talkVO.getContent()));
        Talk talk = BeanCopyUtils.copyObject(talkVO, Talk.class);
        talk.setUserId(currentUser.id());
        this.saveOrUpdate(talk);
    }



    @Override
    public void deleteTalks(List<Integer> talkIdList) {
        talkDao.deleteByIds(talkIdList);
    }



    @Override
    public PageResult<TalkBackDTO> listBackTalks(StatusQueryVO conditionVO, PageQuery pageQuery) {
        // 查询说说总量
        Long count = talkDao.selectCount(new LambdaQueryWrapper<Talk>()
                .eq(Objects.nonNull(conditionVO.getStatus()), Talk::getStatus, conditionVO.getStatus()));
        if (count == 0) {
            return new PageResult<>();
        }
        // 分页查询说说
        List<TalkBackDTO> talkDTOList = talkDao.listBackTalks(pageQuery.offset(), pageQuery.size(), conditionVO);
        talkDTOList.forEach(item -> {
            // 转换图片格式
            if (Objects.nonNull(item.getImages())) {
                item.setImgList(CommonUtils.castList(JSON.parseObject(item.getImages(), List.class), String.class));
            }
        });
        return new PageResult<>(talkDTOList, count);
    }



    @Override
    public TalkBackDTO getBackTalkById(Integer talkId) {
        TalkBackDTO talkBackDTO = talkDao.getBackTalkById(talkId);
        // 转换图片格式
        if (Objects.nonNull(talkBackDTO.getImages())) {
            talkBackDTO.setImgList(CommonUtils.castList(JSON.parseObject(talkBackDTO.getImages(), List.class), String.class));
        }
        return talkBackDTO;
    }

}




