package com.modushield.gateway.policy;

import com.modushield.gateway.error.ErrorResponseWriter;
import com.modushield.gateway.policy.limits.RateLimitPolicy;
import com.modushield.gateway.policy.limits.RequestSizePolicy;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ServerWebExchange;

import java.lang.reflect.RecordComponent;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class ContractCompatibilityTest {

    @Test
    void policyDecisionMatchesTheCanonicalCoreShape() {
        assertThat(PolicyDecision.class.isRecord()).isTrue();
        assertThat(Arrays.stream(PolicyDecision.class.getRecordComponents())
                .map(RecordComponent::getName))
                .containsExactly("allowed", "status", "code", "rule", "safeMessage");

        PolicyDecision allowed = PolicyDecision.allow();
        assertThat(allowed.allowed()).isTrue();
        assertThat(allowed.status()).isEqualTo(HttpStatus.OK);
        assertThat(allowed.code()).isNull();
        assertThat(allowed.rule()).isNull();
        assertThat(allowed.safeMessage()).isNull();
    }

    @Test
    void limitPoliciesImplementTheSharedGatewayPolicyContract() {
        assertThat(GatewayPolicy.class).isAssignableFrom(RequestSizePolicy.class);
        assertThat(GatewayPolicy.class).isAssignableFrom(RateLimitPolicy.class);
    }

    @Test
    void errorWriterConsumesTheSharedDecision() throws NoSuchMethodException {
        assertThat(ErrorResponseWriter.class.getMethod(
                "write",
                ServerWebExchange.class,
                PolicyDecision.class
        )).isNotNull();
    }
}
