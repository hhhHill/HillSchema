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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.dataagent.runtime.DataAgentBootstrap;
import io.agentscope.dataagent.runtime.session.SessionAgentManager;
import io.agentscope.dataagent.runtime.session.SessionEntry;
import io.agentscope.dataagent.runtime.session.SessionKind;
import io.agentscope.dataagent.web.audit.AgentActivityStore;
import io.agentscope.dataagent.web.catalog.AgentCatalogService;
import io.agentscope.dataagent.web.catalog.AgentDefinition;
import io.agentscope.dataagent.web.config.SecurityConfig;
import io.agentscope.dataagent.web.identity.IdentityLinkStore;
import io.agentscope.dataagent.web.share.AgentAccessGuard;
import io.agentscope.dataagent.web.share.AgentAclService.Tier;
import io.agentscope.dataagent.web.toolbus.ToolEventBus;
import io.agentscope.dataagent.web.usage.UsageStore;
import io.agentscope.harness.agent.gateway.MsgContext;
import io.agentscope.harness.agent.gateway.channel.InboundMessage;
import io.agentscope.harness.agent.gateway.channel.PeerKind;
import io.agentscope.harness.agent.gateway.channel.RouteResult;
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
        classes = ChatControllerTest.TestConfig.class,
        webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@TestPropertySource(
        properties = {
            "dataagent.jwt.secret=test-jwt-secret-must-be-at-least-32-characters-long",
            "spring.autoconfigure.exclude=org.springframework.boot.micrometer.metrics.autoconfigure.MetricsAutoConfiguration",
            "spring.sql.init.mode=never"
        })
class ChatControllerTest {

    @Autowired private ApplicationContext applicationContext;
    @Autowired private io.agentscope.dataagent.web.auth.JwtService jwtService;
    @Autowired private ChatUiChannel chatUiChannel;
    @Autowired private AgentCatalogService catalogService;
    @Autowired private AgentAccessGuard guard;
    @Autowired private SessionAgentManager sessionAgentManager;

    private WebTestClient webTestClient;

    @BeforeEach
    void setUpClient() {
        webTestClient = WebTestClient.bindToApplicationContext(applicationContext).build();
    }

    @Test
    void sendUsesConversationIdAsThreadId() {
        when(guard.require("user-1", "data-agent", Tier.RUN)).thenReturn(agent());
        when(catalogService.resolveGatewayAgentId("user-1", "data-agent"))
                .thenReturn("gateway-data-agent");
        when(chatUiChannel.previewRoute(any(InboundMessage.class)))
                .thenAnswer(invocation -> routeFor(invocation.getArgument(0)));
        when(chatUiChannel.dispatch(any(InboundMessage.class)))
                .thenReturn(Mono.just(Msg.builder().role(MsgRole.ASSISTANT).textContent("reply").build()));

        webTestClient
                .post()
                .uri("/api/agents/data-agent/chat/send")
                .header("Authorization", "Bearer " + token())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue("{\"message\":\"帮我分析一下\",\"sessionKey\":\"conv-1\"}")
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.reply")
                .isEqualTo("reply")
                .jsonPath("$.sessionKey")
                .isEqualTo("conv-1");

        ArgumentCaptor<InboundMessage> captor = ArgumentCaptor.forClass(InboundMessage.class);
        verify(chatUiChannel).dispatch(captor.capture());
        InboundMessage inbound = captor.getValue();

        assert inbound.accountId() == null;
        assert inbound.peer().kind() == PeerKind.THREAD;
        assert inbound.peer().id().equals("conv-1");
        assert inbound.parentPeer() != null;
        assert inbound.parentPeer().kind() == PeerKind.DIRECT;
        assert inbound.parentPeer().id().equals("user-1");
        assert inbound.senderId().equals("user-1");
        assert inbound.preferredAgentId().equals("gateway-data-agent");
        assert inbound.messages().size() == 1;
        assert inbound.messages().get(0).getTextContent().equals("帮我分析一下");
    }

    @Test
    void currentSessionFindsExistingThreadedConversation() {
        when(guard.require("user-1", "data-agent", Tier.RUN)).thenReturn(agent());
        when(catalogService.resolveGatewayAgentId("user-1", "data-agent"))
                .thenReturn("gateway-data-agent");
        when(chatUiChannel.previewRoute(any(InboundMessage.class)))
                .thenAnswer(invocation -> routeFor(invocation.getArgument(0)));
        when(sessionAgentManager.allSessions())
                .thenReturn(
                        List.of(
                                new SessionEntry(
                                        "stored-session",
                                        "gateway-data-agent",
                                        "session-1",
                                        "demo",
                                        SessionKind.MAIN,
                                        null,
                                        0,
                                        1L,
                                        2L,
                                        null,
                                        null,
                                        "chatui|r:user-1|t:conv-1|x:agentId=gateway-data-agent",
                                        "user-1")));

        webTestClient
                .get()
                .uri("/api/agents/data-agent/chat/session?sessionKey=conv-1")
                .header("Authorization", "Bearer " + token())
                .exchange()
                .expectStatus()
                .isOk()
                .expectBody()
                .jsonPath("$.sessionKey")
                .isEqualTo("conv-1")
                .jsonPath("$.exists")
                .isEqualTo(true);

        ArgumentCaptor<InboundMessage> captor = ArgumentCaptor.forClass(InboundMessage.class);
        verify(chatUiChannel).previewRoute(captor.capture());
        InboundMessage inbound = captor.getValue();

        assert inbound.accountId() == null;
        assert inbound.peer().kind() == PeerKind.THREAD;
        assert inbound.peer().id().equals("conv-1");
        assert inbound.parentPeer() != null;
        assert inbound.parentPeer().kind() == PeerKind.DIRECT;
        assert inbound.parentPeer().id().equals("user-1");
        assert inbound.senderId().equals("user-1");
        assert inbound.preferredAgentId().equals("gateway-data-agent");
        assert inbound.messages().isEmpty();
    }

    private RouteResult routeFor(InboundMessage inbound) {
        RouteResult route = Mockito.mock(RouteResult.class);
        MsgContext context =
                new MsgContext(
                        ChatUiChannel.CHANNEL_ID,
                        null,
                        inbound.parentPeer() != null ? inbound.parentPeer().id() : inbound.peer().id(),
                        inbound.peer().kind() == PeerKind.THREAD ? inbound.peer().id() : null,
                        null,
                        inbound.preferredAgentId() != null
                                ? java.util.Map.of("agentId", inbound.preferredAgentId())
                                : java.util.Map.of(),
                        inbound.senderId());
        when(route.context()).thenReturn(context);
        return route;
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
    @Import({SecurityConfig.class, io.agentscope.dataagent.web.auth.JwtService.class, ChatController.class})
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
            when(bootstrap.gateway()).thenReturn(gateway);
            when(gateway.sessionAgentManager()).thenReturn(sessionAgentManager());
            return bootstrap;
        }

        @Bean
        SessionAgentManager sessionAgentManager() {
            return Mockito.mock(SessionAgentManager.class);
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
    }
}
