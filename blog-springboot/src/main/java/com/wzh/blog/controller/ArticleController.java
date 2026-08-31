package com.wzh.blog.controller;


import com.wzh.blog.annotation.OptLog;
import com.wzh.blog.annotation.AccessLimit;
import com.wzh.blog.dto.*;
import com.wzh.blog.enums.FilePathEnum;
import com.wzh.blog.content.application.ArticleUseCase;
import com.wzh.blog.media.MediaAssetStore;
import com.wzh.blog.content.ArticleContentService;
import com.wzh.blog.content.ContentAsset;
import com.wzh.blog.media.StorageObject;
import com.wzh.blog.vo.*;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import jakarta.validation.Valid;
import java.util.*;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.function.Supplier;

import static com.wzh.blog.constant.OptTypeConst.*;

/**
 * 文章控制器
 *
 * @author yezhiqiu
 * @date 2021/07/28
 */
@Tag(name = "文章模块")
@RestController
public class ArticleController {
    private final ArticleUseCase articleService;
    private final MediaAssetStore mediaAssetStore;
    private final ArticleContentService articleContentService;

    public ArticleController(ArticleUseCase articleService,
                             MediaAssetStore mediaAssetStore,
                             ArticleContentService articleContentService) {
        this.articleService = articleService;
        this.mediaAssetStore = mediaAssetStore;
        this.articleContentService = articleContentService;
    }

    /**
     * 查看文章归档
     *
     * @return {@link Result<ArchiveDTO>} 文章归档列表
     */
    @Operation(summary = "查看文章归档")
    @GetMapping("/articles/archives")
    public Result<com.wzh.blog.web.CursorPageResult<ArchiveDTO>> listArchives(CursorPageQueryVO pageQueryVO) {
        return Result.ok(articleService.listArchives(pageQueryVO.toCursorPageQuery()));
    }

    /**
     * 查看首页文章
     *
     * @return {@link Result<ArticleHomeDTO>} 首页文章列表
     */
    @Operation(summary = "查看首页文章")
    @GetMapping("/articles")
    public Result<com.wzh.blog.web.CursorPageResult<ArticleHomeDTO>> listArticles(CursorPageQueryVO pageQueryVO) {
        return Result.ok(articleService.listArticles(pageQueryVO.toCursorPageQuery()));
    }

    /**
     * 查看后台文章
     *
     * @param conditionVO 条件
     * @return {@link Result<ArticleBackDTO>} 后台文章列表
     */
    @Operation(summary = "查看后台文章")
    @GetMapping("/admin/articles")
    public Result<PageResult<ArticleBackDTO>> listArticleBacks(ArticleQueryVO conditionVO) {
        return Result.ok(articleService.listArticleBacks(conditionVO, conditionVO.toPageQuery()));
    }

    /**
     * 添加或修改文章
     *
     * @param articleVO 文章信息
     * @return {@link Result<>}
     */
    @OptLog(optType = SAVE_OR_UPDATE)
    @Operation(summary = "添加或修改文章")
    @PostMapping("/admin/articles")
    public Result<Integer> saveOrUpdateArticle(@Valid @RequestBody ArticleVO articleVO) {
        return Result.ok(articleService.saveOrUpdateArticle(articleVO));
    }

    /** Writes a new immutable Markdown version; the article row stores only its pointer. */
    @OptLog(optType = SAVE_OR_UPDATE)
    @Operation(summary = "保存文章 Markdown 内容")
    @AccessLimit(seconds = 60, maxCount = 30)
    @PutMapping("/admin/articles/{articleId}/content")
    public Result<ArticleContentResponse> saveArticleContent(
            @PathVariable Integer articleId,
            @Valid @RequestBody ArticleContentRequest request) {
        return Result.ok(articleContentService.replace(articleId, request));
    }

    @Operation(summary = "查看文章内容版本历史")
    @GetMapping("/admin/articles/{articleId}/versions")
    public Result<ArticleContentVersionPage> listArticleContentVersions(
            @PathVariable Integer articleId,
            @RequestParam(required = false) String cursor,
            @RequestParam(required = false) Integer size) {
        return Result.ok(articleContentService.versions(articleId, cursor, size));
    }

