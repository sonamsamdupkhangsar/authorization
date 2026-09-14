package me.sonam.auth.fraud;

/** Normalized, non-secret input to the fraud policy evaluator. */
public record FraudEvent(
        EventType eventType,
        String tenantHost,
        String authenticationIdHash,
        String sourceIpHash,
        int failedAttemptsLast15Minutes,
        int failedAttemptsFromIpLast10Minutes,
        int distinctAccountsFromIpLast10Minutes,
        int signupAttemptsFromIpLast30Minutes) {

    public enum EventType {
        LOGIN,
        SIGNUP,
        ACTIVATION
    }
}
