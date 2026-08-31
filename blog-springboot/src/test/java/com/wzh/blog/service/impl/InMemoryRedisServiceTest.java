package com.wzh.blog.service.impl;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InMemoryRedisServiceTest {

    @Test
    void evictsOldKeysWhenTheFallbackReachesItsGlobalLimit() {
        InMemoryRedisService service = new InMemoryRedisService();

        for (int index = 0; index <= InMemoryRedisService.MAX_KEY_COUNT; index++) {
            service.set("key-" + index, index);
        }

        assertThat(service.hasKey("key-0")).isFalse();
        assertThat(service.get("key-" + InMemoryRedisService.MAX_KEY_COUNT))
                .isEqualTo(InMemoryRedisService.MAX_KEY_COUNT);
    }

    @Test
    void boundsCollectionsAndRejectsUnboundedBitmapOffsets() {
        InMemoryRedisService service = new InMemoryRedisService();

        for (int index = 0; index < InMemoryRedisService.MAX_COLLECTION_ENTRIES + 100; index++) {
            service.hSet("hash", "field-" + index, index);
            service.sAdd("set", index);
            service.lPush("list", index);
            service.zIncr("sorted", index, 1d);
        }

        assertThat(service.hGetAll("hash")).hasSize(InMemoryRedisService.MAX_COLLECTION_ENTRIES);
        assertThat(service.sSize("set")).isEqualTo(InMemoryRedisService.MAX_COLLECTION_ENTRIES);
        assertThat(service.lSize("list")).isEqualTo(InMemoryRedisService.MAX_COLLECTION_ENTRIES);
        assertThat(service.zAllScore("sorted")).hasSize(InMemoryRedisService.MAX_COLLECTION_ENTRIES);
        assertThat(service.bitGet("bits", InMemoryRedisService.MAX_BITMAP_BITS + 1)).isFalse();
        assertThatThrownBy(() -> service.bitAdd("bits", InMemoryRedisService.MAX_BITMAP_BITS + 1, true))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
