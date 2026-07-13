package me.sonam.auth.jpa.repo;

import java.util.Optional;

import me.sonam.auth.jpa.entity.Authorization;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface AuthorizationRepository extends JpaRepository<Authorization, String> {
    Optional<Authorization> findByState(String state);
    Optional<Authorization> findByTenantIdAndState(String tenantId, String state);
    Optional<Authorization> findByAuthorizationCodeValue(String authorizationCode);
    Optional<Authorization> findByTenantIdAndAuthorizationCodeValue(String tenantId, String authorizationCode);
    Optional<Authorization> findByAccessTokenValue(String accessToken);
    Optional<Authorization> findByTenantIdAndAccessTokenValue(String tenantId, String accessToken);
    Optional<Authorization> findByRefreshTokenValue(String refreshToken);
    Optional<Authorization> findByTenantIdAndRefreshTokenValue(String tenantId, String refreshToken);
    long deleteByTenantIdAndRegisteredClientId(String tenantId, String registeredClientId);
    @Query("select a from Authorization a where a.state = :token" +
            " or a.authorizationCodeValue = :token" +
            " or a.accessTokenValue = :token" +
            " or a.refreshTokenValue = :token"
    )
    Optional<Authorization> findByStateOrAuthorizationCodeValueOrAccessTokenValueOrRefreshTokenValue(@Param("token") String token);

    @Query("select a from Authorization a where a.tenantId = :tenantId and (" +
            " a.state = :token" +
            " or a.authorizationCodeValue = :token" +
            " or a.accessTokenValue = :token" +
            " or a.refreshTokenValue = :token)"
    )
    Optional<Authorization> findByTenantIdAndStateOrAuthorizationCodeValueOrAccessTokenValueOrRefreshTokenValue(
            @Param("tenantId") String tenantId, @Param("token") String token);
}
