package com.wzh.blog.handler;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.authorization.AuthorizationResult;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * Adapts the database-backed URL/role mapping to Spring Security's current
 * AuthorizationManager API.
 */
@Component
public class DynamicAuthorizationManager implements AuthorizationManager<RequestAuthorizationContext> {

    private final FilterInvocationSecurityMetadataSourceImpl securityMetadataSource;
    public DynamicAuthorizationManager(FilterInvocationSecurityMetadataSourceImpl securityMetadataSource) {
        this.securityMetadataSource = securityMetadataSource;
    }

    @Override
    public AuthorizationResult authorize(Supplier<? extends Authentication> authentication,
                                         RequestAuthorizationContext context) {
        HttpServletRequest request = context.getRequest();
        Optional<Collection<String>> requiredRoles = securityMetadataSource.findRequiredRoles(request);
        if (requiredRoles.isEmpty()) {
            return new AuthorizationDecision(false);
        }
        if (requiredRoles.get().isEmpty()) {
            return new AuthorizationDecision(true);
        }
        Authentication currentAuthentication = authentication.get();
        boolean granted = currentAuthentication != null && currentAuthentication.getAuthorities().stream()
                .map(grantedAuthority -> grantedAuthority.getAuthority())
                .anyMatch(requiredRoles.get()::contains);
        return new AuthorizationDecision(granted);
    }
}
