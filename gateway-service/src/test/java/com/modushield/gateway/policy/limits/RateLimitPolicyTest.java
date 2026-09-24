package com.modushield.gateway.policy.limits;

import com.modushield.gateway.error.ErrorResponseWriter;
import com.modushield.gateway.policy.PolicyDecision;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RateLimitPolicyTest {

    private final MutableClock clock = new MutableClock(
            Instant.parse("2026-01-01T00:00:00Z")
    );
    private final ErrorResponseWriter writer = mock(ErrorResponseWriter.class);

    @Test
    void allowsFiveRequestsAndRejectsTheSixth() {
        RateLimitPolicy policy = policy(5, 10);

        for (int request = 1; request <= 5; request++) {
            assertThat(policy.evaluate(exchange("client-a")).allowed()).isTrue();
        }

        PolicyDecision sixth = policy.evaluate(exchange("client-a"));
        assertThat(sixth.allowed()).isFalse();
        assertThat(sixth.status()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(sixth.code()).isEqualTo("RATE_LIMIT_EXCEEDED");
        assertThat(sixth.rule()).isEqualTo("RATE_LIMIT");
        assertThat(sixth.safeMessage()).isEqualTo("Request limit exceeded");
    }

    @Test
    void resetsTheCounterAfterTheWindow() {
        RateLimitPolicy policy = policy(1, 10);

        assertThat(policy.evaluate(exchange("client-a")).allowed()).isTrue();
        assertThat(policy.evaluate(exchange("client-a")).allowed()).isFalse();

        clock.advance(Duration.ofSeconds(10));

        assertThat(policy.evaluate(exchange("client-a")).allowed()).isTrue();
    }

    @Test
    void keepsClientCountersIndependent() {
        RateLimitPolicy policy = policy(1, 10);

        assertThat(policy.evaluate(exchange("client-a")).allowed()).isTrue();
        assertThat(policy.evaluate(exchange("client-b")).allowed()).isTrue();
        assertThat(policy.evaluate(exchange("client-a")).allowed()).isFalse();
    }

    @Test
    void allowsOnlyTheConfiguredCapacityUnderConcurrency()
            throws InterruptedException, ExecutionException {
        RateLimitPolicy policy = policy(5, 10);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(12);
        List<Future<PolicyDecision>> decisions = new ArrayList<>();

        try {
            for (int request = 0; request < 20; request++) {
                decisions.add(executor.submit(() -> {
                    start.await();
                    return policy.evaluate(exchange("concurrent-client"));
                }));
            }
            start.countDown();

            long allowed = 0;
            for (Future<PolicyDecision> decision : decisions) {
                if (decision.get().allowed()) {
                    allowed++;
                }
            }

            assertThat(allowed).isEqualTo(5);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void storesOnlyHashedIdentities() {
        RateLimitPolicy policy = policy(5, 10);

        policy.evaluate(exchange("secret-key-must-not-be-retained"));

        assertThat(policy.trackedIdentityHashes())
                .hasSize(1)
                .allSatisfy(identity -> {
                    assertThat(identity).hasSize(64);
                    assertThat(identity).doesNotContain("secret-key-must-not-be-retained");
                });
    }

    @Test
    void evictsExpiredClientBuckets() {
        RateLimitPolicy policy = policy(5, 10);
        policy.evaluate(exchange("expired-client"));

        clock.advance(Duration.ofSeconds(11));
        policy.evaluate(exchange("active-client"));

        assertThat(policy.trackedIdentityCount()).isEqualTo(1);
    }

    @Test
    void filterContinuesOnlyWhenCapacityIsAvailable() {
        RateLimitPolicy policy = policy(1, 10);
        MockServerWebExchange exchange = exchange("client-a");
        GatewayFilterChain chain = mock(GatewayFilterChain.class);
        when(chain.filter(exchange)).thenReturn(Mono.empty());

        StepVerifier.create(policy.filter(exchange, chain)).verifyComplete();

        verify(chain).filter(exchange);
        verify(writer, never()).write(any(), any());
        assertThat(policy.getOrder()).isEqualTo(60);
    }

    @Test
    void filterUsesTheSharedWriterAfterCapacityIsConsumed() {
        RateLimitPolicy policy = policy(1, 10);
        policy.evaluate(exchange("client-a"));
        MockServerWebExchange rejected = exchange("client-a");
        GatewayFilterChain chain = mock(GatewayFilterChain.class);
        when(writer.write(any(), any())).thenReturn(Mono.empty());

        StepVerifier.create(policy.filter(rejected, chain)).verifyComplete();

        verify(writer).write(rejected, new PolicyDecision(
                false,
                HttpStatus.TOO_MANY_REQUESTS,
                "RATE_LIMIT_EXCEEDED",
                "RATE_LIMIT",
                "Request limit exceeded"
        ));
        verify(chain, never()).filter(any());
    }

    @Test
    void rejectsInvalidConfiguration() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new RateLimitPolicy(0, 10, clock, writer));
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new RateLimitPolicy(5, 0, clock, writer));
    }

    private RateLimitPolicy policy(int capacity, long windowSeconds) {
        return new RateLimitPolicy(capacity, windowSeconds, clock, writer);
    }

    private MockServerWebExchange exchange(String apiKey) {
        return MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/products")
                        .header("X-API-Key", apiKey)
                        .build()
        );
    }

    private static final class MutableClock extends Clock {
        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        private void advance(Duration duration) {
            instant = instant.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
