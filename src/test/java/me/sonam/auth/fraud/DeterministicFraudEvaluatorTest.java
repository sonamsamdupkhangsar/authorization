package me.sonam.auth.fraud;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DeterministicFraudEvaluatorTest {
    private static final String IP = "203.0.113.10";

    private final DeterministicFraudEvaluator evaluator =
            new DeterministicFraudEvaluator(List.of("sha256:198.51.100.7"));

    @Test
    void allowsEventWithNoMatchedRules() {
        FraudDecision decision = evaluator.evaluate(event(0, 0, 0, 0));

        assertEquals(FraudDecision.Outcome.ALLOW, decision.outcome());
        assertEquals(List.of(), decision.matchedRules());
    }

    @Test
    void accountFailureRateRequiresReview() {
        FraudDecision decision = evaluator.evaluate(event(5, 0, 0, 0));

        assertEquals(FraudDecision.Outcome.REVIEW, decision.outcome());
        assertEquals(List.of("ACCOUNT_FAILURE_RATE"), decision.matchedRules());
    }

    @Test
    void blockedSourceTakesPrecedence() {
        FraudEvent event = new FraudEvent(FraudEvent.EventType.LOGIN, "free.openissuer.com",
                "sha256:user", "sha256:198.51.100.7", 5, 20, 10, 3);

        FraudDecision decision = evaluator.evaluate(event);

        assertEquals(FraudDecision.Outcome.BLOCK, decision.outcome());
        assertEquals(List.of("BLOCKED_SOURCE", "IP_FAILURE_RATE", "DISTRIBUTED_ACCOUNT_ATTACK",
                "ACCOUNT_FAILURE_RATE", "NEW_ACCOUNT_VELOCITY"), decision.matchedRules());
    }

    @Test
    void signupVelocityRequiresReview() {
        FraudDecision decision = evaluator.evaluate(event(0, 0, 0, 3));

        assertEquals(FraudDecision.Outcome.REVIEW, decision.outcome());
        assertEquals(List.of("NEW_ACCOUNT_VELOCITY"), decision.matchedRules());
    }

    private static FraudEvent event(int accountFailures, int ipFailures,
                                    int distinctAccounts, int signupAttempts) {
        return new FraudEvent(FraudEvent.EventType.LOGIN, "free.openissuer.com",
                "sha256:user", "sha256:" + IP, accountFailures, ipFailures,
                distinctAccounts, signupAttempts);
    }
}
