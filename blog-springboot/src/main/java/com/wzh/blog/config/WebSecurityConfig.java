package com.wzh.blog.config;

import com.wzh.blog.handler.AccessDeniedHandlerImpl;
import com.wzh.blog.handler.AuthenticationEntryPointImpl;
import com.wzh.blog.handler.AuthenticationFailHandlerImpl;
import com.wzh.blog.handler.AuthenticationSuccessHandlerImpl;
import com.wzh.blog.handler.CsrfCookieFilter;
import com.wzh.blog.handler.DynamicAuthorizationManager;
import com.wzh.blog.handler.LogoutSuccessHandlerImpl;
import com.wzh.blog.handler.MonitoringTokenFilter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.core.session.SessionRegistryImpl;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.springframework.session.security.SpringSessionBackedSessionRegistry;
import org.springframework.beans.factory.annotation.Value;

/** Security configuration compatible with Spring Security 7. */
@Configuration
@EnableWebSecurity
public class WebSecurityConfig {

    private final AuthenticationEntryPointImpl authenticationEntryPoint;
    private final AccessDeniedHandlerImpl accessDeniedHandler;
    private final AuthenticationSuccessHandlerImpl authenticationSuccessHandler;
    private final AuthenticationFailHandlerImpl authenticationFailHandler;
    private final LogoutSuccessHandlerImpl logoutSuccessHandler;
    private final String deploymentProfile;

    public WebSecurityConfig(AuthenticationEntryPointImpl authenticationEntryPoint,
                             AccessDeniedHandlerImpl accessDeniedHandler,
                             AuthenticationSuccessHandlerImpl authenticationSuccessHandler,
                             AuthenticationFailHandlerImpl authenticationFailHandler,
                             LogoutSuccessHandlerImpl logoutSuccessHandler,
                             @Value("${app.deployment-profile:local}") String deploymentProfile) {
        this.authenticationEntryPoint = authenticationEntryPoint;
        this.accessDeniedHandler = accessDeniedHandler;
        this.authenticationSuccessHandler = authenticationSuccessHandler;
        this.authenticationFailHandler = authenticationFailHandler;
        this.logoutSuccessHandler = logoutSuccessHandler;
        this.deploymentProfile = deploymentProfile;
    }

    @Bean
    @ConditionalOnBean(FindByIndexNameSessionRepository.class)
    public SpringSessionBackedSessionRegistry<? extends Session> springSessionRegistry(
            FindByIndexNameSessionRepository<? extends Session> sessionRepository) {
        return new SpringSessionBackedSessionRegistry<>(sessionRepository);
    }

    @Bean
    @ConditionalOnProperty(name = "spring.session.store-type", havingValue = "none", matchIfMissing = true)
    public SessionRegistry localSessionRegistry() {
        return new SessionRegistryImpl();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   DynamicAuthorizationManager dynamicAuthorizationManager,
                                                   CsrfCookieFilter csrfCookieFilter,
                                                   MonitoringTokenFilter monitoringTokenFilter,
                                                   SessionRegistry sessionRegistry) throws Exception {
        http
                .formLogin(formLogin -> formLogin
                        .loginProcessingUrl("/login")
                        .successHandler(authenticationSuccessHandler)
                        .failureHandler(authenticationFailHandler))
                .logout(logout -> logout
                        .logoutUrl("/logout")
                        .logoutSuccessHandler(logoutSuccessHandler))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers("/login", "/actuator/health/**", "/actuator/prometheus", "/uploads/**", "/websocket").permitAll()
                        .anyRequest().access(dynamicAuthorizationManager))
                .cors(Customizer.withDefaults())
                .csrf(csrf -> csrf
                        .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                        .ignoringRequestMatchers("/login"))
                .addFilterAfter(csrfCookieFilter, CsrfFilter.class)
                .addFilterBefore(monitoringTokenFilter, AuthorizationFilter.class)
                .exceptionHandling(exceptionHandling -> exceptionHandling
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .sessionManagement(sessionManagement -> sessionManagement
                        .maximumSessions(20)
                        .sessionRegistry(sessionRegistry))
                .headers(headers -> {
                    headers.contentTypeOptions(Customizer.withDefaults());
                    headers.frameOptions(frame -> frame.deny());
                    headers.referrerPolicy(referrer -> referrer.policy(
                            org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN));
                    headers.contentSecurityPolicy(csp -> csp.policyDirectives(
                            "default-src 'self'; base-uri 'self'; object-src 'none'; "
                                    + "frame-ancestors 'none'; img-src 'self' data: https:; "
                                    + "style-src 'self' 'unsafe-inline'; script-src 'self' https://ssl.captcha.qq.com "
                                    + "https://connect.qq.com https://tjs.sjs.sinajs.cn; "
                                    + "connect-src 'self' https: wss:; font-src 'self' data:"));
                    if (isProductionLike()) {
                        headers.httpStrictTransportSecurity(hsts -> hsts
                                .includeSubDomains(true).maxAgeInSeconds(31_536_000));
                    }
                });
        return http.build();
    }

    private boolean isProductionLike() {
        String profile = deploymentProfile == null ? "" : deploymentProfile.toLowerCase(java.util.Locale.ROOT);
        return profile.equals("production") || profile.equals("production-like") || profile.equals("staging");
    }
}
