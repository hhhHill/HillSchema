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
package io.agentscope.dataagent.insight.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;

import io.agentscope.core.message.TextBlock;
import io.agentscope.core.model.ChatResponse;
import io.agentscope.core.model.Model;
import io.agentscope.dataagent.insight.domain.InsightCandidate;
import io.agentscope.dataagent.insight.domain.InsightKind;
import io.agentscope.dataagent.insight.domain.InsightNarrative;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import reactor.core.publisher.Flux;

class InsightNarrativeServiceTest {

    @Test
    void fallsBackToTemplateWhenModelIsUnavailable() {
        InsightNarrativeService service = new InsightNarrativeService(Optional.empty());

        InsightNarrative narrative = service.render(dropCandidate());

        assertThat(narrative.title()).contains("订单量");
        assertThat(narrative.summary()).contains("下降");
        assertThat(narrative.conclusion()).contains("3");
        assertThat(narrative.followUpQuestions()).isNotEmpty();
    }

    @Test
    void parsesModelJsonWhenModelReturnsStructuredNarrative() {
        Model model = Mockito.mock(Model.class);
        Mockito.when(model.stream(anyList(), any(), any()))
                .thenReturn(
                        Flux.just(
                                new ChatResponse(
                                        "narrative-1",
                                        List.of(
                                                TextBlock.builder()
                                                        .text(
                                                                """
                                                                {
                                                                  "title": "广告渠道订单流失明显",
                                                                  "summary": "近 24 小时订单量显著低于上一窗口。",
                                                                  "conclusion": "订单量从 12 单降到 3 单，广告渠道拖累最大。",
                                                                  "evidenceExplanation": "广告渠道订单从 6 单降到 2 单。",
                                                                  "followUpQuestions": ["广告投放是否变化？", "是否影响退款率？"]
                                                                }
                                                                """)
                                                        .build()),
                                        null,
                                        Map.of(),
                                        "stop")));

        InsightNarrativeService service = new InsightNarrativeService(Optional.of(model));

        InsightNarrative narrative = service.render(dropCandidate());

        assertThat(narrative.title()).isEqualTo("广告渠道订单流失明显");
        assertThat(narrative.followUpQuestions()).containsExactly("广告投放是否变化？", "是否影响退款率？");
    }

    private static InsightCandidate dropCandidate() {
        return new InsightCandidate(
                "orders-demo",
                InsightKind.ANOMALY,
                "order_count",
                "订单量",
                "orders-demo:anomaly:order-count",
                Instant.parse("2026-06-28T10:00:00Z"),
                Instant.parse("2026-06-27T10:00:00Z"),
                Instant.parse("2026-06-28T10:00:00Z"),
                3.0d,
                12.0d,
                null,
                null,
                List.of(
                        new InsightCandidate.EvidenceEntry(
                                "currentOrders", "当前订单量", "3", "最近 24 小时订单量"),
                        new InsightCandidate.EvidenceEntry(
                                "previousOrders", "上一窗口订单量", "12", "前一窗口订单量")),
                List.of("广告渠道跌幅最大"));
    }
}
