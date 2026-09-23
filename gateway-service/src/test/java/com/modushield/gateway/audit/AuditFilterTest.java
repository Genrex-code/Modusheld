package com.modushield.gateway.audit;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.modushield.gateway.policy.PolicyDecision;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AuditFilterTest {

    private final AuditFilter filter = new AuditFilter();

    @Test
    void logsOneAllowedEventWithoutLeakingTheApiKey() {
        MockServerWebExchange exchange = exchange("audit-allow", "never-log-this-key");

        List<String> events = captureEvents(() -> filter.filter(exchange, filtered -> {
            filtered.getResponse().setStatusCode(HttpStatus.OK);
            return filtered.getResponse().setComplete();
        }).block());

        assertThat(events).singleElement().satisfies(event -> assertThat(event)
                .contains("requestId=audit-allow")
                .contains("method=GET")
                .contains("path=/api/products")
                .contains("decision=ALLOW")
                .contains("rule=ROUTE_AND_KEY")
                .contains("status=200")
                .contains("apiKeyId=demo-client")
                .doesNotContain("never-log-this-key"));
    }

    @Test
    void logsTheGatewayRuleForARejectedRequest() {
        MockServerWebExchange exchange = exchange("audit-deny", null);

        List<String> events = captureEvents(() -> filter.filter(exchange, filtered -> {
            AuditContext.record(filtered, new PolicyDecision(
                    false,
                    HttpStatus.UNAUTHORIZED,
                    "INVALID_API_KEY",
                    "API_KEY",
                    "Missing or invalid API key"));
            filtered.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            return filtered.getResponse().setComplete();
        }).block());

        assertThat(events).singleElement().satisfies(event -> assertThat(event)
                .contains("requestId=audit-deny")
                .contains("decision=DENY")
                .contains("rule=API_KEY")
                .contains("status=401")
                .contains("apiKeyId=none"));
    }

    @Test
    void doesNotIdentifyAnInvalidPresentedKeyAsTheDemoClient() {
        MockServerWebExchange exchange = exchange("audit-invalid", "bad-key-never-log");

        List<String> events = captureEvents(() -> filter.filter(exchange, filtered -> {
            AuditContext.record(filtered, new PolicyDecision(
                    false,
                    HttpStatus.UNAUTHORIZED,
                    "INVALID_API_KEY",
                    "API_KEY",
                    "Missing or invalid API key"));
            filtered.getResponse().setStatusCode(HttpStatus.UNAUTHORIZED);
            return filtered.getResponse().setComplete();
        }).block());

        assertThat(events).singleElement().satisfies(event -> assertThat(event)
                .contains("requestId=audit-invalid")
                .contains("decision=DENY")
                .contains("apiKeyId=unknown")
                .doesNotContain("bad-key-never-log"));
    }

    @Test
    void runsBetweenRequestIdAndGatewayErrorHandling() {
        assertThat(filter.getOrder()).isEqualTo(-90);
    }

    private MockServerWebExchange exchange(String requestId, String apiKey) {
        MockServerHttpRequest.BaseBuilder<?> request =
                MockServerHttpRequest.get("/api/products")
                        .header("X-Request-Id", requestId);
        if (apiKey != null) {
            request.header("X-API-Key", apiKey);
        }
        return MockServerWebExchange.from(request.build());
    }

    private List<String> captureEvents(Runnable action) {
        Logger logger = (Logger) LoggerFactory.getLogger(AuditFilter.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            action.run();
            return appender.list.stream()
                    .map(ILoggingEvent::getFormattedMessage)
                    .toList();
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }
}
