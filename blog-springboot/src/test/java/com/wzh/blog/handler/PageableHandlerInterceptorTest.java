package com.wzh.blog.handler;

import com.wzh.blog.exception.BizException;
import com.wzh.blog.web.PaginationContext;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PageableHandlerInterceptorTest {

    private final PaginationContext paginationContext = new PaginationContext();
    private final PageableHandlerInterceptor interceptor = new PageableHandlerInterceptor(paginationContext);

    @Test
    void acceptsBoundedPagination() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setParameter("current", "3");
        request.setParameter("size", "25");

        interceptor.preHandle(request, new MockHttpServletResponse(), new Object());

        assertThat(paginationContext.getCurrent()).isEqualTo(3);
        assertThat(paginationContext.getSize()).isEqualTo(25);
        assertThat(paginationContext.getOffset()).isEqualTo(50);
    }

    @Test
    void appliesDefaultCurrentWhenOnlySizeIsProvided() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setParameter("size", "20");

        interceptor.preHandle(request, new MockHttpServletResponse(), new Object());

        assertThat(paginationContext.getCurrent()).isEqualTo(1);
        assertThat(paginationContext.getSize()).isEqualTo(20);
    }

    @Test
    void rejectsInvalidOrUnboundedPagination() {
        MockHttpServletRequest nonNumeric = new MockHttpServletRequest();
        nonNumeric.setParameter("current", "abc");
        assertThatThrownBy(() -> interceptor.preHandle(nonNumeric, new MockHttpServletResponse(), new Object()))
                .isInstanceOf(BizException.class)
                .hasMessage("current必须是整数");

        MockHttpServletRequest tooLarge = new MockHttpServletRequest();
        tooLarge.setParameter("size", "101");
        assertThatThrownBy(() -> interceptor.preHandle(tooLarge, new MockHttpServletResponse(), new Object()))
                .isInstanceOf(BizException.class)
                .hasMessage("分页条数不能超过100");
    }
}
