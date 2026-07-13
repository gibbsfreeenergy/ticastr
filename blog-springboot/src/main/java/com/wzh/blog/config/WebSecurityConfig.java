package com.wzh.blog.config;

import com.wzh.blog.handler.AccessDeniedHandlerImpl;
import com.wzh.blog.handler.AuthenticationEntryPointImpl;
import com.wzh.blog.handler.AuthenticationFailHandlerImpl;
import com.wzh.blog.handler.AuthenticationSuccessHandlerImpl;
import com.wzh.blog.handler.CsrfCookieFilter;
import com.wzh.blog.handler.DynamicAuthorizationManager;
import com.wzh.blog.handler.LogoutSuccessHandlerImpl;
import com.wzh.blog.handler.MonitoringTokenFilter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.session.FindByIndexNameSessionRepository;
import org.springframework.session.Session;
import org.springframework.session.security.SpringSessionBackedSessionRegistry;

/** Security configuration compatible with Spring Security 7. */
@Configuration
@EnableWebSecurity
public class WebSecurityConfig {

    @Autowired
    private AuthenticationEntryPointImpl authenticationEntryPoint;
    @Autowired
    private AccessDeniedHandlerImpl accessDeniedHandler;
    @Autowired
    private AuthenticationSuccessHandlerImpl authenticationSuccessHandler;
    @Autowired
    private AuthenticationFailHandlerImpl authenticationFailHandler;
    @Autowired
    private LogoutSuccessHandlerImpl logoutSuccessHandler;

    @Bean
    public SpringSessionBackedSessionRegistry<? extends Session> sessionRegistry(
            FindByIndexNameSessionRepository<? extends Session> sessionRepository) {
        return new SpringSessionBackedSessionRegistry<>(sessionRepository);
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
                                                   SpringSessionBackedSessionRegistry<? extends Session> sessionRegistry) throws Exception {
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
                        .sessionRegistry(sessionRegistry));
        return http.build();
    }
}
