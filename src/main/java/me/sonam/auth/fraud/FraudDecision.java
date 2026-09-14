package me.sonam.auth.fraud;

import java.util.List;

/** The deterministic outcome applied to a protected request. */
public record FraudDecision(
        Outcome outcome,
        List<String> matchedRules,
        String policyVersion) {

    public enum Outcome {
        ALLOW,
        REVIEW,
        BLOCK
    }

    public FraudDecision {
        matchedRules = List.copyOf(matchedRules);
    }
}
