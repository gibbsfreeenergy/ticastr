package com.wzh.blog.handler;

import com.alibaba.fastjson2.JSON;
import com.wzh.blog.annotation.AccessLimit;
import com.wzh.blog.service.RateLimitStore;
import com.wzh.blog.security.BoundedInMemoryRateLimitStore;
import com.wzh.blog.util.IpUtils;
import com.wzh.blog.vo.Result;
import lombok.extern.log4j.Log4j2;
import org.springframework.dao.DataAccessException;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

import static com.wzh.blog.constant.CommonConst.APPLICATION_JSON;

/**
 * @author hnz
 * @date 2022/3/23 11:21
 * @description
 */
@Log4j2
public class WebSecurityHandler implements HandlerInterceptor {
    private final RateLimitStore rateLimitStore;
    private final BoundedInMemoryRateLimitStore fallbackRateLimitStore = new BoundedInMemoryRateLimitStore();

    public WebSecurityHandler(RateLimitStore rateLimitStore) {
        this.rateLimitStore = rateLimitStore;
    }

    @Override
    public boolean preHandle(HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse, Object handler) throws Exception {
        AccessLimit accessLimit = handler instanceof HandlerMethod hm
                ? hm.getMethodAnnotation(AccessLimit.class)
                : null;
        if (accessLimit == null && "POST".equalsIgnoreCase(httpServletRequest.getMethod())
                && "/login".equals(httpServletRequest.getRequestURI())) {
            accessLimit = new FixedAccessLimit(60, 10);
        }
        if (accessLimit != null) {
                long seconds = accessLimit.seconds();
                int maxCount = accessLimit.maxCount();
                // 关于key的生成规则可以自己定义 本项目需求是对每个方法都加上限流功能，如果你只是针对ip地址限流，那么key只需要只用ip就好
                String rawKey = httpServletRequest.getMethod() + ":"
                        + httpServletRequest.getRequestURI() + ":" + IpUtils.getIpAddress(httpServletRequest);
                String key = "rate-limit:" + HexFormat.of().formatHex(sha256(rawKey));
                // 从redis中获取用户访问的次数
                try {
                    // 此操作代表获取该key对应的值自增1后的结果
                    long q = rateLimitStore.increment(key, seconds);
                    if (q > maxCount) {
                        render(httpServletResponse, 429,
                                Result.fail("请求过于频繁，请稍候再试"));
                        log.warn("Rate limit exceeded for key {} ({} requests in {} seconds)", key, q, seconds);
                        return false;
                    }
                    return true;
                } catch (DataAccessException e) {
                    long fallbackCount = fallbackRateLimitStore.increment(key, seconds);
                    log.warn("Distributed rate limiter unavailable; using bounded local fallback");
                    if (fallbackCount > maxCount) {
                        render(httpServletResponse, 429,
                                Result.fail("请求过于频繁，请稍候再试"));
                        return false;
                    }
                    return true;
                }
        }
        return true;
    }

    private record FixedAccessLimit(int seconds, int maxCount) implements AccessLimit {
        @Override
        public Class<? extends java.lang.annotation.Annotation> annotationType() {
            return AccessLimit.class;
        }
    }

    private byte[] sha256(String value) {
        try {
            return MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private void render(HttpServletResponse response, int status, Result<?> result) throws Exception {
        response.setStatus(status);
        response.setContentType(APPLICATION_JSON);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        OutputStream out = response.getOutputStream();
        String str = JSON.toJSONString(result);
        out.write(str.getBytes(StandardCharsets.UTF_8));
        out.flush();
        out.close();
    }

}
