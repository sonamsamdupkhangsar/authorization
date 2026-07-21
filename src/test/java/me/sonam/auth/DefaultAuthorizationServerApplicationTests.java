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

import org.htmlunit.Page;
import org.htmlunit.WebClient;
import org.htmlunit.WebResponse;
import org.htmlunit.html.HtmlButton;
import org.htmlunit.html.HtmlElement;
import org.htmlunit.html.HtmlInput;
import org.htmlunit.html.HtmlPage;
import me.sonam.auth.jpa.entity.ClientOrganization;
import me.sonam.auth.jpa.entity.ClientOrganizationId;
import me.sonam.auth.jpa.entity.ClientUser;
import me.sonam.auth.jpa.entity.ClientUserId;
import me.sonam.auth.jpa.repo.ClientOrganizationRepository;
import me.sonam.auth.jpa.repo.HClientUserRepository;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test cases in the original one does not apply for the use case I have in this implementation.
 * My implementation of the authorization server requires http callouts to user-rest-service
 *  for getting user-id of a person logging, checking if that user-id exists in an organization using
 *  organization-rest-service and then authenticating using authentication-rest-service.
 * My use case requires that there be a client-id always for a user logging-in.  It is multi-tenant in that
 * I want to have multiple organizations using the same authorization server maintaining their own client-ids.
 *
 */

