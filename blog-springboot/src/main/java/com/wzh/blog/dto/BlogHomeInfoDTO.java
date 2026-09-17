package com.wzh.blog.dto;

import com.wzh.blog.vo.PageVO;
import com.wzh.blog.vo.WebsiteConfigVO;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 首页所需的博客信息。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BlogHomeInfoDTO {

    private Long articleCount;

    private WebsiteConfigVO websiteConfig;

    private List<PageVO> pageList;
}
