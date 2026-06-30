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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.dataagent.insight.service.InsightScopedChatPromptBuilder;
import io.agentscope.dataagent.runtime.DataAgentBootstrap;
import io.agentscope.dataagent.runtime.session.SessionAgentManager;
import io.agentscope.dataagent.web.audit.AgentActivityStore;
import io.agentscope.dataagent.web.catalog.AgentCatalogService;
import io.agentscope.dataagent.web.catalog.AgentDefinition;
import io.agentscope.dataagent.web.config.SecurityConfig;
import io.agentscope.dataagent.web.identity.IdentityLinkStore;
import io.agentscope.dataagent.web.share.AgentAccessGuard;
import io.agentscope.dataagent.web.share.AgentAclService.Tier;
import io.agentscope.dataagent.web.toolbus.ToolEventBus;
import io.agentscope.dataagent.web.usage.UsageStore;
import io.agentscope.harness.agent.gateway.channel.InboundMessage;
import io.agentscope.harness.agent.gateway.channel.chatui.ChatUiChannel;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

@SpringBootTest(
        classes = InsightChatControllerTest.TestConfig.class,
        webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@TestPropertySource(
        properties = {
            "dataagent.jwt.secret=test-jwt-secret-must-be-at-least-32-characters-long",
            "spring.autoconfigure.exclude=org.springframework.boot.micrometer.metrics.autoconfigure.MetricsAutoConfiguration",
            "spring.sql.init.mode=never"
        })
class InsightChatControllerTest {

    @Autowired private ApplicationContext applicationContext;
    @Autowired private io.agentscope.dataagent.web.auth.JwtService jwtService;
    @Autowired private ChatUiChannel chatUiChannel;
    @Autowired private AgentCatalogService catalogService;
    @Autowired private AgentAccessGuard guard;
    @Autowired private InsightScopedChatPromptBuilder promptBuilder;

    private WebTestClient webTestClient;

    @BeforeEach
    void setUpClient() {
        webTestClient = WebTestClient.bindToApplicationContext(applicationContext).build();
    }

    @Test
    void sendInjectsInsightContextAndUsesScopedConversationNamespace() {
        when(guard.require("user-1", "data-agent", Tier.RUN)).thenReturn(agent());
        when(catalogService.resolveGatewayAgentId("user-1", "data-agent")).thenReturn("gateway-data-agent");
        when(promptBuilder.buildPrompt(303L, "为什么会发生？")).thenReturn("SCOPED CONTEXT BLOCK");
        when(chatUiChannel.dispatch(any(InboundMessage.class)))
                .thenReturn(Mono.just(Msg.builder().role(MsgRole.ASSISTANT).textContent("scoped reply").build()));

        webTestClient
                .post()
                .uri("/api/agents/data-agent/insights/303/chat/send")
                .header("Authorization", "Bearer " + token())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"message\":\"为什么会发生？\",\"sessionKey\":\"conv-1\"}")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.reply")
                .isEqualTo("scoped reply")
                .jsonPath("$.sessionKey")
                .isEqualTo("conv-1");

        ArgumentCaptor<InboundMessage> captor = ArgumentCaptor.forClass(InboundMessage.class);
        verify(chatUiChannel).dispatch(captor.capture());
        InboundMessage inbound = captor.getValue();

        assert inbound.accountId().equals("insight:303:conv-1");
        assert inbound.preferredAgentId().equals("gateway-data-agent");
        assert inbound.messages().size() == 2;
        assert inbound.messages().get(0).getRole() == MsgRole.SYSTEM;
        assert inbound.messages().get(0).getTextContent().equals("SCOPED CONTEXT BLOCK");
        assert inbound.messages().get(1).getRole() == MsgRole.USER;
        assert inbound.messages().get(1).getTextContent().equals("为什么会发生？");
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
    @Import({SecurityConfig.class, io.agentscope.dataagent.web.auth.JwtService.class, InsightChatController.class})
    static class TestConfig {

        @Bean
        ChatUiChannel chatUiChannel() {
            return Mockito.mock(ChatUiChannel.class);
        }

        @Bean
        DataAgentBootstrap dataAgentBootstrap() {
            DataAgentBootstrap bootstrap = Mockito.mock(DataAgentBootstrap.class);
            io.agentscope.dataagent.runtime.gateway.HarnessGateway gateway =
                    Mockito.mock(io.agentscope.dataagent.runtime.gateway.HarnessGateway.class);
            SessionAgentManager sessionAgentManager = Mockito.mock(SessionAgentManager.class);
            when(bootstrap.gateway()).thenReturn(gateway);
            when(gateway.sessionAgentManager()).thenReturn(sessionAgentManager);
            return bootstrap;
        }

        @Bean
        AgentCatalogService agentCatalogService() {
            return Mockito.mock(AgentCatalogService.class);
        }

        @Bean
        IdentityLinkStore identityLinkStore() {
            return Mockito.mock(IdentityLinkStore.class);
        }

        @Bean
        UsageStore usageStore() {
            return Mockito.mock(UsageStore.class);
        }

        @Bean
        ToolEventBus toolEventBus() {
            return Mockito.mock(ToolEventBus.class);
        }

        @Bean
        AgentAccessGuard agentAccessGuard() {
            return Mockito.mock(AgentAccessGuard.class);
        }

        @Bean
        AgentActivityStore agentActivityStore() {
            return Mockito.mock(AgentActivityStore.class);
        }

        @Bean
        InsightScopedChatPromptBuilder insightScopedChatPromptBuilder() {
            return Mockito.mock(InsightScopedChatPromptBuilder.class);
        }
    }
}
