package com.modushield.gateway.policy.limits;

import com.modushield.gateway.error.ErrorResponseWriter;
import com.modushield.gateway.policy.PolicyDecision;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RequestSizePolicyTest {

    private final ErrorResponseWriter writer = mock(ErrorResponseWriter.class);
    private final RequestSizePolicy policy = new RequestSizePolicy(8192L, writer);

    @Test
    void allowsTheExact8192ByteBoundary() {
        PolicyDecision decision = policy.evaluate(exchange(
                MockServerHttpRequest.post("/api/orders")
                        .header(HttpHeaders.CONTENT_LENGTH, "8192")
        ));

        assertThat(decision.allowed()).isTrue();
    }

    @Test
    void rejects8193Bytes() {
        PolicyDecision decision = policy.evaluate(exchange(
                MockServerHttpRequest.post("/api/orders")
                        .header(HttpHeaders.CONTENT_LENGTH, "8193")
        ));

        assertPayloadTooLarge(decision);
    }

    @Test
    void rejectsBodyMethodWhenLengthIsMissing() {
        PolicyDecision decision = policy.evaluate(exchange(
                MockServerHttpRequest.post("/api/orders")
        ));

        assertPayloadTooLarge(decision);
    }

    @Test
    void rejectsChunkedTransferWhenLengthCannotBeVerified() {
        PolicyDecision decision = policy.evaluate(exchange(
                MockServerHttpRequest.post("/api/orders")
                        .header(HttpHeaders.TRANSFER_ENCODING, "chunked")
        ));

        assertPayloadTooLarge(decision);
    }

    @Test
    void rejectsAmbiguousTransferEncodingAndContentLength() {
        PolicyDecision decision = policy.evaluate(exchange(
                MockServerHttpRequest.post("/api/orders")
                        .header(HttpHeaders.TRANSFER_ENCODING, "chunked")
                        .header(HttpHeaders.CONTENT_LENGTH, "10")
        ));

        assertPayloadTooLarge(decision);
    }

    @Test
    void rejectsMultipleContentLengthValues() {
        PolicyDecision decision = policy.evaluate(exchange(
                MockServerHttpRequest.post("/api/orders")
                        .header(HttpHeaders.CONTENT_LENGTH, "10", "11")
        ));

        assertPayloadTooLarge(decision);
    }

    @Test
    void rejectsMalformedContentLengthWithoutThrowing() {
        PolicyDecision decision = policy.evaluate(exchange(
                MockServerHttpRequest.post("/api/orders")
                        .header(HttpHeaders.CONTENT_LENGTH, "not-a-number")
        ));

        assertPayloadTooLarge(decision);
    }

    @Test
    void rejectsNegativeContentLength() {
        PolicyDecision decision = policy.evaluate(exchange(
                MockServerHttpRequest.post("/api/orders")
                        .header(HttpHeaders.CONTENT_LENGTH, "-2")
        ));

        assertPayloadTooLarge(decision);
    }

    @Test
    void allowsBodylessGetWithoutContentLength() {
        PolicyDecision decision = policy.evaluate(exchange(
                MockServerHttpRequest.get("/api/products")
        ));

        assertThat(decision.allowed()).isTrue();
    }

    @Test
    void filterContinuesOnlyWhenTheSizePolicyAllows() {
        MockServerWebExchange exchange = exchange(MockServerHttpRequest.get("/api/products"));
        GatewayFilterChain chain = mock(GatewayFilterChain.class);
        when(chain.filter(exchange)).thenReturn(Mono.empty());

        StepVerifier.create(policy.filter(exchange, chain)).verifyComplete();

        verify(chain).filter(exchange);
        verify(writer, never()).write(any(), any());
        assertThat(policy.getOrder()).isEqualTo(50);
    }

    @Test
    void filterUsesTheSharedWriterForARejectedRequest() {
        MockServerWebExchange exchange = exchange(
                MockServerHttpRequest.post("/api/orders")
                        .header(HttpHeaders.CONTENT_LENGTH, "8193")
        );
        GatewayFilterChain chain = mock(GatewayFilterChain.class);
        when(writer.write(any(), any())).thenReturn(Mono.empty());

        StepVerifier.create(policy.filter(exchange, chain)).verifyComplete();

        verify(writer).write(exchange, policy.evaluate(exchange));
        verify(chain, never()).filter(any());
    }

    @Test
    void rejectsInvalidConfiguration() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new RequestSizePolicy(0, writer));
    }

    private MockServerWebExchange exchange(MockServerHttpRequest.BaseBuilder<?> request) {
        return MockServerWebExchange.from(request.build());
    }

    private void assertPayloadTooLarge(PolicyDecision decision) {
        assertThat(decision.allowed()).isFalse();
        assertThat(decision.status()).isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE);
        assertThat(decision.code()).isEqualTo("PAYLOAD_TOO_LARGE");
        assertThat(decision.rule()).isEqualTo("SIZE");
        assertThat(decision.safeMessage()).isNotBlank();
    }
}
