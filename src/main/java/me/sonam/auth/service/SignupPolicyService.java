package me.sonam.auth.service;

import me.sonam.auth.config.SignupPolicyProperties;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Service
public class SignupPolicyService {
    private final SignupPolicyProperties signupPolicyProperties;
    private final HostOrganizationResolver hostOrganizationResolver;
    private final Environment environment;

    public SignupPolicyService(SignupPolicyProperties signupPolicyProperties,
                               HostOrganizationResolver hostOrganizationResolver,
                               Environment environment) {
        this.signupPolicyProperties = signupPolicyProperties;
        this.hostOrganizationResolver = hostOrganizationResolver;
        this.environment = environment;
    }

    // Applies the current host policy by checking both whether signup is allowed and whether the
    // submitted email domain is permitted for that host.
    public Optional<String> validateEmailForCurrentHost(String email) {
        return validateEmailForHost(hostOrganizationResolver.currentHost().orElse(null), email);
    }

    public Optional<String> validateEmailForHost(String host, String email) {
        Optional<String> signupAllowedError = validateSignupAllowedForHost(host);
        if (signupAllowedError.isPresent()) {
            return signupAllowedError;
        }

        String normalizedEmail = normalize(email);
        int at = normalizedEmail.lastIndexOf('@');
        if (at < 0 || at == normalizedEmail.length() - 1) {
            return Optional.of("email address format is invalid");
        }

        if (!StringUtils.hasText(host)) {
            return Optional.empty();
        }

        Optional<SignupPolicyProperties.HostPolicy> policyOptional = policyForHost(host);
        if (policyOptional.isEmpty() || policyOptional.get().getAllowedEmailDomains().isEmpty()) {
            return Optional.empty();
        }

        String emailDomain = normalizedEmail.substring(at + 1);
        List<String> allowedDomains = policyOptional.get().getAllowedEmailDomains();
        if (allowedDomains.stream().map(this::normalize).anyMatch("*"::equals)) {
            if (isDomainReservedForAnotherHost(host, emailDomain)) {
                return Optional.of("email domain is reserved for another subdomain");
            }
            return Optional.empty();
        }

        boolean allowed = allowedDomains.stream()
                .map(this::normalize)
                .anyMatch(domain -> domain.equals(emailDomain));

        return allowed ? Optional.empty()
                : Optional.of("email domain is not allowed for this subdomain");
    }

    // Allows each subdomain to explicitly opt in or out of user signup.
    public Optional<String> validateSignupAllowedForCurrentHost() {
        return validateSignupAllowedForHost(hostOrganizationResolver.currentHost().orElse(null));
    }

    public Optional<String> validateSignupAllowedForHost(String host) {
        if (!StringUtils.hasText(host)) {
            return Optional.empty();
        }

        Optional<SignupPolicyProperties.HostPolicy> policyOptional = policyForHost(host);
        if (policyOptional.isEmpty()) {
            return Optional.empty();
        }

        SignupPolicyProperties.HostPolicy policy = policyOptional.get();
        if (!policy.isAllowAccountSelfService()) {
            return Optional.of("account self-service is disabled on this subdomain");
        }

        return policy.isAllowSignup() ? Optional.empty()
                : Optional.of("signup is not allowed on this subdomain");
    }

    public Optional<String> validateAccountSelfServiceAllowedForCurrentHost() {
        return validateAccountSelfServiceAllowedForHost(hostOrganizationResolver.currentHost().orElse(null));
    }

    public Optional<String> validateAccountSelfServiceAllowedForHost(String host) {
        if (!StringUtils.hasText(host)) {
            return Optional.empty();
        }

        Optional<SignupPolicyProperties.HostPolicy> policyOptional = policyForHost(host);
        if (policyOptional.isEmpty()) {
            return Optional.empty();
        }

        return policyOptional.get().isAllowAccountSelfService() ? Optional.empty()
                : Optional.of("account self-service is disabled on this subdomain");
    }

    public boolean isAccountSelfServiceAllowedForCurrentHost() {
        return validateAccountSelfServiceAllowedForCurrentHost().isEmpty();
    }

    // Decides whether signup on the current host should create a brand new organization or attach
    // the user to the organization already bound to that host.
    public boolean shouldCreateOrganizationOnSignupForCurrentHost() {
        return shouldCreateOrganizationOnSignupForHost(hostOrganizationResolver.currentHost().orElse(null));
    }

    public boolean shouldCreateOrganizationOnSignupForHost(String host) {
        if (!StringUtils.hasText(host)) {
            return true;
        }

        Optional<SignupPolicyProperties.HostPolicy> policyOptional = policyForHost(host);
        if (policyOptional.isEmpty()) {
            return true;
        }
        return policyOptional.get().isCreateOrganizationOnSignup();
    }

    // Normalizes host and email-domain values so policy matching stays case-insensitive.
    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizeHost(String host) {
        return normalize(host);
    }

    private Optional<SignupPolicyProperties.HostPolicy> policyForHost(String host) {
        String normalizedHost = normalizeHost(host);
        if (!StringUtils.hasText(normalizedHost)) {
            return Optional.empty();
        }

        SignupPolicyProperties.HostPolicy policy = signupPolicyProperties.getHosts().get(normalizedHost);
        if (policy != null) {
            return Optional.of(policy);
        }

        Optional<SignupPolicyProperties.HostPolicy> normalizedEntryPolicy = signupPolicyProperties.getHosts()
                .entrySet()
                .stream()
                .filter(entry -> normalizeHost(entry.getKey()).equals(normalizedHost))
                .map(java.util.Map.Entry::getValue)
                .findFirst();
        if (normalizedEntryPolicy.isPresent()) {
            return normalizedEntryPolicy;
        }

        String prefix = "authorization-server.signup-policy.hosts." + normalizedHost;
        Boolean allowSignup = environment.getProperty(prefix + ".allow-signup", Boolean.class);
        Boolean allowAccountSelfService = environment.getProperty(prefix + ".allow-account-self-service", Boolean.class);
        if (allowSignup == null && allowAccountSelfService == null) {
            return Optional.empty();
        }

        SignupPolicyProperties.HostPolicy environmentPolicy = new SignupPolicyProperties.HostPolicy();
        environmentPolicy.setAllowSignup(allowSignup == null || allowSignup);
        environmentPolicy.setAllowAccountSelfService(allowAccountSelfService == null || allowAccountSelfService);
        environmentPolicy.setCreateOrganizationOnSignup(environment.getProperty(
                prefix + ".create-organization-on-signup", Boolean.class, true));
        List<String> allowedEmailDomains = Binder.get(environment)
                .bind(prefix + ".allowed-email-domains", Bindable.listOf(String.class))
                .orElse(List.of());
        environmentPolicy.setAllowedEmailDomains(allowedEmailDomains);
        return Optional.of(environmentPolicy);
    }

    private boolean isDomainReservedForAnotherHost(String host, String emailDomain) {
        String normalizedHost = normalize(host);
        return signupPolicyProperties.getHosts().entrySet().stream()
                .filter(entry -> !normalize(entry.getKey()).equals(normalizedHost))
                .flatMap(entry -> entry.getValue().getAllowedEmailDomains().stream())
                .map(this::normalize)
                .filter(domain -> !domain.equals("*"))
                .anyMatch(domain -> domain.equals(emailDomain));
    }
}
