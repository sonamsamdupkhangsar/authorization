/*
 * Copyright 2020-2022 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package me.sonam.auth;

import me.sonam.auth.jpa.entity.ClientOrganization;
import me.sonam.auth.jpa.entity.ClientOrganizationId;
import me.sonam.auth.jpa.entity.ClientUser;
import me.sonam.auth.jpa.entity.ClientUserId;
import me.sonam.auth.jpa.repo.ClientOrganizationRepository;
import me.sonam.auth.jpa.repo.HClientUserRepository;
import me.sonam.auth.service.JpaRegisteredClientRepository;
import me.sonam.auth.util.TokenRequestFilter;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.assertj.core.api.AssertionsForClassTypes;
import org.htmlunit.Page;
import org.htmlunit.WebClient;
import org.htmlunit.html.HtmlButton;
import org.htmlunit.html.HtmlInput;
import org.htmlunit.html.HtmlPage;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;


@ExtendWith(SpringExtension.class)
@SpringBootTest(classes = {DefaultAuthorizationServerApplication.class}, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
public class AuthorizationServerApplicationUserLoginTests {
	private static final Logger LOG = LoggerFactory.getLogger(AuthorizationServerApplicationUserLoginTests.class);

	private static final String REDIRECT_URI = "http://127.0.0.1:{server.port}/login/oauth2/code/messaging-client-oidc";
	//private static String REDIRECT_URI = "http://localhost:{server.port}/login";

	static final String clientsClientId = "messaging-client";
	static final UUID clientId = UUID.randomUUID();// = "messaging-client";
	private static final UUID userId = UUID.randomUUID();
	private static final UUID organizationId = UUID.randomUUID();
	private static String AUTHORIZATION_REQUEST; //this is set in {@properties method}
	private static MockWebServer mockWebServer;
	private static String serverPort;

	@Autowired
	private JpaRegisteredClientRepository jpaRegisteredClientRepository;

	@Autowired
	private ClientOrganizationRepository clientOrganizationRepository;

	@Autowired
	private HClientUserRepository clientUserRepository;
	@Autowired
	private TokenRequestFilter tokenRequestFilter;

	private void saveClientOrganization(final UUID clientId, UUID organizationId) {
		RegisteredClient registeredClient = jpaRegisteredClientRepository.findByClientId(clientsClientId);
		assertThat(registeredClient).isNotNull();
		assertThat(registeredClient.getClientId()).isEqualTo(clientsClientId);

		LOG.info("checking exists in repository");
		if (!clientOrganizationRepository.existsByClientIdAndOrganizationId(UUID.fromString(registeredClient.getId()), organizationId).get()) {
			clientOrganizationRepository.save(new ClientOrganization(UUID.fromString(registeredClient.getId()), organizationId));
			LOG.info("saved clientId {} with organizationId {}", registeredClient.getId(), organizationId);
		}
		LOG.info("done saving clientorganization");
	}

	private void saveClientUser(final UUID clientId, UUID userId) {
		RegisteredClient registeredClient = jpaRegisteredClientRepository.findByClientId(clientsClientId);
		assertThat(registeredClient).isNotNull();
		assertThat(registeredClient.getClientId()).isEqualTo(clientsClientId);

		if (!clientUserRepository.existsById(new ClientUserId(UUID.fromString(registeredClient.getId()), userId))) {
			clientUserRepository.save(new ClientUser(UUID.fromString(registeredClient.getId()), userId));
			LOG.info("saved clientUser");
		}
	}

	@BeforeEach
	public void deleteClientFromOrganization() {
		LOG.info("delete clientOrganizationId from clientOrganization with clientId {} and organizationId {}",
				clientId, organizationId);
		clientOrganizationRepository.deleteById(new ClientOrganizationId(clientId, organizationId));

		clientOrganizationRepository.findByClientId(clientId).ifPresent(clientOrganization ->
				LOG.info("still found clientOrganization {}", clientOrganization));

		cleanOutAccessTokens();
	}

	private void cleanOutAccessTokens() {
		for(TokenRequestFilter.RequestFilter tokenRequestFilter: tokenRequestFilter.getRequestFilters()) {
			tokenRequestFilter.getAccessToken().setAccessToken(null);
		}
	}

	@BeforeEach
	public void deleteClientUser() {
		LOG.info("delete clientUserId from clientUser");
		clientUserRepository.deleteById(new ClientUserId(clientId, userId));
		//clientUserRepository.deleteAll();
	}

	@BeforeAll
	static void setupMockWebServer() throws IOException {
		mockWebServer = new MockWebServer();
		mockWebServer.start();

		LOG.info("host: {}, port: {}", mockWebServer.getHostName(), mockWebServer.getPort());
		AuthorizationServerApplicationUserLoginTests.serverPort = "http://"+ mockWebServer.getHostName() + ":"+mockWebServer.getPort();
	}

	@AfterAll
	public static void shutdownMockWebServer() throws IOException {
		LOG.info("shutdown and close mockWebServer");
		mockWebServer.shutdown();
		mockWebServer.close();
	}

	@DynamicPropertySource
	static void properties(DynamicPropertyRegistry r) throws IOException {
		r.add("authentication-rest-service.root", () -> "http://localhost:"+mockWebServer.getPort());
		r.add("organization-rest-service.root", () -> "http://localhost:"+mockWebServer.getPort());
		r.add("user-rest-service.root", () -> "http://localhost:"+mockWebServer.getPort());
		r.add("auth-server.root", () -> "http://localhost:"+mockWebServer.getPort());
		r.add("attempt-rest-service.root", () -> "http://localhost:"+mockWebServer.getPort());
		r.add("account-rest-service.root", () -> "http://localhost:"+mockWebServer.getPort());

		String redirectUri = REDIRECT_URI.replace("{server.port}", "" +mockWebServer.getPort());
		AUTHORIZATION_REQUEST = UriComponentsBuilder
				.fromPath("/oauth2/authorize")
				.queryParam("response_type", "code")
				.queryParam("client_id", clientsClientId)
				.queryParam("scope", "openid")
				.queryParam("state", "some-state")
				.queryParam("redirect_uri", redirectUri)
				.toUriString();
	}

	@Autowired
	private WebClient webClient;

	@BeforeEach
	public void setUp() {
		this.webClient.getOptions().setThrowExceptionOnFailingStatusCode(true);
		this.webClient.getOptions().setRedirectEnabled(true);
		this.webClient.getCookieManager().clearCookies();	// log out
	}


	@Test
	public void whenNotLoggedInAndRequestingTokenThenRedirectsToLogin() throws IOException {
		LOG.info("test whenNotLoggedInAndRequestingTokenThenRedirectsToLogin()");
		HtmlPage page = this.webClient.getPage(AUTHORIZATION_REQUEST);

		assertLoginPage(page);
	}

	/**
	 * This test will check the behavior when the account is locked for a user
	 * signing in with a username/authenticationId
	 */
	@Test
	public void accountLockedTrueTest() throws Exception {
		saveClientOrganization(clientId, organizationId);

		// Log in
		this.webClient.getOptions().setThrowExceptionOnFailingStatusCode(false);
		//set redirection false so we can login manually with code below
		this.webClient.getOptions().setRedirectEnabled(true);

		final String jwtString= "eyJhbGciOiJIUzUxMiJ9.eyJzdWIiOiJzb25hbSIsImlzcyI6InNvbmFtLmNsb3VkIiwiYXVkIjoic29uYW0uY2xvdWQiLCJqdGkiOiJmMTY2NjM1OS05YTViLTQ3NzMtOWUyNy00OGU0OTFlNDYzNGIifQ.KGFBUjghvcmNGDH0eM17S9pWkoLwbvDaDBGAx2AyB41yZ_8-WewTriR08JdjLskw1dsRYpMh9idxQ4BS6xmOCQ";

		//1
		final String jwtTokenMsg = " {\"access_token\":\""+jwtString+"\"}";
		mockWebServer.enqueue(new MockResponse().setHeader("Content-Type", "application/json")
				.setResponseCode(200).setBody(jwtTokenMsg));

		//2
		//this response is for getting user by authenticationId (loginId)
		mockWebServer.enqueue(new MockResponse().setHeader("Content-Type", "application/json")
				.setResponseCode(200).setBody("{\"message\": true}"));

		Page page = signIn(/*this.webClient.getPage(response
				.getResponseHeaderValue("location"))*/
				this.webClient.getPage(AUTHORIZATION_REQUEST), "user1", "password");

		//in future look for the error message in the htmlPage
		LOG.info("is html page: {}, url: {}, content: {}", page.isHtmlPage(), page.getUrl(), page.getWebResponse().getContentAsString());

		assertThat(page.getUrl().toString()).endsWith("?error");
		//1
		RecordedRequest recordedRequest = mockWebServer.takeRequest();
		LOG.info("should be acesstoken path for recordedRequest: {}", recordedRequest.getPath());
		AssertionsForClassTypes.assertThat(recordedRequest.getPath()).startsWith("/issuer/oauth2/token");
		AssertionsForClassTypes.assertThat(recordedRequest.getMethod()).isEqualTo("POST");

		//2
		recordedRequest = mockWebServer.takeRequest();
		assertThat(recordedRequest.getMethod()).isEqualTo("PUT");
		assertThat(recordedRequest.getPath()).startsWith("/accounts/lock");
	}

	/**
	 * This tests the login process for a user and verifies the user is in a organization
	 * and gets the user roles.  This verifies the login process works.
	 */
	@Test
	public void checkClientInOrganization() throws Exception {
		saveClientOrganization(clientId, organizationId);

		// Log in
		this.webClient.getOptions().setThrowExceptionOnFailingStatusCode(false);
		//set redirection false so we can login manually with code below
		this.webClient.getOptions().setRedirectEnabled(true);

		final String jwtString= "eyJhbGciOiJIUzUxMiJ9.eyJzdWIiOiJzb25hbSIsImlzcyI6InNvbmFtLmNsb3VkIiwiYXVkIjoic29uYW0uY2xvdWQiLCJqdGkiOiJmMTY2NjM1OS05YTViLTQ3NzMtOWUyNy00OGU0OTFlNDYzNGIifQ.KGFBUjghvcmNGDH0eM17S9pWkoLwbvDaDBGAx2AyB41yZ_8-WewTriR08JdjLskw1dsRYpMh9idxQ4BS6xmOCQ";

		//1
		final String jwtTokenMsg = " {\"access_token\":\""+jwtString+"\"}";
		mockWebServer.enqueue(new MockResponse().setHeader("Content-Type", "application/json")
				.setResponseCode(200).setBody(jwtTokenMsg));

		//2
		//user is not locked response
		mockWebServer.enqueue(new MockResponse().setHeader("Content-Type", "application/json")
				.setResponseCode(200).setBody("{\"message\":\"false\"}"));

		//3 for get user data by auth-id
		mockWebServer.enqueue(new MockResponse().setHeader("Content-Type", "application/json")
				.setResponseCode(200).setBody("{\"id\":\""+userId+"\", \"firstName\":\"Dommy\"}"));

		//4 user exists in organization call to return true
		mockWebServer.enqueue(new MockResponse().setHeader("Content-Type", "application/json")
				.setResponseCode(200).setBody("{\"message\": true}"));

		//5 this is returned for authentications authenticate call mock response with roles with userId
		// and message
		mockWebServer.enqueue(new MockResponse().setHeader("Content-Type", "application/json")
				.setResponseCode(200).setBody("{\"roleNames\": \"[user, SuperAdmin]\", \"userId\": \""+ userId +"\", \"message\": \"Authentication successful\"}"));

		//6 login success is recorded before returning user roles in authentication
		mockWebServer.enqueue(new MockResponse().setHeader("Content-Type", "application/json")
				.setResponseCode(200).setBody("{\"message\": \"CONTINUE\"}"));

		//7
		//it seems like we need to mock one more response for the redirection to redirecUris: /login/oauth2/code/messaging-client-oidc?code=...
		mockWebServer.enqueue(new MockResponse().setHeader("Content-Type", "application/json")
				.setResponseCode(200).setBody("{\"roleNames\": \"[user, SuperAdmin]\", \"userId\": \""+ userId +"\", \"message\": \"Authentication successful\"}"));

		Page page = signIn(/*this.webClient.getPage(response
				.getResponseHeaderValue("location"))*/
				this.webClient.getPage(AUTHORIZATION_REQUEST), "user1", "password");


		//in future look for the error message in the htmlPage
		LOG.info("is html page: {}, url: {}, content: {}", page.isHtmlPage(), page.getUrl(), page.getWebResponse().getContentAsString());

		LOG.info("The login process involves redirection in the OAuth2 workflow so we expect this url");
		assertThat(page.getUrl().toString()).startsWith("http://127.0.0.1:"+mockWebServer.getPort()+"/login/oauth2/code/messaging-client-oidc?code=");

		//1
		RecordedRequest recordedRequest = mockWebServer.takeRequest();
		LOG.info("should be acesstoken path for recordedRequest: {}", recordedRequest.getPath());
		AssertionsForClassTypes.assertThat(recordedRequest.getPath()).startsWith("/issuer/oauth2/token");
		AssertionsForClassTypes.assertThat(recordedRequest.getMethod()).isEqualTo("POST");

		//2
		recordedRequest = mockWebServer.takeRequest();
		assertThat(recordedRequest.getMethod()).isEqualTo("PUT");
		assertThat(recordedRequest.getPath()).startsWith("/accounts/lock");

		//3 for get user by auth id
		recordedRequest = mockWebServer.takeRequest();
		assertThat(recordedRequest.getMethod()).isEqualTo("GET");
		assertThat(recordedRequest.getPath()).startsWith("/users/");

		//4 check user exists in organization
		recordedRequest = mockWebServer.takeRequest();
		LOG.info("(recordedRequest.getPath()): {}", recordedRequest.getPath());
		assertThat(recordedRequest.getMethod()).isEqualTo("GET");
		assertThat(recordedRequest.getPath()).startsWith("/organizations/"+organizationId+"/users/"+userId);


		//5 authentication call
		recordedRequest = mockWebServer.takeRequest();
		assertThat(recordedRequest.getMethod()).isEqualTo("POST");
		assertThat(recordedRequest.getPath()).startsWith("/authentications/authenticate");

		//6 for /attempt/login/sucess
		recordedRequest = mockWebServer.takeRequest();
		assertThat(recordedRequest.getMethod()).isEqualTo("PUT");
		assertThat(recordedRequest.getPath()).startsWith("/attempts/login/success");

		//7 for redirection on successful login
		recordedRequest = mockWebServer.takeRequest();
		assertThat(recordedRequest.getMethod()).isEqualTo("GET");
		LOG.info("recordedRequest path: {}", recordedRequest.getPath());
		assertThat(recordedRequest.getPath()).startsWith("/login/oauth2/code/messaging-client-oidc?code=");
	}

	/**
	 * This will test the user logging in and will fail when the client has no organization association.
	 * It will then fail again when it is also checked to see if the client has a user association.
	 * It will then call the attempt-rest-service to record the failed login attempt.
	 * @throws IOException
	 * @throws InterruptedException
	 */
	@Test
	public void checkClientInOrganizationAndClientNotFound() throws IOException, InterruptedException {
		//clientOrganizationRepository.deleteAll();
		LOG.info("test the client organization relationship, user existence in organization");
		// Log in
		this.webClient.getOptions().setThrowExceptionOnFailingStatusCode(false);
		//set redirection false so we can login manually with code below
		this.webClient.getOptions().setRedirectEnabled(true);

		final String jwtString= "eyJhbGciOiJIUzUxMiJ9.eyJzdWIiOiJzb25hbSIsImlzcyI6InNvbmFtLmNsb3VkIiwiYXVkIjoic29uYW0uY2xvdWQiLCJqdGkiOiJmMTY2NjM1OS05YTViLTQ3NzMtOWUyNy00OGU0OTFlNDYzNGIifQ.KGFBUjghvcmNGDH0eM17S9pWkoLwbvDaDBGAx2AyB41yZ_8-WewTriR08JdjLskw1dsRYpMh9idxQ4BS6xmOCQ";

		//1
		final String jwtTokenMsg = " {\"access_token\":\""+jwtString+"\"}";
		mockWebServer.enqueue(new MockResponse().setHeader("Content-Type", "application/json")
				.setResponseCode(200).setBody(jwtTokenMsg));

		//2
		//account is not locked for user logging in by returning false
		mockWebServer.enqueue(new MockResponse().setHeader("Content-Type", "application/json")
				.setResponseCode(200).setBody("{\"message\":\"false\"}"));

		//3
		mockWebServer.enqueue(new MockResponse().setHeader("Content-Type", "application/json")
				.setResponseCode(200).setBody("{\"id\":\""+userId+"\", \"firstName\":\"Dommy\"}"));

		//4 when user does not exist or login fails, make call out to attempt-rest-service/attempts/loginFailed
		//to find out remaining attempts or lock out user by username
		mockWebServer.enqueue(new MockResponse().setHeader("Content-Type", "application/json")
				.setResponseCode(200).setBody("{\"message\":\"remaining login attempt 1\"}"));

		Page page = signIn(/*this.webClient.getPage(response
				.getResponseHeaderValue("location"))*/
				this.webClient.getPage(AUTHORIZATION_REQUEST), "user1", "password");
		//HTMLParser htmlParser = HTMLParser

		//LOG.info("textPage: {}", page.getUrl());

		LOG.info("assert we get back the same login page when client not found");
		//in future look for the error message in the htmlPage
		LOG.info("is html page: {}, url: {}, content: {}", page.isHtmlPage(), page.getUrl(), page.getWebResponse().getContentAsString());

		LOG.info("assert we got back the login page when clientId is not found");
		assertThat(page.getUrl().toString()).endsWith("?error");

		//1
		RecordedRequest recordedRequest = mockWebServer.takeRequest();
		LOG.info("should be acesstoken path for recordedRequest: {}", recordedRequest.getPath());
		AssertionsForClassTypes.assertThat(recordedRequest.getPath()).startsWith("/issuer/oauth2/token");
		AssertionsForClassTypes.assertThat(recordedRequest.getMethod()).isEqualTo("POST");

		//2
		recordedRequest = mockWebServer.takeRequest();
		assertThat(recordedRequest.getMethod()).isEqualTo("PUT");
		assertThat(recordedRequest.getPath()).startsWith("/accounts/lock");

		//3
		recordedRequest = mockWebServer.takeRequest();
		assertThat(recordedRequest.getMethod()).isEqualTo("GET");
		assertThat(recordedRequest.getPath()).startsWith("/users/");


		//4
		recordedRequest = mockWebServer.takeRequest();
		assertThat(recordedRequest.getMethod()).isEqualTo("PUT");
		assertThat(recordedRequest.getPath()).startsWith("/attempts/login/failed");
	}

	/**
     * This will test the user.get by authId failure.  It will then call the attempt-rest-service
	 * to record the login failure.
	 */
	@Test
	public void checkUserNotExist() throws IOException, InterruptedException {
		LOG.info("test the client organization relationship, user existence in organization");
		// Log in
		this.webClient.getOptions().setThrowExceptionOnFailingStatusCode(false);
		this.webClient.getOptions().setRedirectEnabled(true);

		LOG.info("serverPort: {}", AuthorizationServerApplicationUserLoginTests.serverPort);
		final String jwtString= "eyJhbGciOiJIUzUxMiJ9.eyJzdWIiOiJzb25hbSIsImlzcyI6InNvbmFtLmNsb3VkIiwiYXVkIjoic29uYW0uY2xvdWQiLCJqdGkiOiJmMTY2NjM1OS05YTViLTQ3NzMtOWUyNy00OGU0OTFlNDYzNGIifQ.KGFBUjghvcmNGDH0eM17S9pWkoLwbvDaDBGAx2AyB41yZ_8-WewTriR08JdjLskw1dsRYpMh9idxQ4BS6xmOCQ";

		//1
		final String jwtTokenMsg = " {\"access_token\":\""+jwtString+"\"}";
		mockWebServer.enqueue(new MockResponse().setHeader("Content-Type", "application/json")
				.setResponseCode(200).setBody(jwtTokenMsg));

		//2
		//this response is for getting user by authenticationId (loginId)
		mockWebServer.enqueue(new MockResponse().setHeader("Content-Type", "application/json")
				.setResponseCode(200).setBody("{\"message\":\"false\"}"));

		//3
		//this response is for getting user by authenticationId (loginId)
		mockWebServer.enqueue(new MockResponse().setHeader("Content-Type", "application/json")
				.setResponseCode(400).setBody("{\"error\":\"user not found\"}"));

		//4 when user does not exist or login fails, make call out to attempt-rest-service/attempts/loginFailed
		//to find out remaining attempts or lock out user by username
		mockWebServer.enqueue(new MockResponse().setHeader("Content-Type", "application/json")
				.setResponseCode(200).setBody("{\"message\":\"remaining login attempt 1\"}"));


		signIn(this.webClient.getPage(AUTHORIZATION_REQUEST), "user1", "password");

		LOG.info("take request");
		RecordedRequest recordedRequest = mockWebServer.takeRequest();
		LOG.info("should be acesstoken path for recordedRequest: {}", recordedRequest.getPath());
		AssertionsForClassTypes.assertThat(recordedRequest.getPath()).startsWith("/issuer/oauth2/token");
		AssertionsForClassTypes.assertThat(recordedRequest.getMethod()).isEqualTo("POST");

		recordedRequest = mockWebServer.takeRequest();
		assertThat(recordedRequest.getMethod()).isEqualTo("PUT");
		assertThat(recordedRequest.getPath()).startsWith("/accounts/lock");

		recordedRequest = mockWebServer.takeRequest();
		assertThat(recordedRequest.getMethod()).isEqualTo("GET");
		assertThat(recordedRequest.getPath()).startsWith("/users/authentication-id/user1");

		recordedRequest = mockWebServer.takeRequest();

		assertThat(recordedRequest.getMethod()).isEqualTo("PUT");
		assertThat(recordedRequest.getPath()).startsWith("/attempts/login/failed");
	}

    /**
	 * This will test the user can log in with the client not being associated to an organization.
	 * It will test the client being associated to a user.
	 * @throws IOException
	 * @throws InterruptedException
	 */

	@Test
	public void checkClientInOrganizationAndClientFoundInClientUser() throws IOException, InterruptedException {
		LOG.info("test the client organization relationship, user existence in organization");
		//clear out userOrganization relationship if there was any from prior relationship

		clientOrganizationRepository.deleteAll(); //clear client-organization association that maybe left over from
		//other tests.
		// Log in
		this.webClient.getOptions().setThrowExceptionOnFailingStatusCode(false);
		this.webClient.getOptions().setRedirectEnabled(true);

		deleteClientFromOrganization();
		//save User uuid with clientId
		saveClientUser(clientId, userId);
		final String jwtString= "eyJhbGciOiJIUzUxMiJ9.eyJzdWIiOiJzb25hbSIsImlzcyI6InNvbmFtLmNsb3VkIiwiYXVkIjoic29uYW0uY2xvdWQiLCJqdGkiOiJmMTY2NjM1OS05YTViLTQ3NzMtOWUyNy00OGU0OTFlNDYzNGIifQ.KGFBUjghvcmNGDH0eM17S9pWkoLwbvDaDBGAx2AyB41yZ_8-WewTriR08JdjLskw1dsRYpMh9idxQ4BS6xmOCQ";

		//1
		final String jwtTokenMsg = " {\"access_token\":\""+jwtString+"\"}";
		mockWebServer.enqueue(new MockResponse().setHeader("Content-Type", "application/json")
				.setResponseCode(200).setBody(jwtTokenMsg));

		//2
		//this response is for getting user by authenticationId (loginId)
		mockWebServer.enqueue(new MockResponse().setHeader("Content-Type", "application/json")
				.setResponseCode(200).setBody("{\"message\":\"false\"}"));

		//3 mock response for user-id http callout
		mockWebServer.enqueue(new MockResponse().setHeader("Content-Type", "application/json")
				.setResponseCode(200).setBody("{\"id\":\""+userId+"\", \"firstName\":\"Dommy\"}"));

		//4 user will be found from clientUser relationship
		//mock role names for authentication http callout
		mockWebServer.enqueue(new MockResponse().setHeader("Content-Type", "application/json")
				.setResponseCode(200).setBody("{\"roleNames\": \"[user, SuperAdmin]\", \"userId\": \""+ userId +"\", \"message\": \"Authentication successful\"}"));

		//5 login success is recorded before returning user roles in authentication
		mockWebServer.enqueue(new MockResponse().setHeader("Content-Type", "application/json")
				.setResponseCode(200).setBody("{\"message\": \"CONTINUE\"}"));

		//6 it seems like we need to mock one more response for the redirection to redirecUris: /login/oauth2/code/messaging-client-oidc?code=...
		mockWebServer.enqueue(new MockResponse().setHeader("Content-Type", "application/json")
				.setResponseCode(200).setBody("{\"roleNames\": \"[user, SuperAdmin]\", \"userId\": \""+ userId +"\", \"message\": \"Authentication successful\"}"));

		LOG.info("sign-in to the location page");

		//login should work for client as client should be found in ClientUser relationship
		signIn(/*this.webClient.getPage(response
				.getResponseHeaderValue("location"))*/this.webClient.getPage(AUTHORIZATION_REQUEST),
				"user1", "password");

		//1
		RecordedRequest recordedRequest = mockWebServer.takeRequest();
		LOG.info("should be acesstoken path for recordedRequest: {}", recordedRequest.getPath());
		AssertionsForClassTypes.assertThat(recordedRequest.getPath()).startsWith("/issuer/oauth2/token");
		AssertionsForClassTypes.assertThat(recordedRequest.getMethod()).isEqualTo("POST");

		//2
		recordedRequest = mockWebServer.takeRequest();
		assertThat(recordedRequest.getMethod()).isEqualTo("PUT");
		assertThat(recordedRequest.getPath()).startsWith("/accounts/lock");

		//3
		recordedRequest = mockWebServer.takeRequest();
		assertThat(recordedRequest.getMethod()).isEqualTo("GET");
		assertThat(recordedRequest.getPath()).startsWith("/users/");

		//4
		recordedRequest = mockWebServer.takeRequest();
		assertThat(recordedRequest.getMethod()).isEqualTo("POST");
		assertThat(recordedRequest.getPath()).startsWith("/authentications/authenticate");

		//5
		recordedRequest = mockWebServer.takeRequest();
		assertThat(recordedRequest.getMethod()).isEqualTo("PUT");
		assertThat(recordedRequest.getPath()).startsWith("/attempts/login/success");

		//6
		recordedRequest = mockWebServer.takeRequest();
		assertThat(recordedRequest.getMethod()).isEqualTo("GET");
		assertThat(recordedRequest.getPath()).startsWith("/login/oauth2/code/messaging-client-oidc?code=");
	}

	/**
	 * test the failure when the authentication fails
	 * 	This will test the user authentication failure which happens to be the last call
	 * 	made by the {@link me.sonam.auth.service.AuthenticationCallout#authenticate(Authentication)}
	 */
	@Test
	public void checkUserAuthenticationFailure() throws IOException, InterruptedException {
		LOG.info("This will test the user authentication failure");
		//clear out userOrganization relationship if there was any from prior relationship

		// Log in
		this.webClient.getOptions().setThrowExceptionOnFailingStatusCode(false);
		this.webClient.getOptions().setRedirectEnabled(true);


		//save User uuid with clientId
		saveClientUser(clientId, userId);
		final String jwtString= "eyJhbGciOiJIUzUxMiJ9.eyJzdWIiOiJzb25hbSIsImlzcyI6InNvbmFtLmNsb3VkIiwiYXVkIjoic29uYW0uY2xvdWQiLCJqdGkiOiJmMTY2NjM1OS05YTViLTQ3NzMtOWUyNy00OGU0OTFlNDYzNGIifQ.KGFBUjghvcmNGDH0eM17S9pWkoLwbvDaDBGAx2AyB41yZ_8-WewTriR08JdjLskw1dsRYpMh9idxQ4BS6xmOCQ";

		//1
		final String jwtTokenMsg = " {\"access_token\":\""+jwtString+"\"}";
		mockWebServer.enqueue(new MockResponse().setHeader("Content-Type", "application/json")
				.setResponseCode(200).setBody(jwtTokenMsg));

		//2
		//this response is for getting user by authenticationId (loginId)
		mockWebServer.enqueue(new MockResponse().setHeader("Content-Type", "application/json")
				.setResponseCode(200).setBody("{\"message\":\"false\"}"));

		//3 mock response for user-id http callout
		mockWebServer.enqueue(new MockResponse().setHeader("Content-Type", "application/json")
				.setResponseCode(200).setBody("{\"id\":\""+userId+"\", \"firstName\":\"Dommy\"}"));

		//4 it seems like we need to mock one more response for the redirection to redirecUris: /login/oauth2/code/messaging-client-oidc?code=...
		mockWebServer.enqueue(new MockResponse().setHeader("Content-Type", "application/json")
				.setResponseCode(400).setBody("{\"error\": \"error occurred\"}"));

		//5 when user does not exist or login fails, make call out to attempt-rest-service/attempts/loginFailed
		//to find out remaining attempts or lock out user by username
		mockWebServer.enqueue(new MockResponse().setHeader("Content-Type", "application/json")
				.setResponseCode(200).setBody("{\"message\":\"remaining login attempt 1\"}"));

		LOG.info("sign-in to the location page");

		//login should work for client as client should be found in ClientUser relationship
		signIn(/*this.webClient.getPage(response
				.getResponseHeaderValue("location"))*/this.webClient.getPage(AUTHORIZATION_REQUEST),
				"user1", "password");

		//1
		RecordedRequest recordedRequest = mockWebServer.takeRequest();
		LOG.info("should be acesstoken path for recordedRequest: {}", recordedRequest.getPath());
		AssertionsForClassTypes.assertThat(recordedRequest.getPath()).startsWith("/issuer/oauth2/token");
		AssertionsForClassTypes.assertThat(recordedRequest.getMethod()).isEqualTo("POST");

		//2
		recordedRequest = mockWebServer.takeRequest();
		assertThat(recordedRequest.getMethod()).isEqualTo("PUT");
		assertThat(recordedRequest.getPath()).startsWith("/accounts/lock");

		//3
		recordedRequest = mockWebServer.takeRequest();
		assertThat(recordedRequest.getMethod()).isEqualTo("GET");
		assertThat(recordedRequest.getPath()).startsWith("/users/");

		//4
		recordedRequest = mockWebServer.takeRequest();
		assertThat(recordedRequest.getMethod()).isEqualTo("POST");
		assertThat(recordedRequest.getPath()).startsWith("/authentications/authenticate");

		//5
		recordedRequest = mockWebServer.takeRequest();
		assertThat(recordedRequest.getMethod()).isEqualTo("PUT");
		assertThat(recordedRequest.getPath()).startsWith("/attempts/login/failed");
	}


	/**
	 * This will test the last call in authentication call where authentication fails.
	 * The attempt-rest-service will request to
	 * lock the account in the response when login failure endpoint is called.
	 * The account-rest-service is called to lock account then.
	 */
	@Test
	public void authenticationFailureAndLockAccount() throws IOException, InterruptedException {
		saveClientOrganization(clientId, organizationId);

		// Log in
		this.webClient.getOptions().setThrowExceptionOnFailingStatusCode(false);
		//set redirection false so we can login manually with code below
		this.webClient.getOptions().setRedirectEnabled(true);

		final String jwtString= "eyJhbGciOiJIUzUxMiJ9.eyJzdWIiOiJzb25hbSIsImlzcyI6InNvbmFtLmNsb3VkIiwiYXVkIjoic29uYW0uY2xvdWQiLCJqdGkiOiJmMTY2NjM1OS05YTViLTQ3NzMtOWUyNy00OGU0OTFlNDYzNGIifQ.KGFBUjghvcmNGDH0eM17S9pWkoLwbvDaDBGAx2AyB41yZ_8-WewTriR08JdjLskw1dsRYpMh9idxQ4BS6xmOCQ";

		//1
		final String jwtTokenMsg = " {\"access_token\":\""+jwtString+"\"}";
		mockWebServer.enqueue(new MockResponse().setHeader("Content-Type", "application/json")
				.setResponseCode(200).setBody(jwtTokenMsg));

		//2
		//user not locked
		mockWebServer.enqueue(new MockResponse().setHeader("Content-Type", "application/json")
				.setResponseCode(200).setBody("{\"message\":\"false\"}"));

		//3 for get user data by auth-id
		mockWebServer.enqueue(new MockResponse().setHeader("Content-Type", "application/json")
				.setResponseCode(200).setBody("{\"id\":\""+userId+"\", \"firstName\":\"Dommy\"}"));

		//4 user exists in organization call to return true
		mockWebServer.enqueue(new MockResponse().setHeader("Content-Type", "application/json")
				.setResponseCode(200).setBody("{\"message\": true}"));

		//5 this is returned for authentications authenticate call mock response authentication failure
		// and message
		mockWebServer.enqueue(new MockResponse().setHeader("Content-Type", "application/json")
				.setResponseCode(400).setBody("{\"error\": \"no authentication found with username and password\"}"));

		//6 login failure attempt-rest-service call and gets You can retry message indicating a lock out of user
		mockWebServer.enqueue(new MockResponse().setHeader("Content-Type", "application/json")
				.setResponseCode(200).setBody("{\"message\": \"You can retry login again in 1 hour\"}"));

		//7, on lock-out of login failure in attempt-rest-service, call account-rest-service to lock account
		mockWebServer.enqueue(new MockResponse().setHeader("Content-Type", "application/json")
				.setResponseCode(200).setBody("{\"message\": \"account is now locked for authenticationId: \"}"));

		Page page = signIn(/*this.webClient.getPage(response
				.getResponseHeaderValue("location"))*/
				this.webClient.getPage(AUTHORIZATION_REQUEST), "user1", "password");

		//in future look for the error message in the htmlPage
		LOG.info("is html page: {}, url: {}, content: {}", page.isHtmlPage(), page.getUrl(), page.getWebResponse().getContentAsString());

		LOG.info("The login process involves redirection in the OAuth2 workflow so we expect this url");
		assertThat(page.getUrl().toString()).endsWith("?error");

		//1
		RecordedRequest recordedRequest = mockWebServer.takeRequest();
		LOG.info("should be acesstoken path for recordedRequest: {}", recordedRequest.getPath());
		AssertionsForClassTypes.assertThat(recordedRequest.getPath()).startsWith("/issuer/oauth2/token");
		AssertionsForClassTypes.assertThat(recordedRequest.getMethod()).isEqualTo("POST");

		//2 for check if account is locked
		recordedRequest = mockWebServer.takeRequest();
		assertThat(recordedRequest.getMethod()).isEqualTo("PUT");
		assertThat(recordedRequest.getPath()).startsWith("/accounts/lock");

		//3 for get user by auth id
		recordedRequest = mockWebServer.takeRequest();
		assertThat(recordedRequest.getMethod()).isEqualTo("GET");
		assertThat(recordedRequest.getPath()).startsWith("/users/");

		//4 check user exists in organization
		recordedRequest = mockWebServer.takeRequest();
		LOG.info("(recordedRequest.getPath()): {}", recordedRequest.getPath());
		assertThat(recordedRequest.getMethod()).isEqualTo("GET");
		assertThat(recordedRequest.getPath()).startsWith("/organizations/"+organizationId+"/users/"+userId);

		//5 authentication call
		recordedRequest = mockWebServer.takeRequest();
		assertThat(recordedRequest.getMethod()).isEqualTo("POST");
		assertThat(recordedRequest.getPath()).startsWith("/authentications/authenticate");

		//6 for /attempt/login/sucess
		recordedRequest = mockWebServer.takeRequest();
		assertThat(recordedRequest.getMethod()).isEqualTo("PUT");
		assertThat(recordedRequest.getPath()).startsWith("/attempts/login/failed");

		//7 for /attempt/login/sucess
		recordedRequest = mockWebServer.takeRequest();
		assertThat(recordedRequest.getMethod()).isEqualTo("PUT");
		assertThat(recordedRequest.getPath()).startsWith("/accounts/lock/true");
	}

	private static <P extends Page> P signIn(HtmlPage page, String username, String password) throws IOException {
		LOG.info("page: {}, done end", page.toString());
		HtmlInput usernameInput = page.querySelector("input[name=\"username\"]");
		HtmlInput passwordInput = page.querySelector("input[name=\"password\"]");
		HtmlButton signInButton = page.querySelector("button");

		usernameInput.type(username);
		passwordInput.type(password);
		LOG.info("sign in button: {}", signInButton);
		P p = signInButton.click();
		LOG.info("signIn button clicked?: {}", p.getUrl());

		return p;
	}

	private static void assertLoginPage(HtmlPage page) {
		assertThat(page.getUrl().toString()).endsWith("/");

		HtmlInput usernameInput = page.querySelector("input[name=\"username\"]");
		HtmlInput passwordInput = page.querySelector("input[name=\"password\"]");
		HtmlButton signInButton = page.querySelector("button");

		assertThat(usernameInput).isNotNull();
		assertThat(passwordInput).isNotNull();
		assertThat(signInButton.getTextContent()).isEqualTo("Sign in");
	}

}
