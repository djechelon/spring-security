/*
 * Copyright 2002-2021 the original author or authors.
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

package org.springframework.security.oauth2.client.endpoint;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Collections;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.core.convert.converter.Converter;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.TestClientRegistrations;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.OAuth2AuthorizationException;
import org.springframework.security.oauth2.core.endpoint.OAuth2AccessTokenResponse;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.validateMockitoUsage;
import static org.mockito.Mockito.verify;

/**
 * @author Rob Winch
 */
public class WebClientReactiveClientCredentialsTokenResponseClientTests {

	private MockWebServer server;

	private WebClientReactiveClientCredentialsTokenResponseClient client = new WebClientReactiveClientCredentialsTokenResponseClient();

	private ClientRegistration.Builder clientRegistration;

	@BeforeEach
	public void setup() throws Exception {
		this.server = new MockWebServer();
		this.server.start();
		this.clientRegistration = TestClientRegistrations.clientCredentials()
				.tokenUri(this.server.url("/oauth2/token").uri().toASCIIString());
	}

	@AfterEach
	public void cleanup() throws Exception {
		validateMockitoUsage();
		this.server.shutdown();
	}

	@Test
	public void getTokenResponseWhenHeaderThenSuccess() throws Exception {
		// @formatter:off
		enqueueJson("{\n"
			+ "  \"access_token\":\"MTQ0NjJkZmQ5OTM2NDE1ZTZjNGZmZjI3\",\n"
			+ "  \"token_type\":\"bearer\",\n"
			+ "  \"expires_in\":3600,\n"
			+ "  \"refresh_token\":\"IwOGYzYTlmM2YxOTQ5MGE3YmNmMDFkNTVk\",\n"
			+ "  \"scope\":\"create\"\n"
			+ "}");
		// @formatter:on
		OAuth2ClientCredentialsGrantRequest request = new OAuth2ClientCredentialsGrantRequest(
				this.clientRegistration.build());
		OAuth2AccessTokenResponse response = this.client.getTokenResponse(request).block();
		RecordedRequest actualRequest = this.server.takeRequest();
		String body = actualRequest.getUtf8Body();
		assertThat(response.getAccessToken()).isNotNull();
		assertThat(actualRequest.getHeader(HttpHeaders.AUTHORIZATION))
				.isEqualTo("Basic Y2xpZW50LWlkOmNsaWVudC1zZWNyZXQ=");
		assertThat(body).isEqualTo("grant_type=client_credentials&scope=read%3Auser");
	}

	// gh-9610
	@Test
	public void getTokenResponseWhenSpecialCharactersThenSuccessWithEncodedClientCredentials() throws Exception {
		// @formatter:off
		enqueueJson("{\n"
			+ "  \"access_token\":\"MTQ0NjJkZmQ5OTM2NDE1ZTZjNGZmZjI3\",\n"
			+ "  \"token_type\":\"bearer\",\n"
			+ "  \"expires_in\":3600,\n"
			+ "  \"refresh_token\":\"IwOGYzYTlmM2YxOTQ5MGE3YmNmMDFkNTVk\",\n"
			+ "  \"scope\":\"create\"\n"
			+ "}");
		// @formatter:on
		String clientCredentialWithAnsiKeyboardSpecialCharacters = "~!@#$%^&*()_+{}|:\"<>?`-=[]\\;',./ ";
		OAuth2ClientCredentialsGrantRequest request = new OAuth2ClientCredentialsGrantRequest(
				this.clientRegistration.clientId(clientCredentialWithAnsiKeyboardSpecialCharacters)
						.clientSecret(clientCredentialWithAnsiKeyboardSpecialCharacters).build());
		OAuth2AccessTokenResponse response = this.client.getTokenResponse(request).block();
		RecordedRequest actualRequest = this.server.takeRequest();
		String body = actualRequest.getBody().readUtf8();
		assertThat(response.getAccessToken()).isNotNull();
		String urlEncodedClientCredentialecret = URLEncoder.encode(clientCredentialWithAnsiKeyboardSpecialCharacters,
				StandardCharsets.UTF_8.toString());
		String clientCredentials = Base64.getEncoder()
				.encodeToString((urlEncodedClientCredentialecret + ":" + urlEncodedClientCredentialecret)
						.getBytes(StandardCharsets.UTF_8));
		assertThat(actualRequest.getHeader(HttpHeaders.AUTHORIZATION)).isEqualTo("Basic " + clientCredentials);
		assertThat(body).isEqualTo("grant_type=client_credentials&scope=read%3Auser");
	}

