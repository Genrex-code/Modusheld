package com.modushield.gateway.policy.limits;

import com.modushield.gateway.error.ErrorResponseWriter;
import com.modushield.gateway.policy.GatewayPolicy;
import com.modushield.gateway.policy.PolicyDecision;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.HexFormat;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/** Fixed-window, in-memory request limiter for the single-instance demo. */
@Component
public class RateLimitPolicy implements GlobalFilter, Ordered, GatewayPolicy {

    private static final int ORDER = 60;
    private static final String API_KEY_HEADER = "X-API-Key";

    private final int capacity;
    private final long windowSeconds;
    private final Clock clock;
    private final ErrorResponseWriter errorResponseWriter;
    private final Map<String, UserBucket> buckets = new ConcurrentHashMap<>();
    private final AtomicLong nextCleanupEpochSecond;

    @Autowired
    public RateLimitPolicy(
            @Value("${modushield.limits.rate-limit-capacity:5}") int capacity,
            @Value("${modushield.limits.rate-limit-window-seconds:10}") long windowSeconds,
            ErrorResponseWriter errorResponseWriter
    ) {
        this(capacity, windowSeconds, Clock.systemUTC(), errorResponseWriter);
    }

    RateLimitPolicy(
            int capacity,
            long windowSeconds,
            Clock clock,
            ErrorResponseWriter errorResponseWriter
    ) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("capacity must be greater than zero");
        }
        if (windowSeconds <= 0) {
            throw new IllegalArgumentException("windowSeconds must be greater than zero");
        }
        this.capacity = capacity;
        this.windowSeconds = windowSeconds;
        this.clock = Objects.requireNonNull(clock, "clock");
        this.errorResponseWriter = Objects.requireNonNull(
                errorResponseWriter,
                "errorResponseWriter"
        );
        this.nextCleanupEpochSecond = new AtomicLong(now() + windowSeconds);
    }

    @Override
    public int getOrder() {
        return ORDER;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        PolicyDecision decision = evaluate(exchange);
        if (!decision.allowed()) {
            return errorResponseWriter.write(exchange, decision);
        }
        return chain.filter(exchange);
    }

    @Override
    public PolicyDecision evaluate(ServerWebExchange exchange) {
        long now = now();
        evictExpiredBuckets(now);

        String identityHash = hashIdentity(clientIdentity(exchange));
        UserBucket bucket = buckets.computeIfAbsent(identityHash, ignored -> new UserBucket(now));

        synchronized (bucket) {
            if (now < bucket.windowStart || now - bucket.windowStart >= windowSeconds) {
                bucket.windowStart = now;
                bucket.requestCount = 0;
            }

            if (bucket.requestCount >= capacity) {
                return new PolicyDecision(
                        false,
                        HttpStatus.TOO_MANY_REQUESTS,
                        "RATE_LIMIT_EXCEEDED",
                        "RATE_LIMIT",
                        "Request limit exceeded"
                );
            }

            bucket.requestCount++;
            return PolicyDecision.allow();
        }
    }

    int trackedIdentityCount() {
        return buckets.size();
    }

    Set<String> trackedIdentityHashes() {
        return Set.copyOf(buckets.keySet());
    }

    private String clientIdentity(ServerWebExchange exchange) {
        String apiKey = exchange.getRequest().getHeaders().getFirst(API_KEY_HEADER);
        if (apiKey != null && !apiKey.isBlank()) {
            return "api-key:" + apiKey;
        }

        InetSocketAddress remoteAddress = exchange.getRequest().getRemoteAddress();
        if (remoteAddress == null) {
            return "ip:unknown";
        }
        if (remoteAddress.getAddress() != null) {
            return "ip:" + remoteAddress.getAddress().getHostAddress();
        }
        return "ip:" + remoteAddress.getHostString();
    }

    private String hashIdentity(String identity) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(identity.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private void evictExpiredBuckets(long now) {
        long scheduledCleanup = nextCleanupEpochSecond.get();
        if (now < scheduledCleanup
                || !nextCleanupEpochSecond.compareAndSet(
                        scheduledCleanup,
                        now + windowSeconds
                )) {
            return;
        }

        buckets.entrySet().removeIf(entry -> isExpired(entry.getValue(), now));
    }

    private boolean isExpired(UserBucket bucket, long now) {
        synchronized (bucket) {
            return now >= bucket.windowStart
                    && now - bucket.windowStart >= windowSeconds;
        }
    }

    private long now() {
        return clock.instant().getEpochSecond();
    }

    private static final class UserBucket {
        private long windowStart;
        private int requestCount;

        private UserBucket(long windowStart) {
            this.windowStart = windowStart;
        }
    }
}
