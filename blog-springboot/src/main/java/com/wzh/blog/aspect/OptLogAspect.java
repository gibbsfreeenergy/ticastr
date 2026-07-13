package com.wzh.blog.aspect;

import com.wzh.blog.annotation.OptLog;
import com.wzh.blog.entity.OperationLog;
import com.wzh.blog.service.AuditLogService;
import com.wzh.blog.util.AuditLogSanitizer;
import com.wzh.blog.util.IpUtils;
import com.wzh.blog.util.UserUtils;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.Operation;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;

import jakarta.servlet.http.HttpServletRequest;
import java.lang.reflect.Method;
import java.util.Objects;

/**
 * 操作日志切面处理
 *
 * @author yezhiqiu
 * @date 2021/07/27
 */
@Aspect
@Component
@Log4j2
public class OptLogAspect {

    @Autowired
    private AuditLogService auditLogService;

    private final AuditLogSanitizer sanitizer;

    public OptLogAspect(@Value("${app.audit.max-payload-length:8000}") int maxPayloadLength) {
        this.sanitizer = new AuditLogSanitizer(maxPayloadLength);
    }

    /**
     * 设置操作日志切入点 记录操作日志 在注解的位置切入代码
     */
    @Pointcut("@annotation(com.wzh.blog.annotation.OptLog)")
    public void optLogPointCut() {}


    /**
     * 正常返回通知，拦截用户操作日志，连接点正常执行完成后执行， 如果连接点抛出异常，则不会执行
     *
     * @param joinPoint 切入点
     * @param keys      返回结果
     */
    @AfterReturning(value = "optLogPointCut()", returning = "keys")
    @SuppressWarnings("unchecked")
    public void saveOptLog(JoinPoint joinPoint, Object keys) {
        // 获取RequestAttributes
        RequestAttributes requestAttributes = RequestContextHolder.getRequestAttributes();
        // 从获取RequestAttributes中获取HttpServletRequest的信息
        HttpServletRequest request = (HttpServletRequest) Objects.requireNonNull(requestAttributes).resolveReference(RequestAttributes.REFERENCE_REQUEST);
        OperationLog operationLog = new OperationLog();
        // 从切面织入点处通过反射机制获取织入点处的方法
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        // 获取切入点所在的方法
        Method method = signature.getMethod();
        // 获取操作
        Class<?> declaringType = signature.getDeclaringType();
        Tag tag = declaringType.getAnnotation(Tag.class);
        Operation operation = method.getAnnotation(Operation.class);
        OptLog optLog = method.getAnnotation(OptLog.class);
        // 操作模块
        operationLog.setOptModule(tag == null ? signature.getDeclaringType().getSimpleName() : tag.name());
        // 操作类型
        operationLog.setOptType(optLog.optType());
        // 操作描述
        operationLog.setOptDesc(operation == null ? method.getName() : operation.summary());
        // 获取请求的类名
        String className = joinPoint.getTarget().getClass().getName();
        // 获取请求的方法名
        String methodName = method.getName();
        methodName = className + "." + methodName;
        // 请求方式
        operationLog.setRequestMethod(Objects.requireNonNull(request).getMethod());
        // 请求方法
        operationLog.setOptMethod(methodName);
        // 请求参数
        operationLog.setRequestParam(sanitizer.sanitize(joinPoint.getArgs()));
        // 返回结果
        operationLog.setResponseData(sanitizer.sanitize(keys));
        // 请求用户ID
        operationLog.setUserId(UserUtils.getLoginUser().getId());
        // 请求用户
        operationLog.setNickname(UserUtils.getLoginUser().getNickname());
        // 请求IP
        String ipAddress = IpUtils.getIpAddress(request);
        operationLog.setIpAddress(ipAddress);
        operationLog.setIpSource(IpUtils.getIpSource(ipAddress));
        // 请求URL
        operationLog.setOptUrl(request.getRequestURI());
        try {
            auditLogService.persist(operationLog);
        } catch (RuntimeException exception) {
            log.warn("Unable to schedule operation audit log", exception);
        }
    }

}
