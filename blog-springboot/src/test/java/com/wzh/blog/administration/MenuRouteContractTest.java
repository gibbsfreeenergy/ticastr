package com.wzh.blog.administration;

import com.wzh.blog.entity.Menu;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MenuRouteContractTest {

    @Test
    void derivesStableMetadataFromLegacyRouteFields() {
        Menu menu = Menu.builder()
                .id(10)
                .path("/article-list")
                .component("/article/ArticleList.vue")
                .name("文章管理")
                .build();

        MenuRouteContract.normalize(menu);

        assertThat(menu.getCode()).isEqualTo("articleList");
        assertThat(menu.getRouteKey()).isEqualTo("articleList");
        assertThat(menu.getSection()).isEqualTo("content");
        assertThat(menu.getIconKey()).isEqualTo("list");
    }

    @Test
    void keepsExplicitMetadataWhenDisplayFieldsChange() {
        Menu menu = Menu.builder()
                .id(11)
                .path("/custom")
                .name("任意名称")
                .code("settings.custom")
                .routeKey("settings.custom")
                .section("settings")
                .iconKey("grid")
                .build();

        MenuRouteContract.normalize(menu);

        assertThat(menu.getRouteKey()).isEqualTo("settings.custom");
        assertThat(menu.getSection()).isEqualTo("settings");
        assertThat(menu.getIconKey()).isEqualTo("grid");
    }
}
