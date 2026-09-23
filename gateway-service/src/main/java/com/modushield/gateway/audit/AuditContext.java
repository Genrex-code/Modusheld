package com.modushield.gateway.audit;

import com.modushield.gateway.policy.PolicyDecision;
import org.springframework.web.server.ServerWebExchange;

/** Shared exchange attributes used to explain the final gateway decision. */
public final class AuditContext {

    static final String DECISION_ATTRIBUTE = AuditContext.class.getName() + ".decision";
    static final String RULE_ATTRIBUTE = AuditContext.class.getName() + ".rule";

    private AuditContext() {
    }

    public static void record(ServerWebExchange exchange, PolicyDecision decision) {
        String rule = decision.rule() == null || decision.rule().isBlank()
                ? "UNSPECIFIED"
                : decision.rule();
        String outcome = switch (rule) {
            case "UPSTREAM", "INTERNAL" -> "ERROR";
            default -> "DENY";
        };
        exchange.getAttributes().put(DECISION_ATTRIBUTE, outcome);
        exchange.getAttributes().put(RULE_ATTRIBUTE, rule);
    }

    static String decision(ServerWebExchange exchange) {
        return exchange.getAttributeOrDefault(DECISION_ATTRIBUTE, "ALLOW");
    }

    static String rule(ServerWebExchange exchange) {
        return exchange.getAttributeOrDefault(RULE_ATTRIBUTE, "ROUTE_AND_KEY");
    }
}
