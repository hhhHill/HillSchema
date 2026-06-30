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

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.NoSuchElementException;
import org.springframework.stereotype.Service;

/** Builds an explicit issue-bound context block for insight detail follow-up chat. */
@Service
public class InsightScopedChatPromptBuilder {

    private static final DateTimeFormatter TIME_FORMATTER =
            DateTimeFormatter.ISO_OFFSET_DATE_TIME.withZone(ZoneOffset.UTC);

    private final InsightFeedService feedService;

    public InsightScopedChatPromptBuilder(InsightFeedService feedService) {
        this.feedService = feedService;
    }

    public String buildPrompt(long itemId, String userQuestion) {
        InsightFeedService.InsightDetail detail =
                feedService
                        .getDetail(itemId)
                        .orElseThrow(
                                () ->
                                        new NoSuchElementException(
                                                "Insight item not found: " + itemId));

        StringBuilder prompt = new StringBuilder(1024);
        prompt.append("当前问题上下文\n");
        prompt.append("请严格围绕当前问题、证据快照和时间窗口回答，不要退化成泛化数据库问答。\n\n");
        prompt.append("问题标题：").append(orDash(detail.title())).append('\n');
        prompt.append("问题摘要：").append(orDash(detail.summary())).append('\n');
        prompt.append("问题结论：").append(orDash(detail.conclusion())).append('\n');
        prompt.append("证据摘要：").append(orDash(detail.evidenceSummary())).append('\n');
        prompt.append("所属数据源：").append(orDash(detail.sourceId())).append('\n');
        prompt.append("问题类型：").append(orDash(detail.kind())).append('\n');
        prompt.append("问题状态：").append(orDash(detail.status())).append('\n');
        prompt.append("观察时间：").append(format(detail.observedAt())).append('\n');
        prompt.append("创建时间：").append(format(detail.createdAt())).append('\n');
        prompt.append("时间窗口：")
                .append(format(detail.windowStart()))
                .append(" ~ ")
                .append(format(detail.windowEnd()))
                .append('\n');
        prompt.append("指标：")
                .append(orDash(detail.metricLabel()))
                .append(" (")
                .append(orDash(detail.metricKey()))
                .append(")")
                .append('\n');
        prompt.append("当前值：").append(detail.currentValue()).append('\n');
        prompt.append("基线值：").append(detail.baselineValue()).append('\n');
        prompt.append("关键维度：")
                .append(orDash(detail.dimensionName()))
                .append(" = ")
                .append(orDash(detail.dimensionValue()))
                .append('\n');

        prompt.append("\n证据快照\n");
        if (detail.evidence().isEmpty()) {
            prompt.append("- 无证据快照\n");
        } else {
            for (InsightFeedService.InsightEvidence evidence : detail.evidence()) {
                prompt.append("- ")
                        .append(orDash(evidence.label()))
                        .append("：")
                        .append(orDash(evidence.valueText()))
                        .append('\n');
                if (hasText(evidence.detailText())) {
                    prompt.append("  说明：").append(evidence.detailText().trim()).append('\n');
                }
            }
        }

        prompt.append("\n建议追问\n");
        if (detail.followUpQuestions().isEmpty()) {
            prompt.append("- 无\n");
        } else {
            for (String question : detail.followUpQuestions()) {
                prompt.append("- ").append(question).append('\n');
            }
        }

        prompt.append("\n用户当前追问\n");
        prompt.append(orDash(userQuestion)).append('\n');
        prompt.append("\n回答要求\n");
        prompt.append("1. 先直接回答用户追问。\n");
        prompt.append("2. 只引用当前问题上下文中的事实，不要虚构额外数据。\n");
        prompt.append("3. 如果证据不足，明确指出缺口，并给出下一步排查建议。\n");
        return prompt.toString();
    }

    private static String format(Instant instant) {
        return instant == null ? "-" : TIME_FORMATTER.format(instant);
    }

    private static String orDash(String value) {
        return hasText(value) ? value.trim() : "-";
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
