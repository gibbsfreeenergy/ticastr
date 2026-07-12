package com.wzh.blog.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * OpenAPI 3 configuration. The class name is retained to avoid breaking any
 * existing component scans or external references to the former Knife4j setup.
 */
@Configuration
public class Knife4jConfig {

    @Bean
    public OpenAPI openAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Blog API")
                        .description("Ticastr blog service API")
                        .version("1.0")
                        .termsOfService("https://ticastr.com/api")
                        .contact(new Contact()
                                .name("Ticstar")
                                .url("https://github.com/gibbsfreeenergy")
                                .email("1036421779@qq.com")))
                .servers(List.of(new Server()
                        .url("https://ticastr.com")
                        .description("Production")));
    }
}
