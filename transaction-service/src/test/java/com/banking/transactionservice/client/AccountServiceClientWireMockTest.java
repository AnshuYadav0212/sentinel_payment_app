package com.banking.transactionservice.client;

import com.github.tomakehurst.wiremock.WireMockServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.cloud.openfeign.FeignAutoConfiguration;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringJUnitConfig(AccountServiceClientWireMockTest.TestConfig.class)
class AccountServiceClientWireMockTest {

    private static final WireMockServer wireMock =
            new WireMockServer(0);

    @BeforeAll
    static void startServer() {
        wireMock.start();
    }

    @AfterAll
    static void stopServer() {
        wireMock.stop();
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add(
                "account.service.url",
                () -> "http://localhost:" + wireMock.port()
        );
    }

    @org.springframework.beans.factory.annotation.Autowired
    private AccountServiceClient accountServiceClient;

    @Test
    void getAccount_shouldReturnActiveAccountWhenDownstreamReturns200() {

        wireMock.stubFor(
                get(urlEqualTo("/api/v1/accounts/987654321012"))
                        .willReturn(okJson("""
                                {
                                  "accountNumber":"987654321012",
                                  "status":"ACTIVE"
                                }
                                """))
        );

        AccountLookupResponse response =
                accountServiceClient.getAccount("987654321012");

        assertThat(response.accountNumber())
                .isEqualTo("987654321012");

        assertThat(response.status())
                .isEqualTo("ACTIVE");
    }

    @Test
    void getAccount_shouldPropagate404WhenAccountDoesNotExist() {

        wireMock.stubFor(
                get(urlEqualTo("/api/v1/accounts/111111111111"))
                        .willReturn(aResponse().withStatus(404))
        );

        assertThatThrownBy(() ->
                accountServiceClient.getAccount("111111111111")
        ).isInstanceOf(feign.FeignException.NotFound.class);
    }

    @Test
    void getAccount_shouldFailWhenAccountServiceIsUnavailable() {

        wireMock.stubFor(
                get(urlEqualTo("/api/v1/accounts/222222222222"))
                        .willReturn(aResponse().withStatus(503))
        );

        assertThatThrownBy(() ->
                accountServiceClient.getAccount("222222222222")
        ).isInstanceOf(feign.FeignException.class);
    }

    @Configuration(proxyBeanMethods = false)
    @EnableFeignClients(clients = AccountServiceClient.class)
    @ImportAutoConfiguration(FeignAutoConfiguration.class)
    static class TestConfig {
    }
}