@ExtendWith(SpringExtension.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
public class DefaultAuthorizationServerApplicationTests {
	private static final Logger LOG = LoggerFactory.getLogger(DefaultAuthorizationServerApplicationTests.class);

	private static final String REDIRECT_URI = "http://127.0.0.1:{server.port}/login/oauth2/code/messaging-client-oidc";
	//private static String REDIRECT_URI = "http://localhost:{server.port}/login";
	static final String clientsClientId = "messaging-client";
	static final UUID clientId = UUID.randomUUID(); //"messaging-client";
	private static UUID userId = UUID.randomUUID();
	private static UUID organizationId = UUID.randomUUID();
	private static String AUTHORIZATION_REQUEST = "";
	private static MockWebServer mockWebServer;


	@Autowired
	private ClientOrganizationRepository clientOrganizationRepository;

	@Autowired
	private HClientUserRepository clientUserRepository;

	//@BeforeEach
	private void saveClientOrganization(final UUID clientId, UUID organizationId) {
		if (!clientOrganizationRepository.existsByClientId(clientId).get()) {
			clientOrganizationRepository.save(new ClientOrganization(clientId, organizationId));
			LOG.info("saved clientId {} with organizationId {}", clientId, organizationId);
		}
	}
	//@BeforeEach
	private void saveClientUser(final UUID clientId, UUID userId) {
		if (!clientUserRepository.existsById(new ClientUserId(clientId, userId))) {
			clientUserRepository.save(new ClientUser(clientId, userId));
			LOG.info("saved clientUser");
		}
	}

	@BeforeEach
	public void deleteClientFromOrganizaton() {
		LOG.info("delete clientOrganizationId from clientOrganization with clientId {} and organizationId {}",
				clientId, organizationId);
		clientOrganizationRepository.deleteById(new ClientOrganizationId(clientId, organizationId));

		clientOrganizationRepository.findByClientId(clientId).ifPresent(clientOrganization ->
				LOG.info("still found clientOrganization {}", clientOrganization));
	}

	@BeforeEach
	public void deleteClientUser() {
		LOG.info("delete clientUserId from clientUser");
		clientUserRepository.deleteById(new ClientUserId(clientId, userId));
		//clientUserRepository.deleteAll();
	}

	public
	@BeforeAll
	static void setupMockWebServer() throws IOException {
		mockWebServer = new MockWebServer();
		mockWebServer.start();

		LOG.info("host: {}, port: {}", mockWebServer.getHostName(), mockWebServer.getPort());
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

	//@Test
	public void whenLoginSuccessfulThenDisplayNotFoundError() throws IOException, InterruptedException {
		LOG.info("test whenLoginSuccessfulThenDisplayNotFoundError()");
		UUID organizationId = UUID.randomUUID();
		saveClientOrganization(clientId, organizationId);	//save client ("messaging-client" with organizationId)

		mockWebServer.enqueue(new MockResponse().setHeader("Content-Type", "application/json")
				.setResponseCode(200).setBody("{\"id\":\"cf792fa5-f2e9-4cfa-b099-7f62f2d15b38\", \"firstName\":\"Dommy\"}"));

		mockWebServer.enqueue(new MockResponse().setHeader("Content-Type", "application/json")
				.setResponseCode(200).setBody("{\"message\":true}"));

		mockWebServer.enqueue(new MockResponse().setHeader("Content-Type", "application/json")
				.setResponseCode(200).setBody("{\"roleNames\": \"[user, OrgAdmin]\", \"message\": \"Authentication successful\"}"));

		//HtmlPage page = this.webClient.getPage("/");
		HtmlPage page = this.webClient.getPage(AUTHORIZATION_REQUEST);//.getWebResponse();
		assertLoginPage(page);//this.webClient.getPage(webResponse.getResponseHeaderValue("location")));

		this.webClient.getOptions().setThrowExceptionOnFailingStatusCode(false);
		WebResponse signInResponse = signIn(page, "user1", "password").getWebResponse();

		RecordedRequest recordedRequest = mockWebServer.takeRequest();

		assertThat(recordedRequest.getMethod()).isEqualTo("GET");
		assertThat(recordedRequest.getPath()).startsWith("/users/");

		recordedRequest = mockWebServer.takeRequest();
		assertThat(recordedRequest.getMethod()).isEqualTo("GET");
		assertThat(recordedRequest.getPath()).startsWith("/organizations/");//userExistsInOrganization http call

		assertThat(recordedRequest.getMethod()).isEqualTo("POST");
		assertThat(recordedRequest.getPath()).startsWith("/authentications/verify-password");

		assertThat(signInResponse.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND.value());	// there is no "default" index page
	}

	//@Test
	public void whenLoginFailsThenDisplayBadCredentials() throws IOException, InterruptedException {
		LOG.info("test whenLoginFailsThenDisplayBadCredentials()");
		HtmlPage page = this.webClient.getPage("/");

		mockWebServer.enqueue(new MockResponse().setHeader("Content-Type", "application/json")
				.setResponseCode(401).setBody("Bad Credentials"));

		HtmlPage loginErrorPage = signIn(page, "user1", "wrong-password");

		RecordedRequest recordedRequest = mockWebServer.takeRequest();
		assertThat(recordedRequest.getMethod()).isEqualTo("POST");
		assertThat(recordedRequest.getPath()).startsWith("/authentications/verify-password");

		HtmlElement alert = loginErrorPage.querySelector("div[role=\"alert\"]");
		assertThat(alert).isNotNull();
		assertThat(alert.getTextContent()).isEqualTo("Bad credentials");
	}

	@Test
	public void whenNotLoggedInAndRequestingTokenThenRedirectsToLogin() throws IOException {
		LOG.info("test whenNotLoggedInAndRequestingTokenThenRedirectsToLogin()");
		HtmlPage page = this.webClient.getPage(AUTHORIZATION_REQUEST);

		assertLoginPage(page);
	}

	//@Test
	public void whenLoggingInAndRequestingTokenThenRedirectsToClientApplication() throws IOException, InterruptedException {
		LOG.info("test whenLoggingInAndRequestingTokenThenRedirectsToClientApplication()");
		// Log in
		this.webClient.getOptions().setThrowExceptionOnFailingStatusCode(false);
		this.webClient.getOptions().setRedirectEnabled(false);

		//{"error":"user does not exist in organization"} for not in org
		mockWebServer.enqueue(new MockResponse().setHeader("Content-Type", "application/json")
				.setResponseCode(200).setBody("{\"message\":true}"));

		mockWebServer.enqueue(new MockResponse().setHeader("Content-Type", "application/json")
				.setResponseCode(200).setBody("{id=cf792fa5-f2e9-4cfa-b099-7f62f2d15b38, firstName='Dommy', lastName='thecat', email='dommy@cat.email', birthDate=null, profilePhoto='null', genderId=null, newAccount=false}"));

		mockWebServer.enqueue(new MockResponse().setHeader("Content-Type", "application/json")
				.setResponseCode(200).setBody("{\"roleNames\": \"[user, OrgAdmin]\", \"message\": \"Authentication successful\"}"));

		//WebResponse response = this.webClient.getPage(AUTHORIZATION_REQUEST).getWebResponse();
		/*assertThat(response.getStatusCode()).isEqualTo(HttpStatus.MOVED_PERMANENTLY.value());
		String location = response.getResponseHeaderValue("location");
		assertThat(location).startsWith(REDIRECT_URI);
		assertThat(location).contains("code=");
*/
		signIn(this.webClient.getPage("/login"), "user1", "password");

		LOG.info("get page");
		// Request token
		WebResponse response = this.webClient.getPage(AUTHORIZATION_REQUEST).getWebResponse();

		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.MOVED_PERMANENTLY.value());
		String location = response.getResponseHeaderValue("location");
		LOG.info("response location: {}", location);
		assertThat(location).startsWith(REDIRECT_URI);
		assertThat(location).contains("code=");


		RecordedRequest recordedRequest = mockWebServer.takeRequest();

/*
		assertThat(recordedRequest.getMethod()).isEqualTo("GET");
		assertThat(recordedRequest.getPath()).startsWith("/organizations/");//userExistsInOrganization http call

		assertThat(recordedRequest.getMethod()).isEqualTo("GET");
		assertThat(recordedRequest.getPath()).startsWith("/users/");
*/

		assertThat(recordedRequest.getMethod()).isEqualTo("POST");
		assertThat(recordedRequest.getPath()).startsWith("/authentications/verify-password");

/*		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.MOVED_PERMANENTLY.value());
		String location = response.getResponseHeaderValue("location");
		assertThat(location).startsWith(REDIRECT_URI);
		assertThat(location).contains("code=");*/
	}




	private static <P extends Page> P signIn(HtmlPage page, String username, String password) throws IOException {
		//LOG.info("page: {}, done end", page.toString());
		HtmlInput usernameInput = page.querySelector("input[name=\"username\"]");
		HtmlInput passwordInput = page.querySelector("input[name=\"password\"]");
		HtmlButton signInButton = page.querySelector("button");

		usernameInput.type(username);
		passwordInput.type(password);
		return signInButton.click();
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
