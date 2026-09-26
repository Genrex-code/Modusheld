package com.modushield.gateway.auth;

import com.modushield.gateway.error.ErrorResponseWriter;
import com.modushield.gateway.policy.PolicyDecision;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Optional;

@Component
public class JwtAuthenticationFilter implements GlobalFilter, Ordered {

    public static final String AUTHENTICATED_USER_ATTRIBUTE = "modushield.authenticatedUser";
    private static final String BEARER_PREFIX = "Bearer ";
    private static final int ORDER = 40;

    private final JwtService jwtService;
    private final ErrorResponseWriter errorResponseWriter;

    public JwtAuthenticationFilter(JwtService jwtService, ErrorResponseWriter errorResponseWriter) {
        this.jwtService = jwtService;
        this.errorResponseWriter = errorResponseWriter;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        if (!isProductPath(exchange.getRequest().getPath().value())) {
            return chain.filter(exchange);
        }

        List<String> authorizationHeaders = exchange.getRequest().getHeaders().get(HttpHeaders.AUTHORIZATION);
        if (authorizationHeaders == null || authorizationHeaders.size() != 1) {
            return unauthorized(exchange);
        }

        String header = authorizationHeaders.get(0);
        if (header == null || !header.startsWith(BEARER_PREFIX)) {
            return unauthorized(exchange);
        }
        String serializedToken = header.substring(BEARER_PREFIX.length()).trim();
        if (serializedToken.isEmpty() || serializedToken.contains(" ")) {
            return unauthorized(exchange);
        }

        Optional<JwtService.AuthenticatedUser> authenticated = jwtService.validate(serializedToken);
        if (authenticated.isEmpty()) {
            return unauthorized(exchange);
        }

        JwtService.AuthenticatedUser user = authenticated.get();
        if (!isReadMethod(exchange.getRequest().getMethod()) && user.role() != UserRole.ADMIN) {
            return errorResponseWriter.write(exchange, new PolicyDecision(
                    false,
                    HttpStatus.FORBIDDEN,
                    "INSUFFICIENT_PERMISSIONS",
                    "AUTHORIZATION",
                    "Administrator role is required for this operation"));
        }

        exchange.getAttributes().put(AUTHENTICATED_USER_ATTRIBUTE, user);
        ServerWebExchange authenticatedExchange = exchange.mutate()
                .request(request -> request
                        .headers(headers -> {
                            headers.remove(HttpHeaders.AUTHORIZATION);
                            headers.remove("X-Authenticated-User");
                            headers.remove("X-Authenticated-Role");
                        })
                        .header("X-Authenticated-User", user.username())
                        .header("X-Authenticated-Role", user.role().name()))
                .build();
        return chain.filter(authenticatedExchange);
    }

    @Override
    public int getOrder() {
        return ORDER;
    }

    private boolean isProductPath(String path) {
        return "/api/products".equals(path) || path.startsWith("/api/products/");
    }

    private boolean isReadMethod(HttpMethod method) {
        return HttpMethod.GET.equals(method);
    }

    private Mono<Void> unauthorized(ServerWebExchange exchange) {
        return errorResponseWriter.write(exchange, new PolicyDecision(
                false,
                HttpStatus.UNAUTHORIZED,
                "INVALID_TOKEN",
                "AUTHENTICATION",
                "Missing, invalid or expired bearer token"));
    }
}
