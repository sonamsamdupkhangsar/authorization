package me.sonam.auth.util;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Component
@ConfigurationProperties
public class TokenRequestFilter {
    private final List<RequestFilter> requestFilters = new ArrayList<>();

    public List<RequestFilter> getRequestFilters() {
        return requestFilters;
    }

    public TokenRequestFilter() {
    }

    public static class RequestFilter {
        private String out;
        private Set<String> outSet = new HashSet<>();
        private String outHttpMethods;
        private Set<String> outHttpMethodSet = new HashSet<>();

        private AccessToken accessToken;

        public RequestFilter() {
        }

        public String getOut() {
            return out;
        }

        public void setOut(String out) {
            this.out = out;
            String[] outArray = out.split(",");
            outSet = Arrays.stream(outArray).map(String::trim).collect(Collectors.toSet());
        }
        public Set<String> getOutSet() {
            return this.outSet;
        }
        public Set<String> getOutHttpMethodSet() {
            return this.outHttpMethodSet;
        }

        public String getOutHttpMethods() {
            return this.outHttpMethods;
        }

        public void setOutHttpMethods(String outHttpMethods) {
            this.outHttpMethods = outHttpMethods;
            String[] httpMethodArray = outHttpMethods.split(",");
            outHttpMethodSet = Arrays.stream(httpMethodArray).map(String::trim).map(String::toLowerCase).collect(Collectors.toSet());
        }
        public AccessToken getAccessToken() {
            return accessToken;
        }

        public void setAccessToken(AccessToken accessToken) {
            this.accessToken = accessToken;
        }

        @Override
        public String toString() {
            return "RequestFilter{" +
                    " out='" + out + '\'' +
                    ", outSet='" + outSet +'\'' +
                    ", outHttpMethods='" + outHttpMethods + '\'' +
                    ", outHttpMethodSet='" + outHttpMethodSet + '\'' +
                    ", accessToken='" + accessToken + '\'' +
                    '}';
        }

        @Override
        public boolean equals(Object object) {
            if (this == object) return true;
            if (object == null || getClass() != object.getClass()) return false;
            RequestFilter that = (RequestFilter) object;
            return Objects.equals(out, that.out) && Objects.equals(outHttpMethods, that.outHttpMethods) && Objects.equals(accessToken, that.accessToken);
        }

        @Override
        public int hashCode() {
            return Objects.hash(out, outHttpMethods, accessToken);
        }

        public static class AccessToken {
            public static enum JwtOption {
                forward, request, doNothing
            }

            private final JwtOption option;
            private final String scopes;
            private final String base64EncodedClientIdSecret;
            private String accessToken;
            private LocalDateTime accessTokenCreationTime;

            public AccessToken(String option, String scopes, String base64EncodedClientIdSecret) {
                this.option = JwtOption.valueOf(option);
                this.scopes = scopes;
                this.base64EncodedClientIdSecret = base64EncodedClientIdSecret;
            }

            public JwtOption getOption() {
                return option;
            }
            public String getScopes() {
                return scopes;
            }
            public String getBase64EncodedClientIdSecret() {
                return base64EncodedClientIdSecret;
            }

            public String getAccessToken() {
                return this.accessToken;
            }

            public void setAccessToken(String accessToken) {
                this.accessToken = accessToken;
                accessTokenCreationTime = LocalDateTime.now();
            }

            public LocalDateTime getAccessTokenCreationTime() {
                return this.accessTokenCreationTime;
            }


            @Override
            public String toString() {
                return "AccessToken{" +
                        "option=" + option +
                        ", scopes='" + scopes + '\'' +
                        ", credentialsConfigured=" + (base64EncodedClientIdSecret != null && !base64EncodedClientIdSecret.isBlank()) +
                        '}';
            }

            @Override
            public boolean equals(Object object) {
                if (this == object) return true;
                if (object == null || getClass() != object.getClass()) return false;
                AccessToken that = (AccessToken) object;
                return option == that.option && Objects.equals(scopes, that.scopes) && Objects.equals(base64EncodedClientIdSecret, that.base64EncodedClientIdSecret);
            }

            @Override
            public int hashCode() {
                return Objects.hash(option, scopes, base64EncodedClientIdSecret);
            }
        }
    }
}
