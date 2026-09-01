package com.wzh.blog.controller;

import com.wzh.blog.administration.StorageConfigAdminService;
import com.wzh.blog.security.CurrentUser;
import com.wzh.blog.vo.StorageConfigListResponse;
import com.wzh.blog.vo.StorageConfigSummaryVO;
import com.wzh.blog.vo.StorageProviderSelectionResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class StorageProviderControllerTest {

    private StorageConfigAdminService service;
    private CurrentUser currentUser;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        service = mock(StorageConfigAdminService.class);
        currentUser = mock(CurrentUser.class);
        when(currentUser.id()).thenReturn(7);
        mvc = MockMvcBuilders.standaloneSetup(new StorageProviderController(service, currentUser)).build();
    }

    @Test
    void configRoutesReturnRedactedSummaries() throws Exception {
        StorageConfigSummaryVO summary = new StorageConfigSummaryVO(1L, "local", "local", true, true, false,
                null, null, null, "C:/storage", "https://cdn.example.com", null, null);
        when(service.list()).thenReturn(new StorageConfigListResponse(1L, List.of(summary)));
        when(service.create(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any())).thenReturn(summary);
        when(service.update(org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any())).thenReturn(summary);
        when(service.activate(org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.any())).thenReturn(summary);

        mvc.perform(get("/admin/storage/configs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.configs[0].accessKeySecret").doesNotExist())
                .andExpect(jsonPath("$.data.configs[0].accessKeySecretCiphertext").doesNotExist());
        mvc.perform(post("/admin/storage/configs").contentType(MediaType.APPLICATION_JSON).content(localRequest()))
                .andExpect(status().isOk());
        mvc.perform(put("/admin/storage/configs/1").contentType(MediaType.APPLICATION_JSON).content(localRequest()))
                .andExpect(status().isOk());
        mvc.perform(delete("/admin/storage/configs/1")).andExpect(status().isOk());
        mvc.perform(post("/admin/storage/configs/1/validate")).andExpect(status().isOk());
        mvc.perform(post("/admin/storage/configs/1/activate")).andExpect(status().isOk());
        mvc.perform(post("/admin/storage/configs/1/usage")).andExpect(status().isOk());

        verify(service).delete(1L);
        verify(service).validate(1L);
        verify(service).refreshUsage(1L);
    }

    @Test
    void deprecatedProviderEndpointsRemainCompatibilityAdapters() throws Exception {
        when(service.current()).thenReturn(new StorageProviderSelectionResponse("local", 1L, List.of("local", "oss")));

        mvc.perform(get("/admin/storage/provider")).andExpect(status().isOk());
        mvc.perform(get("/admin/storage/providers")).andExpect(status().isOk());
        mvc.perform(post("/admin/storage/providers/local/validate")).andExpect(status().isOk());
        mvc.perform(put("/admin/storage/provider").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"provider\":\"local\"}"))
                .andExpect(status().isOk());
        verify(service).switchProvider("local", 7);
    }

    private String localRequest() {
        return "{\"name\":\"local\",\"provider\":\"local\",\"localRoot\":\"C:/storage\",\"publicUrl\":\"https://cdn.example.com\"}";
    }
}
