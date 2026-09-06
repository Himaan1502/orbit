package com.orbit.platform.common.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.Contact;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI orbitOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("ORBIT API")
                        .description("Personal Intelligence & Execution Platform API")
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("ORBIT Team")));
    }
}
