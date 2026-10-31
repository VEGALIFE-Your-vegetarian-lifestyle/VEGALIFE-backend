package com.vegalife.config;

import com.vegalife.shared.openapi.OpenApiSchemeNames;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

  @Bean
  public OpenAPI vegalifeOpenApi() {
    return new OpenAPI()
        .info(
            new Info()
                .title("Vegalife API")
                .version("1.0.0")
                .description(
                    "REST API for the Vegalife vegan social platform. Operations carrying the"
                        + " bearerAuth requirement expect"
                        + " `Authorization: Bearer <accessToken>`; tokens are issued by"
                        + " POST /api/auth/login."))
        .components(
            new Components()
                .addSecuritySchemes(
                    OpenApiSchemeNames.BEARER_AUTH,
                    new SecurityScheme()
                        .name(OpenApiSchemeNames.BEARER_AUTH)
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")
                        .description("JWT access token returned by POST /api/auth/login.")));
  }
}
