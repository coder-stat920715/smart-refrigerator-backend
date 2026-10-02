package com.smartfridge.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI smartFridgeOpenApi() {
        return new OpenAPI().info(new Info()
                .title("Smart Refrigerator IoT & Telemetry Backend API")
                .version("1.0.0")
                .description("Device fleet management, telemetry queries, fault tracking, remote commands "
                        + "and OTA firmware management for MCU-driven smart refrigerators.")
                .contact(new Contact().name("Platform Engineering"))
                .license(new License().name("Apache 2.0")));
    }
}
