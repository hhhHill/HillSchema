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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class InsightScopedChatPromptBuilderTest {

    @Test
    void buildsPromptFromInsightDetailContext() {
        InsightFeedService feedService = mock(InsightFeedService.class);
        when(feedService.getDetail(303L))
                .thenReturn(
                        Optional.of(
                                new InsightFeedService.InsightDetail(
                                        303L,
                                        "shop-demo",
                                        "ANOMALY",
                                        "NEW",
                                        "退款率突然抬升",
                                        "最近一个窗口退款率高于上一窗口。",
                                        "售后问题仍在持续，优先检查商品与履约链路。",
                                        "当前退款率 12%，上一窗口 4%。",
                                        Instant.parse("2026-06-30T09:30:00Z"),
                                        Instant.parse("2026-06-30T09:31:00Z"),
                                        Instant.parse("2026-06-29T09:30:00Z"),
                                        Instant.parse("2026-06-30T09:30:00Z"),
                                        "refund_rate",
                                        "退款率",
                                        12.0d,
                                        4.0d,
                                        "channel",
                                        "直播",
                                        List.of("问题现在是否还在持续？", "哪个维度影响最大？"),
                                        List.of(
                                                new InsightFeedService.InsightEvidence(
                                                        "refundRate",
                                                        "当前退款率",
                                                        "12%",
                                                        "最近 24 小时退款订单占比",
                                                        "{\"metric\":\"refund_rate\",\"value\":12}")))));

        InsightScopedChatPromptBuilder builder = new InsightScopedChatPromptBuilder(feedService);

        String prompt = builder.buildPrompt(303L, "下一步应该查什么？");

        assertThat(prompt).contains("当前问题上下文");
        assertThat(prompt).contains("退款率突然抬升");
        assertThat(prompt).contains("售后问题仍在持续");
        assertThat(prompt).contains("当前退款率");
        assertThat(prompt).contains("问题现在是否还在持续");
        assertThat(prompt).contains("用户当前追问");
        assertThat(prompt).contains("下一步应该查什么？");
    }
}
