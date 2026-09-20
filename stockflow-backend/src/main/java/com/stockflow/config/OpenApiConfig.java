package com.stockflow.config;

import io.swagger.v3.oas.models.*;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.security.*;
import io.swagger.v3.oas.models.servers.Server;
import java.util.List;
import java.util.Map;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.info.BuildProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {
    @Bean OpenAPI stockflowApi(ObjectProvider<BuildProperties> properties) {
        var build = properties.getIfAvailable();
        return new OpenAPI().info(new Info().title("StockFlow API")
            .version(build == null ? "development" : build.getVersion())
            .description("Log in using POST /api/auth/login, then paste the returned token into Authorize. "
                + "ADMIN and STAFF can use operational endpoints. Only ADMIN can manage users or delete master data. "
                + "Inactive accounts and expired tokens are rejected. Passwords are write-only; hashes are never returned. "
                + "Pagination is zero-based. Inventory and order transitions retain their existing validation and transaction rules."))
            .servers(List.of(new Server().url("/")))
            .components(new Components().addSecuritySchemes("bearerAuth", new SecurityScheme()
                .type(SecurityScheme.Type.HTTP).scheme("bearer").bearerFormat("JWT")));
    }

    @Bean OpenApiCustomizer permissionsDocumentation() {
        return api -> api.getPaths().forEach((path, item) -> item.readOperationsMap().forEach((method, operation) -> {
            boolean publicEndpoint = (method == PathItem.HttpMethod.POST && path.equals("/api/auth/login"))
                || (method == PathItem.HttpMethod.GET && List.of("/api/health", "/api/health/ready", "/api/version").contains(path));
            boolean admin = path.equals("/api/users") || path.startsWith("/api/users/") || method == PathItem.HttpMethod.DELETE;
            String permission = publicEndpoint ? "Public" : admin ? "ADMIN only" : "ADMIN or STAFF";
            String section = path.split("/")[2];
            String tag = Map.of("auth", "Authentication", "users", "Users", "health", "Deployment",
                "version", "Deployment", "dashboard", "Dashboard", "reports", "Dashboard")
                .getOrDefault(section, section.replace('-', ' '));
            operation.setTags(List.of(tag));
            operation.setSummary(method + " " + path);
            operation.setDescription("Permission: " + permission + "." + details(path));
            operation.addExtension("x-roles", publicEndpoint ? List.of() : admin ? List.of("ADMIN") : List.of("ADMIN", "STAFF"));
            operation.setSecurity(publicEndpoint ? List.of() : List.of(new SecurityRequirement().addList("bearerAuth")));
            if ((method == PathItem.HttpMethod.POST && !path.equals("/api/auth/login")) || method == PathItem.HttpMethod.DELETE) {
                var success = operation.getResponses().remove("200");
                if (success != null) operation.getResponses().addApiResponse(
                    method == PathItem.HttpMethod.DELETE ? "204" : "201", success);
            }
            if (!publicEndpoint) {
                operation.getResponses().addApiResponse("401", new ApiResponse().description("Missing, expired or invalid bearer token, or inactive account"));
                operation.getResponses().addApiResponse("403", new ApiResponse().description("Role does not permit this operation"));
            }
        }));
    }

    private static String details(String path) {
        if (path.startsWith("/api/users")) return " Create accepts ADMIN or STAFF for compatibility; the UI creates STAFF. Activation preserves records, blocks existing sessions, and prevents self/last-admin deactivation.";
        if (path.startsWith("/api/sales-orders")) return " DRAFT → CONFIRMED → FULFILLED, or cancellation before fulfillment. Fulfillment deducts stock atomically once.";
        if (path.startsWith("/api/purchase-orders")) return " DRAFT → ORDERED → RECEIVED, or cancellation before receipt. Receipt adds stock atomically once.";
        if (path.startsWith("/api/products")) return " Stock changes use the dedicated stock endpoint; ordinary product edits do not change quantity. Referenced records cannot be deleted.";
        if (path.startsWith("/api/inventory")) return " Movements maintain immutable stock history and reject negative stock.";
        if (path.equals("/api/auth/login")) return " Returns a short-lived JWT. Invalid credentials and inactive accounts return 401.";
        return "";
    }
}