    @OptLog(optType = SAVE_OR_UPDATE)
    @Operation(summary = "恢复文章内容版本")
    @AccessLimit(seconds = 60, maxCount = 10)
    @PostMapping("/admin/articles/{articleId}/versions/{version}/restore")
    public Result<ArticleContentResponse> restoreArticleContent(
            @PathVariable Integer articleId,
            @PathVariable Integer version,
            @RequestBody(required = false) ArticleContentRestoreRequest request) {
        return Result.ok(articleContentService.restore(articleId, version,
                request == null ? null : request.expectedVersion()));
    }

    /**
     * 修改文章置顶状态
     *
     * @param articleTopVO 文章置顶信息
     * @return {@link Result<>}
     */
    @OptLog(optType = UPDATE)
    @Operation(summary = "修改文章置顶")
    @PutMapping("/admin/articles/top")
    public Result<?> updateArticleTop(@Valid @RequestBody ArticleTopVO articleTopVO) {
        articleService.updateArticleTop(articleTopVO);
        return Result.ok();
    }

    /**
     * 恢复或删除文章
     *
     * @param deleteVO 逻辑删除信息
     * @return {@link Result<>}
     */
    @OptLog(optType = UPDATE)
    @Operation(summary = "恢复或删除文章")
    @PutMapping("/admin/articles")
    public Result<?> updateArticleDelete(@Valid @RequestBody DeleteVO deleteVO) {
        articleService.updateArticleDelete(deleteVO);
        return Result.ok();
    }

    /**
     * 上传文章图片
     *
     * @param file 文件
     * @return {@link Result<String>} 文章图片地址
     */
    @Operation(summary = "上传文章图片")
    @AccessLimit(seconds = 60, maxCount = 30)
    @Parameter(name = "file", description = "文章图片", required = true)
    @PostMapping("/admin/articles/images")
    public Result<String> saveArticleImages(MultipartFile file) {
        return Result.ok(mediaAssetStore.upload(file, FilePathEnum.ARTICLE.getPath()));
    }

    /**
     * 删除文章
     *
     * @param articleIdList 文章id列表
     * @return {@link Result<>}
     */
    @OptLog(optType = REMOVE)
    @Operation(summary = "物理删除文章")
    @DeleteMapping("/admin/articles")
    public Result<?> deleteArticles(@RequestBody List<Integer> articleIdList) {
        articleService.deleteArticles(articleIdList);
        return Result.ok();
    }

    /**
     * 根据id查看后台文章
     *
     * @param articleId 文章id
     * @return {@link Result<ArticleVO>} 后台文章
     */
    @Operation(summary = "根据id查看后台文章")
    @Parameter(name = "articleId", description = "文章id", required = true)
    @GetMapping("/admin/articles/{articleId}")
    public Result<ArticleVO> getArticleBackById(@PathVariable("articleId") Integer articleId) {
        return Result.ok(articleService.getArticleBackById(articleId));
    }

    /**
     * 根据id查看文章
     *
     * @param articleId 文章id
     * @return {@link Result<ArticleDTO>} 文章信息
     */
    @Operation(summary = "根据id查看文章")
    @Parameter(name = "articleId", description = "文章id", required = true)
    @GetMapping("/articles/{articleId}")
    public Result<ArticleDTO> getArticleById(@PathVariable("articleId") Integer articleId) {
        return Result.ok(articleService.getArticleById(articleId));
    }

    /** Streams public Markdown independently from article metadata. */
    @Operation(summary = "读取文章 Markdown 内容")
    @GetMapping(value = "/articles/{articleId}/content", produces = "text/markdown")
    public ResponseEntity<InputStreamResource> getArticleContent(
            @PathVariable Integer articleId,
            @RequestHeader HttpHeaders requestHeaders) {
        ContentAsset asset = articleContentService.currentPublicAsset(articleId);
        return streamContent(asset, () -> articleContentService.open(asset), requestHeaders, true);
    }

