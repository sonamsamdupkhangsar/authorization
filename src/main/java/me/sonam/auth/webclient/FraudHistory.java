package me.sonam.auth.webclient;

public record FraudHistory(long accountFailures, long ipFailures,
                           long distinctAccounts, long signupAttempts) {
}
