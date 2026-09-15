package me.sonam.auth.fraud;

import me.sonam.auth.webclient.FraudHistory;
import me.sonam.auth.webclient.LoginAttemptWebClient;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class FraudObservationServiceTest {
    @Test
    void observesCanonicalWindowsWithoutChangingFlow() {
        LoginAttemptWebClient client = mock(LoginAttemptWebClient.class);
        when(client.fraudHistory(anyString(), anyString()))
                .thenReturn(Mono.just(new FraudHistory(5, 20, 10, 3)));
        when(client.recordFraudDecision(anyString(), any(), anyString(), anyString(), anyString(), any(), anyString()))
                .thenReturn(Mono.just("{\"id\":\"audit-id\"}"));
        FraudObservationService service = new FraudObservationService(client,
                new DeterministicFraudEvaluator(List.of()));

        service.observe(FraudEvent.EventType.LOGIN, "free.openissuer.com", "user", "203.0.113.10").block();

        verify(client, times(1)).fraudHistory(anyString(), anyString());
        verify(client).recordFraudDecision(eq("LOGIN"), eq("free.openissuer.com"),
                anyString(), anyString(), eq("BLOCK"),
                eq(List.of("IP_FAILURE_RATE", "DISTRIBUTED_ACCOUNT_ATTACK", "ACCOUNT_FAILURE_RATE", "NEW_ACCOUNT_VELOCITY")),
                eq("2026-09-13"));
    }

    @Test
    void persistsReviewDecisionWithMatchedRule() {
        LoginAttemptWebClient client = mock(LoginAttemptWebClient.class);
        when(client.fraudHistory(anyString(), anyString()))
                .thenReturn(Mono.just(new FraudHistory(5, 0, 0, 0)));
        when(client.recordFraudDecision(anyString(), any(), anyString(), anyString(), anyString(), any(), anyString()))
                .thenReturn(Mono.just("created"));

        FraudObservationService service = new FraudObservationService(client,
                new DeterministicFraudEvaluator(List.of()));

        service.observe(FraudEvent.EventType.LOGIN, "free.openissuer.com", "user", "203.0.113.10").block();

        verify(client).recordFraudDecision(eq("LOGIN"), eq("free.openissuer.com"),
                anyString(), anyString(), eq("REVIEW"), eq(List.of("ACCOUNT_FAILURE_RATE")),
                eq("2026-09-13"));
    }

    @Test
    void historyFailureIsBestEffort() {
        LoginAttemptWebClient client = mock(LoginAttemptWebClient.class);
        when(client.fraudHistory(anyString(), anyString()))
                .thenReturn(Mono.error(new RuntimeException("attempt service unavailable")));
        FraudObservationService service = new FraudObservationService(client,
                new DeterministicFraudEvaluator(List.of()));

        assertDoesNotThrow(() -> service.observe(FraudEvent.EventType.LOGIN, null, "user", "203.0.113.10").block());
    }
}