    /** Streams content for the editor, including drafts and private articles. */
    @Operation(summary = "读取后台文章 Markdown 内容")
    @GetMapping(value = "/admin/articles/{articleId}/content", produces = "text/markdown")
    public ResponseEntity<InputStreamResource> getAdminArticleContent(
            @PathVariable Integer articleId,
            @RequestHeader HttpHeaders requestHeaders) {
        ContentAsset asset = articleContentService.currentAsset(articleId);
        return streamContent(asset, () -> articleContentService.open(asset), requestHeaders, false);
    }

    /**
     * 根据条件查询文章
     *
     * @param condition 条件
     * @return {@link Result<ArticlePreviewListDTO>} 文章列表
     */
    @Operation(summary = "根据条件查询文章")
    @GetMapping("/articles/condition")
    public Result<ArticlePreviewListDTO> listArticlesByCondition(ArticleQueryVO condition) {
        return Result.ok(articleService.listArticlesByCondition(condition, condition.toPageQuery()));
    }

    /**
     * 搜索文章
     *
     * @param condition 条件
     * @return {@link Result<ArticleSearchDTO>} 文章列表
     */
    @Operation(summary = "搜索文章")
    @GetMapping("/articles/search")
    public Result<com.wzh.blog.web.CursorPageResult<ArticleSearchDTO>> listArticlesBySearch(
            @RequestParam(required = false, defaultValue = "") String keywords,
            CursorPageQueryVO pageQueryVO) {
        return Result.ok(articleService.listArticlesBySearch(keywords, pageQueryVO.toCursorPageQuery()));
    }

    /**
     * 点赞文章
     *
     * @param articleId 文章id
     * @return {@link Result<>}
     */
    @Operation(summary = "点赞文章")
    @AccessLimit(seconds = 60, maxCount = 10)
    @Parameter(name = "articleId", description = "文章id", required = true)
    @PostMapping("/articles/{articleId}/like")
    public Result<?> saveArticleLike(@PathVariable("articleId") Integer articleId) {
        articleService.saveArticleLike(articleId);
        return Result.ok();
    }

    private ResponseEntity<InputStreamResource> streamContent(ContentAsset asset,
                                                              Supplier<StorageObject> objectSupplier,
                                                              HttpHeaders requestHeaders,
                                                              boolean publicContent) {
        String etag = "\"" + asset.getChecksum() + "\"";
        long lastModified = asset.getUpdatedAt() == null
                ? (asset.getCreatedAt() == null ? 0L
                : asset.getCreatedAt().atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli())
                : asset.getUpdatedAt().atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli();
        if (requestHeaders.getIfNoneMatch().contains(etag)
                || (requestHeaders.getIfNoneMatch().isEmpty()
                && requestHeaders.getIfModifiedSince() >= 0
                && lastModified <= requestHeaders.getIfModifiedSince())) {
            return ResponseEntity.status(HttpStatus.NOT_MODIFIED)
                    .eTag(etag)
                    .lastModified(lastModified)
                    .header(HttpHeaders.CACHE_CONTROL, cacheControl(publicContent))
                    .build();
        }
        StorageObject object = objectSupplier.get();
        InputStream closeAware = new FilterInputStream(object.content()) {
            @Override
            public void close() throws IOException {
                IOException failure = null;
                try {
                    super.close();
                } catch (IOException exception) {
                    failure = exception;
                }
                try {
                    object.close();
                } catch (IOException exception) {
                    if (failure == null) {
                        failure = exception;
                    } else {
                        failure.addSuppressed(exception);
                    }
                }
                if (failure != null) {
                    throw failure;
                }
            }
        };
        MediaType contentType = MediaType.parseMediaType(asset.getContentType());
        return ResponseEntity.ok()
                .contentType(contentType)
                .contentLength(asset.getSizeBytes() == null ? object.metadata().sizeBytes() : asset.getSizeBytes())
                .eTag(etag)
                .lastModified(lastModified)
                .header(HttpHeaders.CACHE_CONTROL, cacheControl(publicContent))
                .body(new InputStreamResource(closeAware));
    }

    private String cacheControl(boolean publicContent) {
        return publicContent
                ? "public, max-age=60, stale-while-revalidate=300"
                : "no-store";
    }

}

