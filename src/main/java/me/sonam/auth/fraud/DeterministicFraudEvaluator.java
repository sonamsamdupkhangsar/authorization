package me.sonam.auth.fraud;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/**
 * Evaluates fraud rules in a fixed order. No network calls, clocks, or random values
 * are used here so the same event always produces the same decision.
 */
public final class DeterministicFraudEvaluator {
    public static final String POLICY_VERSION = "2026-09-13";

    private final Set<String> blockedSources;

    public DeterministicFraudEvaluator(Collection<String> blockedSources) {
        this.blockedSources = new HashSet<>(blockedSources);
    }

    public FraudDecision evaluate(FraudEvent event) {
        Set<String> blockedRules = new HashSet<>();
        if (blockedSources.contains(event.sourceIpHash())) {
            blockedRules.add("BLOCKED_SOURCE");
        }
        if (event.failedAttemptsFromIpLast10Minutes() >= 20) {
            blockedRules.add("IP_FAILURE_RATE");
        }
        if (event.distinctAccountsFromIpLast10Minutes() >= 10) {
            blockedRules.add("DISTRIBUTED_ACCOUNT_ATTACK");
        }

        Set<String> reviewRules = new HashSet<>();
        if (event.failedAttemptsLast15Minutes() >= 5) {
            reviewRules.add("ACCOUNT_FAILURE_RATE");
        }
        if (event.signupAttemptsFromIpLast30Minutes() >= 3) {
            reviewRules.add("NEW_ACCOUNT_VELOCITY");
        }

        if (!blockedRules.isEmpty()) {
            return new FraudDecision(FraudDecision.Outcome.BLOCK,
                    orderedRules(blockedRules, reviewRules), POLICY_VERSION);
        }
        if (!reviewRules.isEmpty()) {
            return new FraudDecision(FraudDecision.Outcome.REVIEW,
                    orderedRules(blockedRules, reviewRules), POLICY_VERSION);
        }
        return new FraudDecision(FraudDecision.Outcome.ALLOW, java.util.List.of(), POLICY_VERSION);
    }

    private static java.util.List<String> orderedRules(Set<String> blockedRules, Set<String> reviewRules) {
        return java.util.stream.Stream.of(
                        "BLOCKED_SOURCE",
                        "IP_FAILURE_RATE",
                        "DISTRIBUTED_ACCOUNT_ATTACK",
                        "ACCOUNT_FAILURE_RATE",
                        "NEW_ACCOUNT_VELOCITY")
                .filter(rule -> blockedRules.contains(rule) || reviewRules.contains(rule))
                .toList();
    }
}
