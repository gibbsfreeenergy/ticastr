package com.wzh.blog.handler;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.wzh.blog.exception.BizException;
import com.wzh.blog.util.PageUtils;
import org.springframework.web.servlet.HandlerInterceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import static com.wzh.blog.constant.CommonConst.*;

/**
 * 分页拦截器
 *
 * @author yezhiqiu
 * @date 2021/07/18
 **/
public class PageableHandlerInterceptor implements HandlerInterceptor {

    static final long MAX_PAGE_SIZE = 100L;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String currentPage = request.getParameter(CURRENT);
        String pageSize = request.getParameter(SIZE);
        if (currentPage == null && pageSize == null) {
            return true;
        }
        long current = parsePositive(CURRENT, currentPage, 1L);
        long size = parsePositive(SIZE, pageSize, Long.parseLong(DEFAULT_SIZE));
        if (size > MAX_PAGE_SIZE) {
            throw new BizException("分页条数不能超过" + MAX_PAGE_SIZE);
        }
        PageUtils.setCurrentPage(new Page<>(current, size));
        return true;
    }

    private long parsePositive(String name, String value, long defaultValue) {
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        try {
            long parsed = Long.parseLong(value);
            if (parsed < 1) {
                throw new BizException(name + "必须大于0");
            }
            return parsed;
        } catch (NumberFormatException exception) {
            throw new BizException(name + "必须是整数");
        }
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        PageUtils.remove();
    }

}
