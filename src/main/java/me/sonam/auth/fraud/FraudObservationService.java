package me.sonam.auth.fraud;

import me.sonam.auth.webclient.FraudHistory;
import me.sonam.auth.webclient.LoginAttemptWebClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import java.util.concurrent.Semaphore;

/** Executes the fraud policy in observe-only mode. It never changes authentication flow. */
public final class FraudObservationService {
    private static final Logger LOG = LoggerFactory.getLogger(FraudObservationService.class);

    private final LoginAttemptWebClient loginAttemptWebClient;
    private final DeterministicFraudEvaluator evaluator;
    private final Semaphore inFlight;
    private final Counter retries;
    private final Counter observationsStarted;
    private final Counter historySucceeded;
    private final Counter historyFailed;
    private final Counter decisionsAllow;
    private final Counter decisionsReview;
    private final Counter decisionsBlock;
    private final Counter exhausted;
    private final Counter auditPersisted;
    private final Counter auditFailures;
    private final Counter concurrencySkipped;

    public FraudObservationService(LoginAttemptWebClient loginAttemptWebClient,
                                   DeterministicFraudEvaluator evaluator) {
        this(loginAttemptWebClient, evaluator, 32);
    }

    public FraudObservationService(LoginAttemptWebClient loginAttemptWebClient,
                                   DeterministicFraudEvaluator evaluator,
                                   int maxInFlight) {
        this(loginAttemptWebClient, evaluator, maxInFlight, new io.micrometer.core.instrument.simple.SimpleMeterRegistry());
    }

    public FraudObservationService(LoginAttemptWebClient loginAttemptWebClient,
                                   DeterministicFraudEvaluator evaluator,
                                   int maxInFlight, MeterRegistry meterRegistry) {
        this.loginAttemptWebClient = loginAttemptWebClient;
        this.evaluator = evaluator;
        this.inFlight = new Semaphore(Math.max(1, maxInFlight));
        this.retries = Counter.builder("fraud.observation.retries").register(meterRegistry);
        this.observationsStarted = Counter.builder("fraud.observation.started").register(meterRegistry);
        this.historySucceeded = Counter.builder("fraud.history.succeeded").register(meterRegistry);
        this.historyFailed = Counter.builder("fraud.history.failed").register(meterRegistry);
        this.decisionsAllow = Counter.builder("fraud.decision.allow").register(meterRegistry);
        this.decisionsReview = Counter.builder("fraud.decision.review").register(meterRegistry);
        this.decisionsBlock = Counter.builder("fraud.decision.block").register(meterRegistry);
        this.exhausted = Counter.builder("fraud.observation.exhausted").register(meterRegistry);
        this.auditPersisted = Counter.builder("fraud.decision.audit.persisted").register(meterRegistry);
        this.auditFailures = Counter.builder("fraud.decision.audit.failures").register(meterRegistry);
        this.concurrencySkipped = Counter.builder("fraud.observation.concurrency.skipped").register(meterRegistry);
    }

    public Mono<Void> observe(FraudEvent.EventType eventType, String tenantHost,
                              String authenticationId, String sourceIp) {
        String authenticationIdHash = hash(authenticationId);
        String sourceIpHash = hash(sourceIp);
        return Mono.defer(() -> {
            if (!inFlight.tryAcquire()) {
                concurrencySkipped.increment();
                LOG.debug("fraud observation skipped because concurrency limit was reached");
                return Mono.empty();
            }
            observationsStarted.increment();
            return Mono.defer(() -> loginAttemptWebClient.fraudHistory(authenticationIdHash, sourceIpHash))
                .timeout(Duration.ofSeconds(2))
                .retryWhen(transientRetry("fraud history"))
                .doOnNext(history -> historySucceeded())
                .doOnError(error -> historyFailed.increment())
                .flatMap(history -> {
                    FraudEvent event = new FraudEvent(eventType, tenantHost, authenticationIdHash, sourceIpHash,
                            count(history.accountFailures()), count(history.ipFailures()),
                            count(history.distinctAccounts()), count(history.signupAttempts()));
                    FraudDecision decision = evaluator.evaluate(event);
                    decisionCounter(decision).increment();
                    LOG.info("fraud observation eventType={} outcome={} rules={} policyVersion={}",
                            eventType, decision.outcome(), decision.matchedRules(), decision.policyVersion());
                    return loginAttemptWebClient.recordFraudDecision(eventType.name(), tenantHost,
                                    authenticationIdHash, sourceIpHash, decision.outcome().name(),
                                    decision.matchedRules(), decision.policyVersion())
                            .timeout(Duration.ofSeconds(2))
                            .doOnSuccess(response -> auditPersisted.increment())
                            .doOnError(error -> { auditFailures.increment(); LOG.warn("fraud decision audit unavailable: {}", error.getMessage()); })
                            .retryWhen(transientRetry("fraud decision audit"))
                            .onErrorResume(error -> Mono.empty());
                })
                .then()
                .onErrorResume(error -> {
                    exhausted.increment();
                    LOG.warn("fraud observation unavailable; authentication is unaffected: {}", error.getMessage());
                    return Mono.empty();
                })
                .doFinally(signal -> inFlight.release());
        });
    }

    private void historySucceeded() {
        historySucceeded.increment();
    }

    private Counter decisionCounter(FraudDecision decision) {
        return switch (decision.outcome()) {
            case ALLOW -> decisionsAllow;
            case REVIEW -> decisionsReview;
            case BLOCK -> decisionsBlock;
        };
    }

    private Retry transientRetry(String operation) {
        return Retry.backoff(2, Duration.ofMillis(100))
                .filter(error -> !(error instanceof WebClientResponseException response
                        && response.getStatusCode().is4xxClientError()))
                .doBeforeRetry(signal -> {
                    retries.increment();
                    LOG.warn("retrying {} after attempt {}: {}", operation,
                            signal.totalRetries() + 1, signal.failure().getMessage());
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
