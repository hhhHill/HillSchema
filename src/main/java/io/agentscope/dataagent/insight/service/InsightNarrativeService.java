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

import com.fasterxml.jackson.databind.ObjectMapper;
import io.agentscope.core.message.ContentBlock;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.model.ChatResponse;
import io.agentscope.core.model.Model;
import io.agentscope.dataagent.insight.domain.InsightCandidate;
import io.agentscope.dataagent.insight.domain.InsightKind;
import io.agentscope.dataagent.insight.domain.InsightNarrative;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/** Renders user-facing narrative text from deterministic insight candidates. */
@Service
public class InsightNarrativeService {

    private static final Logger log = LoggerFactory.getLogger(InsightNarrativeService.class);
    private static final Duration CALL_TIMEOUT = Duration.ofSeconds(30);

    private final Model model;
    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    public InsightNarrativeService(Optional<Model> modelOpt) {
        this.model = modelOpt.orElse(null);
    }

    public InsightNarrative render(InsightCandidate candidate) {
        if (model == null) {
            return fallback(candidate);
        }
        try {
            String response = callModel(candidate);
            NarrativePayload payload = parsePayload(response);
            if (payload == null
                    || isBlank(payload.title())
                    || isBlank(payload.summary())
                    || isBlank(payload.conclusion())) {
                return fallback(candidate);
            }
            return new InsightNarrative(
                    payload.title(),
                    payload.summary(),
                    payload.conclusion(),
                    blankToFallback(payload.evidenceExplanation(), fallback(candidate).evidenceExplanation()),
                    payload.followUpQuestions());
        } catch (Exception e) {
            log.warn("Insight narrative fallback: {}", e.getMessage());
            return fallback(candidate);
        }
    }

    private String callModel(InsightCandidate candidate) {
        String prompt =
                """
                你是电商数据洞察文案助手。根据给定 JSON 生成严格 JSON：
                {
                  "title": "...",
                  "summary": "...",
                  "conclusion": "...",
                  "evidenceExplanation": "...",
                  "followUpQuestions": ["...", "..."]
                }
                只输出 JSON，不要输出 Markdown。

                输入：
                %s
                """
                        .formatted(toPromptJson(candidate));
        Msg message =
                Msg.builder()
                        .role(MsgRole.USER)
                        .content(TextBlock.builder().text(prompt).build())
                        .build();
        List<ChatResponse> responses =
                model.stream(List.of(message), null, null).collectList().block(CALL_TIMEOUT);
        if (responses == null || responses.isEmpty()) {
            throw new IllegalStateException("model returned no response");
        }
        StringBuilder sb = new StringBuilder();
        for (ChatResponse response : responses) {
            if (response == null || response.getContent() == null) {
                continue;
            }
            for (ContentBlock block : response.getContent()) {
                if (block instanceof TextBlock textBlock && textBlock.getText() != null) {
                    sb.append(textBlock.getText());
                }
            }
        }
        String raw = sb.toString().trim();
        if (raw.isBlank()) {
            throw new IllegalStateException("model returned empty content");
        }
        return raw;
    }

    private NarrativePayload parsePayload(String raw) throws Exception {
        String stripped = raw.trim();
        if (stripped.startsWith("```")) {
            int firstNewline = stripped.indexOf('\n');
            if (firstNewline >= 0) {
                stripped = stripped.substring(firstNewline + 1);
            }
            int closing = stripped.lastIndexOf("```");
            if (closing >= 0) {
                stripped = stripped.substring(0, closing);
            }
        }
        int firstBrace = stripped.indexOf('{');
        int lastBrace = stripped.lastIndexOf('}');
        if (firstBrace >= 0 && lastBrace > firstBrace) {
            stripped = stripped.substring(firstBrace, lastBrace + 1);
        }
        return objectMapper.readValue(stripped, NarrativePayload.class);
    }

    private String toPromptJson(InsightCandidate candidate) {
        try {
            return objectMapper.writeValueAsString(candidate);
        } catch (Exception e) {
            throw new IllegalStateException("failed to serialize candidate", e);
        }
    }

    private static InsightNarrative fallback(InsightCandidate candidate) {
        double current = candidate.currentValue();
        double baseline = candidate.baselineValue();
        String metricLabel =
                candidate.metricLabel() == null || candidate.metricLabel().isBlank()
                        ? candidate.metricKey()
                        : candidate.metricLabel();
        String direction =
                current >= baseline ? "上升" : "下降";
        String title =
                switch (candidate.kind()) {
                    case ANOMALY -> metricLabel + "明显" + direction;
                    case TREND -> metricLabel + "持续" + direction;
                    case ATTRIBUTION ->
                            (candidate.dimensionValue() == null ? "关键维度" : candidate.dimensionValue())
                                    + " 可能是主要影响因素";
                    case SUMMARY -> "最近一个窗口的业务概览已生成";
                };
        String summary =
                switch (candidate.kind()) {
                    case SUMMARY ->
                            "最近 24 小时与上一窗口相比，"
                                    + metricLabel
                                    + "为 "
                                    + number(current)
                                    + "，上一窗口为 "
                                    + number(baseline)
                                    + "。";
                    case ATTRIBUTION ->
                            "维度 "
                                    + candidate.dimensionName()
                                    + "="
                                    + candidate.dimensionValue()
                                    + " 的变化最明显。";
                    default ->
                            metricLabel
                                    + "较上一窗口"
                                    + direction
                                    + "，"
                                    + "从 "
                                    + number(baseline)
                                    + " 变化到 "
                                    + number(current)
                                    + "。";
                };
        String conclusion =
                switch (candidate.kind()) {
                    case ATTRIBUTION ->
                            "当前判断 "
                                    + candidate.dimensionName()
                                    + "="
                                    + candidate.dimensionValue()
                                    + " 对本轮变化影响最大。";
                    case SUMMARY ->
                            "系统已生成本轮基础概览，可继续查看异常和归因问题。";
                    default ->
                            "本轮 "
                                    + metricLabel
                                    + "从 "
                                    + number(baseline)
                                    + (current >= baseline ? " 上升到 " : " 下降到 ")
                                    + number(current)
                                    + "，建议继续查看关联维度与退款情况。";
                };
        String evidenceExplanation =
                candidate.evidence().isEmpty()
                        ? summary
                        : candidate.evidence().stream()
                                .map(
                                        entry ->
                                                entry.label()
                                                        + " "
                                                        + entry.valueText()
                                                        + "（"
                                                        + entry.detailText()
                                                        + "）")
                                .reduce((left, right) -> left + "；" + right)
                                .orElse(summary);
        return new InsightNarrative(
                title,
                summary,
                conclusion,
                evidenceExplanation,
                defaultFollowUps(candidate));
    }

    private static List<String> defaultFollowUps(InsightCandidate candidate) {
        if (candidate.kind() == InsightKind.ATTRIBUTION) {
            return List.of("这个维度最近是否有投放或运营变化？", "是否还影响了退款率或转化率？");
        }
        return List.of("造成这个变化的主要维度是什么？", "这个问题现在还在持续吗？");
    }

    private static String blankToFallback(String value, String fallback) {
        return isBlank(value) ? fallback : value;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static String number(double value) {
        if (Math.abs(value - Math.rint(value)) < 0.00001d) {
            return String.valueOf((long) value);
        }
        return String.format(Locale.ROOT, "%.2f", value);
    }

    private record NarrativePayload(
            String title,
            String summary,
            String conclusion,
            String evidenceExplanation,
            List<String> followUpQuestions) {}
}
