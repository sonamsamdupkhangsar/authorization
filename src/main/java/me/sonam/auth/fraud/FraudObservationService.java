package me.sonam.auth.fraud;

import me.sonam.auth.webclient.FraudHistory;
import me.sonam.auth.webclient.LoginAttemptWebClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

/** Executes the fraud policy in observe-only mode. It never changes authentication flow. */
public final class FraudObservationService {
    private static final Logger LOG = LoggerFactory.getLogger(FraudObservationService.class);

    private final LoginAttemptWebClient loginAttemptWebClient;
    private final DeterministicFraudEvaluator evaluator;

    public FraudObservationService(LoginAttemptWebClient loginAttemptWebClient,
                                   DeterministicFraudEvaluator evaluator) {
        this.loginAttemptWebClient = loginAttemptWebClient;
        this.evaluator = evaluator;
    }

    public Mono<Void> observe(FraudEvent.EventType eventType, String tenantHost,
                              String authenticationId, String sourceIp) {
        String authenticationIdHash = hash(authenticationId);
        String sourceIpHash = hash(sourceIp);
        return loginAttemptWebClient.fraudHistory(authenticationIdHash, sourceIpHash)
                .doOnNext(history -> {
                    FraudEvent event = new FraudEvent(eventType, tenantHost, authenticationIdHash, sourceIpHash,
                            count(history.accountFailures()), count(history.ipFailures()),
                            count(history.distinctAccounts()), count(history.signupAttempts()));
                    FraudDecision decision = evaluator.evaluate(event);
                    LOG.info("fraud observation eventType={} outcome={} rules={} policyVersion={}",
                            eventType, decision.outcome(), decision.matchedRules(), decision.policyVersion());
                })
                .then()
                .onErrorResume(error -> {
                    LOG.warn("fraud observation unavailable; authentication is unaffected: {}", error.getMessage());
                    return Mono.empty();
                });
    }

    static String hash(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest((value == null ? "" : value).getBytes(StandardCharsets.UTF_8));
            return "sha256:" + HexFormat.of().formatHex(digest);
        } catch (Exception error) {
            throw new IllegalStateException("unable to hash fraud identifier", error);
        }
    }

    private static int count(long value) {
        return value > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) value;
    }
}
