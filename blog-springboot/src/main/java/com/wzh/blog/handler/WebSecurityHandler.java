package com.wzh.blog.handler;

import com.alibaba.fastjson2.JSON;
import com.wzh.blog.annotation.AccessLimit;
import com.wzh.blog.service.RedisService;
import com.wzh.blog.util.IpUtils;
import com.wzh.blog.vo.Result;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

import static com.wzh.blog.constant.CommonConst.APPLICATION_JSON;

/**
 * @author hnz
 * @date 2022/3/23 11:21
 * @description
 */
@Log4j2
public class WebSecurityHandler implements HandlerInterceptor {
    @Autowired
    private RedisService redisService;

    @Override
    public boolean preHandle(HttpServletRequest httpServletRequest, HttpServletResponse httpServletResponse, Object handler) throws Exception {
        // 如果请求输入方法
        if (handler instanceof HandlerMethod) {
            HandlerMethod hm = (HandlerMethod) handler;
            // 获取方法中的注解,看是否有该注解
            AccessLimit accessLimit = hm.getMethodAnnotation(AccessLimit.class);
            if (accessLimit != null) {
                long seconds = accessLimit.seconds();
                int maxCount = accessLimit.maxCount();
                // 关于key的生成规则可以自己定义 本项目需求是对每个方法都加上限流功能，如果你只是针对ip地址限流，那么key只需要只用ip就好
                String key = "rate-limit:" + httpServletRequest.getMethod() + ":"
                        + httpServletRequest.getRequestURI() + ":" + IpUtils.getIpAddress(httpServletRequest);
                // 从redis中获取用户访问的次数
                try {
                    // 此操作代表获取该key对应的值自增1后的结果
                    long q = redisService.incrExpire(key, seconds);
                    if (q > maxCount) {
                        render(httpServletResponse, 429,
                                Result.fail("请求过于频繁，请稍候再试"));
                        log.warn("Rate limit exceeded for key {} ({} requests in {} seconds)", key, q, seconds);
                        return false;
                    }
                    return true;
                } catch (DataAccessException e) {
                    log.error("Rate limiter is unavailable", e);
                    render(httpServletResponse, HttpServletResponse.SC_SERVICE_UNAVAILABLE,
                            Result.fail("服务暂时不可用，请稍后重试"));
                    return false;
                }
            }
        }
        return true;
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
