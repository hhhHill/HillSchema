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

import io.agentscope.dataagent.insight.service.InsightFeedService;
import io.agentscope.dataagent.web.share.AgentAccessGuard;
import io.agentscope.dataagent.web.share.AgentAclService.Tier;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

/** Feed/detail API for persisted insights shown on the insight-first homepage. */
@RestController
@RequestMapping("/api/agents/{agentId}/insights")
public class InsightFeedController {

    private final InsightFeedService insightFeedService;
    private final AgentAccessGuard guard;

    public InsightFeedController(InsightFeedService insightFeedService, AgentAccessGuard guard) {
        this.insightFeedService = insightFeedService;
        this.guard = guard;
    }

    @GetMapping
    public Mono<java.util.List<InsightFeedService.FeedItem>> feed(
            @PathVariable String agentId,
            @RequestParam(defaultValue = "50") int limit,
            Authentication auth) {
        String userId = (String) auth.getPrincipal();
        return Mono.fromCallable(
                () -> {
                    guard.require(userId, agentId, Tier.RUN);
                    return insightFeedService.listFeed(limit);
                });
    }

    @GetMapping("/{itemId}")
    public Mono<InsightFeedService.InsightDetail> detail(
            @PathVariable String agentId, @PathVariable long itemId, Authentication auth) {
        String userId = (String) auth.getPrincipal();
        return Mono.fromCallable(
                () -> {
                    guard.require(userId, agentId, Tier.RUN);
                    return insightFeedService
                            .getDetail(itemId)
                            .orElseThrow(
                                    () ->
                                            new ResponseStatusException(
                                                    HttpStatus.NOT_FOUND,
                                                    "Insight not found: " + itemId));
                });
    }
}
