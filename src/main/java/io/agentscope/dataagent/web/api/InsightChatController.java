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

import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.dataagent.insight.service.InsightScopedChatPromptBuilder;
import io.agentscope.dataagent.web.catalog.AgentCatalogService;
import io.agentscope.dataagent.web.share.AgentAccessGuard;
import io.agentscope.dataagent.web.share.AgentAclService.Tier;
import io.agentscope.harness.agent.gateway.channel.InboundMessage;
import io.agentscope.harness.agent.gateway.channel.Peer;
import io.agentscope.harness.agent.gateway.channel.chatui.ChatUiChannel;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

/** Chat entrypoint for following up on a specific insight detail with explicit scoped context. */
@RestController
@RequestMapping("/api/agents/{agentId}/insights/{itemId}/chat")
public class InsightChatController {

    private final ChatUiChannel chatUiChannel;
    private final AgentCatalogService catalogService;
    private final AgentAccessGuard guard;
    private final InsightScopedChatPromptBuilder promptBuilder;

    public InsightChatController(
            ChatUiChannel chatUiChannel,
            AgentCatalogService catalogService,
            AgentAccessGuard guard,
            InsightScopedChatPromptBuilder promptBuilder) {
        this.chatUiChannel = chatUiChannel;
        this.catalogService = catalogService;
        this.guard = guard;
        this.promptBuilder = promptBuilder;
    }

    @PostMapping("/send")
    public Mono<ChatController.ChatResponse> send(
            @PathVariable String agentId,
            @PathVariable long itemId,
            @RequestBody ChatController.ChatRequest req,
            Authentication auth) {
        String userId = (String) auth.getPrincipal();
        guard.require(userId, agentId, Tier.RUN);

        String message = normalizeMessage(req.message());
        String conversationId = normalizedConversationId(req.sessionKey());
        if (conversationId == null) {
            conversationId = UUID.randomUUID().toString();
        }

        final String resolvedConversationId = conversationId;
        final String scopedContext;
        try {
            scopedContext = promptBuilder.buildPrompt(itemId, message);
        } catch (NoSuchElementException ex) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, ex.getMessage(), ex);
        }

        String gatewayAgentId = catalogService.resolveGatewayAgentId(userId, agentId);
        Msg systemMsg = Msg.builder().role(MsgRole.SYSTEM).textContent(scopedContext).build();
        Msg userMsg = Msg.builder().role(MsgRole.USER).textContent(message).build();

        InboundMessage inbound =
                InboundMessage.builder(
                                ChatUiChannel.CHANNEL_ID,
                                Peer.direct(userId),
                                List.of(systemMsg, userMsg))
                        .preferredAgentId(gatewayAgentId)
                        .accountId(scopedConversationId(itemId, resolvedConversationId))
                        .build();

        return chatUiChannel
                .dispatch(inbound)
                .map(
                        reply ->
                                new ChatController.ChatResponse(
                                        replyText(reply), resolvedConversationId));
    }

    static String scopedConversationId(long itemId, String conversationId) {
        return "insight:" + itemId + ":" + conversationId;
    }

    private static String normalizeMessage(String message) {
        if (message == null || message.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "message is required");
        }
        return message.trim();
    }

    private static String normalizedConversationId(String key) {
        return (key != null && !key.isBlank()) ? key.trim() : null;
    }

    private static String replyText(Msg reply) {
        return reply != null && reply.getTextContent() != null ? reply.getTextContent() : "";
    }
}
