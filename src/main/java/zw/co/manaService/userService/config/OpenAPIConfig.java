package zw.co.manaService.userService.config;

import io.swagger.v3.oas.annotations.ExternalDocumentation;
import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Contact;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.info.License;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@OpenAPIDefinition(info = @Info(
        title = "Mana Bank APP API",
        description = "This is a Spring Boot User Management Microservice API",
        version = "1.0.1",

        contact = @Contact(
                name = "Tinodashe Kayenie",
                email = "superlightintellex@gmail.com",
                url = "https://github.com/slitechnologies/User Management Microservice"
        ),
        license = @License(
                name = "sli Technologies",
                url = "https://github.com/slitechnologies/User Management Microservice"
        )
),
        externalDocs = @ExternalDocumentation(
                description="The sli Technologies User Microservice App Documentation",
                url = "https://github.com/slitechnologies/User Management Microservice"
        )

)
@Configuration
@SuppressWarnings("unused")
public class OpenAPIConfig {
    @Bean
    public OpenAPI customOpenAPI() {
        // Create the Security Scheme for bearer token authentication
        SecurityScheme securityScheme = new SecurityScheme()
                .type(SecurityScheme.Type.HTTP)
                .scheme("bearer")
                .bearerFormat("JWT")
                .in(SecurityScheme.In.HEADER)
                .name("Authorization");

        // Create the Security Requirement for bearerAuth
        SecurityRequirement securityRequirement = new SecurityRequirement().addList("bearerAuth");

        return new OpenAPI()
                .components(new Components()
                        .addSecuritySchemes("bearerAuth", securityScheme))
                .addSecurityItem(securityRequirement);
    }
}

