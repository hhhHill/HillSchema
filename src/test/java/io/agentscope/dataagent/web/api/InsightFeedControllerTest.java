/*
 * Copyright 2024-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.agentscope.dataagent.web.api;

import static org.mockito.Mockito.when;

import io.agentscope.dataagent.insight.service.InsightFeedService;
import io.agentscope.dataagent.web.auth.JwtService;
import io.agentscope.dataagent.web.catalog.AgentDefinition;
import io.agentscope.dataagent.web.config.SecurityConfig;
import io.agentscope.dataagent.web.share.AgentAccessGuard;
import io.agentscope.dataagent.web.share.AgentAclService.Tier;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.mockito.Mockito;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

@SpringBootTest(
        classes = InsightFeedControllerTest.TestConfig.class,
        webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@TestPropertySource(
        properties = {
            "dataagent.jwt.secret=test-jwt-secret-must-be-at-least-32-characters-long",
            "spring.autoconfigure.exclude=org.springframework.boot.micrometer.metrics.autoconfigure.MetricsAutoConfiguration",
            "spring.sql.init.mode=never"
        })
class InsightFeedControllerTest {

    @Autowired private ApplicationContext applicationContext;
    @Autowired private JwtService jwtService;
    @Autowired private InsightFeedService insightFeedService;
    @Autowired private AgentAccessGuard guard;

    private WebTestClient webTestClient;

    @org.junit.jupiter.api.BeforeEach
    void setUpClient() {
        webTestClient = WebTestClient.bindToApplicationContext(applicationContext).build();
    }

    @Test
    void listsFeedForAuthorizedUsers() {
        when(guard.require("user-1", "data-agent", Tier.RUN)).thenReturn(agent());
        when(insightFeedService.listFeed(5))
                .thenReturn(
                        List.of(
                                new InsightFeedService.FeedItem(
                                        202L,
                                        "shop-demo",
                                        "ANOMALY",
                                        "NEW",
                                        "退款率突然抬升",
                                        "最近一个窗口退款率高于上一窗口。",
                                        "当前退款率 12%，上一窗口 4%。",
                                        Instant.parse("2026-06-30T09:30:00Z"),
                                        Instant.parse("2026-06-30T09:31:00Z"),
                                        "refund_rate",
                                        "退款率",
                                        null,
                                        null)));

        webTestClient
                .get()
                .uri("/api/agents/data-agent/insights?limit=5")
                .header("Authorization", "Bearer " + token())
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$[0].id")
                .isEqualTo(202)
                .jsonPath("$[0].title")
                .isEqualTo("退款率突然抬升")
                .jsonPath("$[0].status")
                .isEqualTo("NEW");
    }

    @Test
    void returnsNotFoundWhenTheInsightDetailDoesNotExist() {
        when(guard.require("user-1", "data-agent", Tier.RUN)).thenReturn(agent());
        when(insightFeedService.getDetail(999L)).thenReturn(Optional.empty());

        webTestClient
                .get()
                .uri("/api/agents/data-agent/insights/999")
                .header("Authorization", "Bearer " + token())
                .exchange()
                .expectStatus()
                .isNotFound();
    }

    private String token() {
        return jwtService.generate("user-1", "User 1", List.of("user"));
    }

    private AgentDefinition agent() {
        return new AgentDefinition(
                "data-agent",
                "DataAgent",
                "Insight-first data operator",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                "AI",
                null,
                null,
                null,
                null,
                AgentDefinition.SCOPE_GLOBAL,
                null,
                0L,
                0L,
                null,
                null,
                null,
                null,
                null,
                null,
                "RUN");
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @Import({SecurityConfig.class, JwtService.class, InsightFeedController.class})
    static class TestConfig {

        @Bean
        InsightFeedService insightFeedService() {
            return Mockito.mock(InsightFeedService.class);
        }

        @Bean
        AgentAccessGuard agentAccessGuard() {
            return Mockito.mock(AgentAccessGuard.class);
        }
    }
}
