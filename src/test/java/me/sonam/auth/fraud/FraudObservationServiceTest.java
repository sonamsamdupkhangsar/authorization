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
        FraudObservationService service = new FraudObservationService(client,
                new DeterministicFraudEvaluator(List.of()));

        service.observe(FraudEvent.EventType.LOGIN, "free.openissuer.com", "user", "203.0.113.10").block();

        verify(client, times(1)).fraudHistory(anyString(), anyString());
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
