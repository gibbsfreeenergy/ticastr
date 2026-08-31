package com.wzh.blog.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.beans.factory.annotation.Value;
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
    public OpenAPI openAPI(@Value("${app.public-api-url}") String publicApiUrl) {
        return new OpenAPI()
                .info(new Info()
                        .title("Blog API")
                        .description("Ticastr blog service API")
                        .version("1.0")
                        .contact(new Contact()
                                .name("Blog maintainers")))
                .servers(List.of(new Server()
                        .url(publicApiUrl)
                        .description("Configured API origin")));
    }
}