	@Test
	public void getTokenResponseWhenPostThenSuccess() throws Exception {
		ClientRegistration registration = this.clientRegistration
				.clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_POST).build();
		// @formatter:off
		enqueueJson("{\n"
			+ "  \"access_token\":\"MTQ0NjJkZmQ5OTM2NDE1ZTZjNGZmZjI3\",\n"
			+ "  \"token_type\":\"bearer\",\n"
			+ "  \"expires_in\":3600,\n"
			+ "  \"refresh_token\":\"IwOGYzYTlmM2YxOTQ5MGE3YmNmMDFkNTVk\",\n"
			+ "  \"scope\":\"create\"\n"
			+ "}");
		// @formatter:on
		OAuth2ClientCredentialsGrantRequest request = new OAuth2ClientCredentialsGrantRequest(registration);
		OAuth2AccessTokenResponse response = this.client.getTokenResponse(request).block();
		RecordedRequest actualRequest = this.server.takeRequest();
		String body = actualRequest.getUtf8Body();
		assertThat(response.getAccessToken()).isNotNull();
		assertThat(actualRequest.getHeader(HttpHeaders.AUTHORIZATION)).isNull();
		assertThat(body).isEqualTo(
				"grant_type=client_credentials&client_id=client-id&client_secret=client-secret&scope=read%3Auser");
	}

	@Test
	public void getTokenResponseWhenNoScopeThenClientRegistrationScopesDefaulted() {
		ClientRegistration registration = this.clientRegistration.build();
		// @formatter:off
		enqueueJson("{\n"
		+ "  \"access_token\":\"MTQ0NjJkZmQ5OTM2NDE1ZTZjNGZmZjI3\",\n"
		+ "  \"token_type\":\"bearer\",\n"
		+ "  \"expires_in\":3600,\n"
		+ "  \"refresh_token\":\"IwOGYzYTlmM2YxOTQ5MGE3YmNmMDFkNTVk\"\n"
		+ "}");
		// @formatter:on
		OAuth2ClientCredentialsGrantRequest request = new OAuth2ClientCredentialsGrantRequest(registration);
		OAuth2AccessTokenResponse response = this.client.getTokenResponse(request).block();
		assertThat(response.getAccessToken().getScopes()).isEqualTo(registration.getScopes());
	}

	@Test
	public void setWebClientNullThenIllegalArgumentException() {
		assertThatIllegalArgumentException().isThrownBy(() -> this.client.setWebClient(null));
	}

	@Test
	public void setWebClientCustomThenCustomClientIsUsed() {
		WebClient customClient = mock(WebClient.class);
		given(customClient.post()).willReturn(WebClient.builder().build().post());
		this.client.setWebClient(customClient);
		ClientRegistration registration = this.clientRegistration.build();
		// @formatter:off
		enqueueJson("{\n"
			+ "  \"access_token\":\"MTQ0NjJkZmQ5OTM2NDE1ZTZjNGZmZjI3\",\n"
			+ "  \"token_type\":\"bearer\",\n"
			+ "  \"expires_in\":3600,\n"
			+ "  \"refresh_token\":\"IwOGYzYTlmM2YxOTQ5MGE3YmNmMDFkNTVk\"\n"
			+ "}");
		// @formatter:on
		OAuth2ClientCredentialsGrantRequest request = new OAuth2ClientCredentialsGrantRequest(registration);
		OAuth2AccessTokenResponse response = this.client.getTokenResponse(request).block();
		verify(customClient, atLeastOnce()).post();
	}

	@Test
	public void getTokenResponseWhenInvalidResponse() throws WebClientResponseException {
		ClientRegistration registration = this.clientRegistration.build();
		enqueueUnexpectedResponse();
		OAuth2ClientCredentialsGrantRequest request = new OAuth2ClientCredentialsGrantRequest(registration);
		assertThatExceptionOfType(OAuth2AuthorizationException.class)
				.isThrownBy(() -> this.client.getTokenResponse(request).block())
				.satisfies((ex) -> assertThat(ex.getError().getErrorCode()).isEqualTo("invalid_token_response"))
				.withMessageContaining("[invalid_token_response]")
				.withMessageContaining("Empty OAuth 2.0 Access Token Response");
	}

	private void enqueueUnexpectedResponse() {
		// @formatter:off
		MockResponse response = new MockResponse()
				.setHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
				.setResponseCode(301);
		// @formatter:on
		this.server.enqueue(response);
	}

	private void enqueueJson(String body) {
		MockResponse response = new MockResponse().setBody(body).setHeader(HttpHeaders.CONTENT_TYPE,
				MediaType.APPLICATION_JSON_VALUE);
		this.server.enqueue(response);
	}

	// gh-10130
	@Test
	public void setHeadersConverterWhenNullThenThrowIllegalArgumentException() {
		assertThatIllegalArgumentException().isThrownBy(() -> this.client.setHeadersConverter(null))
				.withMessage("headersConverter cannot be null");
	}

	// gh-10130
	@Test
	public void addHeadersConverterWhenNullThenThrowIllegalArgumentException() {
		assertThatIllegalArgumentException().isThrownBy(() -> this.client.addHeadersConverter(null))
				.withMessage("headersConverter cannot be null");
	}

	// gh-10130
	@Test
	public void convertWhenHeadersConverterAddedThenCalled() throws Exception {
		OAuth2ClientCredentialsGrantRequest request = new OAuth2ClientCredentialsGrantRequest(
				this.clientRegistration.build());
		Converter<OAuth2ClientCredentialsGrantRequest, HttpHeaders> addedHeadersConverter = mock(Converter.class);
		HttpHeaders headers = new HttpHeaders();
		headers.put("custom-header-name", Collections.singletonList("custom-header-value"));
		given(addedHeadersConverter.convert(request)).willReturn(headers);
		this.client.addHeadersConverter(addedHeadersConverter);
		// @formatter:off
		enqueueJson("{\n"
				+ "  \"access_token\":\"MTQ0NjJkZmQ5OTM2NDE1ZTZjNGZmZjI3\",\n"
				+ "  \"token_type\":\"bearer\",\n"
				+ "  \"expires_in\":3600,\n"
				+ "  \"refresh_token\":\"IwOGYzYTlmM2YxOTQ5MGE3YmNmMDFkNTVk\"\n"
				+ "}");
		// @formatter:on
		this.client.getTokenResponse(request).block();
		verify(addedHeadersConverter).convert(request);
		RecordedRequest actualRequest = this.server.takeRequest();
		assertThat(actualRequest.getHeader(HttpHeaders.AUTHORIZATION))
				.isEqualTo("Basic Y2xpZW50LWlkOmNsaWVudC1zZWNyZXQ=");
		assertThat(actualRequest.getHeader("custom-header-name")).isEqualTo("custom-header-value");
	}

	// gh-10130
	@Test
	public void convertWhenHeadersConverterSetThenCalled() throws Exception {
		OAuth2ClientCredentialsGrantRequest request = new OAuth2ClientCredentialsGrantRequest(
				this.clientRegistration.build());
		ClientRegistration clientRegistration = request.getClientRegistration();
		Converter<OAuth2ClientCredentialsGrantRequest, HttpHeaders> headersConverter = mock(Converter.class);
		HttpHeaders headers = new HttpHeaders();
		headers.setBasicAuth(clientRegistration.getClientId(), clientRegistration.getClientSecret());
		given(headersConverter.convert(request)).willReturn(headers);
		this.client.setHeadersConverter(headersConverter);
		// @formatter:off
		enqueueJson("{\n"
				+ "  \"access_token\":\"MTQ0NjJkZmQ5OTM2NDE1ZTZjNGZmZjI3\",\n"
				+ "  \"token_type\":\"bearer\",\n"
				+ "  \"expires_in\":3600,\n"
				+ "  \"refresh_token\":\"IwOGYzYTlmM2YxOTQ5MGE3YmNmMDFkNTVk\"\n"
				+ "}");
		// @formatter:on
		this.client.getTokenResponse(request).block();
		verify(headersConverter).convert(request);
		RecordedRequest actualRequest = this.server.takeRequest();
		assertThat(actualRequest.getHeader(HttpHeaders.AUTHORIZATION))
				.isEqualTo("Basic Y2xpZW50LWlkOmNsaWVudC1zZWNyZXQ=");
	}

}
