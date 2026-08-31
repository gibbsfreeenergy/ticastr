package com.wzh.blog.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.wzh.blog.dao.PhotoAlbumDao;
import com.wzh.blog.dao.PhotoDao;
import com.wzh.blog.dto.PhotoAlbumBackDTO;
import com.wzh.blog.dto.PhotoAlbumDTO;
import com.wzh.blog.entity.Photo;
import com.wzh.blog.entity.PhotoAlbum;
import com.wzh.blog.exception.BizException;
import com.wzh.blog.service.PhotoAlbumService;
import com.wzh.blog.media.AssetLifecycleService;
import com.wzh.blog.util.BeanCopyUtils;
import com.wzh.blog.vo.SearchQueryVO;
import com.wzh.blog.vo.PageResult;
import com.wzh.blog.vo.PhotoAlbumVO;
import com.wzh.blog.web.PageQuery;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

import static com.wzh.blog.constant.CommonConst.FALSE;
import static com.wzh.blog.constant.CommonConst.TRUE;
import static com.wzh.blog.enums.PhotoAlbumStatusEnum.PUBLIC;


/**
 * 相册服务
 *
 * @author yezhiqiu
 * @date 2021/08/04
 */
@Service
public class PhotoAlbumServiceImpl extends ServiceImpl<PhotoAlbumDao, PhotoAlbum> implements PhotoAlbumService {

    private final PhotoAlbumDao photoAlbumDao;
    private final PhotoDao photoDao;
    private final AssetLifecycleService assetLifecycleService;

    public PhotoAlbumServiceImpl(PhotoAlbumDao photoAlbumDao,
                                 PhotoDao photoDao,
                                 AssetLifecycleService assetLifecycleService) {
        this.photoAlbumDao = photoAlbumDao;
        this.photoDao = photoDao;
        this.assetLifecycleService = assetLifecycleService;
    }

    @Transactional(rollbackFor = Exception.class)



    @Override
    public void saveOrUpdatePhotoAlbum(PhotoAlbumVO photoAlbumVO) {
        String previousCover = null;
        if (photoAlbumVO.getId() != null) {
            PhotoAlbum existingAlbum = photoAlbumDao.selectById(photoAlbumVO.getId());
            previousCover = existingAlbum == null ? null : existingAlbum.getAlbumCover();
        }
        // 查询相册名是否存在
        PhotoAlbum album = photoAlbumDao.selectOne(new LambdaQueryWrapper<PhotoAlbum>()
                .select(PhotoAlbum::getId)
                .eq(PhotoAlbum::getAlbumName, photoAlbumVO.getAlbumName()));
        if (Objects.nonNull(album) && !album.getId().equals(photoAlbumVO.getId())) {
            throw new BizException("相册名已存在");
        }
        PhotoAlbum photoAlbum = BeanCopyUtils.copyObject(photoAlbumVO, PhotoAlbum.class);
        this.saveOrUpdate(photoAlbum);
        if (previousCover != null && !previousCover.equals(photoAlbum.getAlbumCover())) {
            assetLifecycleService.deleteAfterCommit(List.of(previousCover));
        }
    }



    @Override
    public PageResult<PhotoAlbumBackDTO> listPhotoAlbumBacks(SearchQueryVO condition, PageQuery pageQuery) {
        // 查询相册数量
        Long count = photoAlbumDao.selectCount(new LambdaQueryWrapper<PhotoAlbum>()
                .like(StringUtils.isNotBlank(condition.getKeywords()), PhotoAlbum::getAlbumName, condition.getKeywords())
                .eq(PhotoAlbum::getIsDelete, FALSE));
        if (count == 0) {
            return new PageResult<>(List.of(), 0);
        }
        // 查询相册信息
        List<PhotoAlbumBackDTO> photoAlbumBackList = photoAlbumDao.listPhotoAlbumBacks(pageQuery.offset(), pageQuery.size(), condition);
        return new PageResult<>(photoAlbumBackList, count);
    }



    @Override
    public List<PhotoAlbumDTO> listPhotoAlbumBackInfos() {
        List<PhotoAlbum> photoAlbumList = photoAlbumDao.selectList(new LambdaQueryWrapper<PhotoAlbum>()
                .eq(PhotoAlbum::getIsDelete, FALSE));
        return BeanCopyUtils.copyList(photoAlbumList, PhotoAlbumDTO.class);
    }



    @Override
    public PhotoAlbumBackDTO getPhotoAlbumBackById(Integer albumId) {
        // 查询相册信息
        PhotoAlbum photoAlbum = photoAlbumDao.selectById(albumId);
        // 查询照片数量
        Long photoCount = photoDao.selectCount(new LambdaQueryWrapper<Photo>()
                .eq(Photo::getAlbumId, albumId)
                .eq(Photo::getIsDelete, FALSE));
        PhotoAlbumBackDTO album = BeanCopyUtils.copyObject(photoAlbum, PhotoAlbumBackDTO.class);
        album.setPhotoCount(photoCount);
        return album;
    }



    @Override
    public void deletePhotoAlbumById(Integer albumId) {
        PhotoAlbum album = photoAlbumDao.selectById(albumId);
        // 查询照片数量
        Long count = photoDao.selectCount(new LambdaQueryWrapper<Photo>()
                .eq(Photo::getAlbumId, albumId));
        if (count > 0) {
            // 若相册下存在照片则逻辑删除相册和照片
            photoAlbumDao.updateById(PhotoAlbum.builder()
                    .id(albumId)
                    .isDelete(TRUE)
                    .build());
            photoDao.update(new Photo(), new LambdaUpdateWrapper<Photo>()
                    .set(Photo::getIsDelete, TRUE)
                    .eq(Photo::getAlbumId, albumId));
        } else {
            // 若相册下不存在照片则直接删除
            photoAlbumDao.deleteById(albumId);
            if (album != null) {
                assetLifecycleService.deleteAfterCommit(List.of(album.getAlbumCover()));
            }
        }
    }



    @Override
    public List<PhotoAlbumDTO> listPhotoAlbums() {
        // 查询相册列表
        List<PhotoAlbum> photoAlbumList = photoAlbumDao.selectList(new LambdaQueryWrapper<PhotoAlbum>()
                .eq(PhotoAlbum::getStatus, PUBLIC.getStatus())
                .eq(PhotoAlbum::getIsDelete, FALSE)
                .orderByDesc(PhotoAlbum::getId));
        return BeanCopyUtils.copyList(photoAlbumList, PhotoAlbumDTO.class);
    }

}




