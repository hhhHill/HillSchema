# HillSchema Frontend Tonality Refresh Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 把 HillSchema 核心用户面的前端落成统一的“经营洞察作战面板”，让首页、详情、AI 辅助、登录页、主壳层和 Workspace 在同一套冷白战斗台语言下工作。

**Architecture:** 这次实现保持“前端主题层 + 页面组件层 + 一个最小后端 feed 契约补丁”三层结构。前端通过全局 CSS 变量和少量新组件收束样式与信息层级，后端只补首页排序与业务影响表达所需的洞察 feed 元数据，不重写洞察生成链路和详情接口。

**Tech Stack:** React 18, TypeScript, React Router 6, Vite, Vitest, Spring Boot, JPA, WebTestClient

---

## Scope Check

这份 spec 只覆盖一个连续子系统：`核心用户前端 + insight feed 最小契约扩展`。不需要拆成多份 plan。

明确不进入本计划的范围：

- `frontend/src/pages/admin/**`
- `frontend/src/pages/configure/**`
- 后端洞察检测、调度、持久化主链路
- 新增第三方 UI 组件库

## File Structure

- Create: `frontend/src/styles/app.css`
  - 全局样式入口，只负责串联 `theme.css`、`shell.css`、`insights.css`、`auth.css`、`workspace.css`
- Create: `frontend/src/styles/theme.css`
  - 颜色、圆角、间距、阴影、排版、动效、断点 token
- Create: `frontend/src/styles/shell.css`
  - `AppShell`、`SessionsSidebar`、`BackToChatHeader`、`ChatHeader` 的主壳层样式
- Create: `frontend/src/styles/insights.css`
  - 首页 top strip、精选卡、作战列表、详情布局、AI 面板、加载态与错误态
- Create: `frontend/src/styles/auth.css`
  - 登录页版式与表单层级
- Create: `frontend/src/styles/workspace.css`
  - Workspace 轻量跟随样式和信息摘要卡
- Create: `frontend/src/components/insights/insightPresentation.ts`
  - 洞察排序、精选区抽取、top strip 汇总、详情影响文案生成
- Create: `frontend/src/components/insights/InsightSummaryStrip.tsx`
  - 首页顶部经营态势条
- Create: `frontend/src/components/insights/InsightFeaturedRail.tsx`
  - 首页精选洞察区
- Create: `frontend/src/components/__tests__/SessionsSidebar.test.tsx`
  - 壳层导航与洞察优先辅助文案回归测试
- Create: `frontend/src/pages/__tests__/LoginPage.test.tsx`
  - 登录页中文文案、成功跳转、失败提示回归测试
- Create: `src/main/java/io/agentscope/dataagent/insight/service/InsightImpactRanker.java`
  - 计算 `impactScore`、`impactBand`、`impactSummary`、`deltaValue`、`deltaRatio`
- Modify: `frontend/src/main.tsx`
- Modify: `frontend/src/api/insights.ts`
- Modify: `frontend/src/components/AppShell.tsx`
- Modify: `frontend/src/components/SessionsSidebar.tsx`
- Modify: `frontend/src/components/BackToChatHeader.tsx`
- Modify: `frontend/src/components/ChatHeader.tsx`
- Modify: `frontend/src/components/ChatPanel.tsx`
- Modify: `frontend/src/components/insights/InsightCard.tsx`
- Modify: `frontend/src/components/insights/InsightFeedList.tsx`
- Modify: `frontend/src/components/insights/InsightDetailPanel.tsx`
- Modify: `frontend/src/components/insights/InsightScopedChatPanel.tsx`
- Modify: `frontend/src/pages/InsightsPage.tsx`
- Modify: `frontend/src/pages/LoginPage.tsx`
- Modify: `frontend/src/pages/WorkspacePage.tsx`
- Modify: `frontend/src/pages/__tests__/InsightsPage.test.tsx`
- Modify: `frontend/src/pages/__tests__/WorkspacePage.test.tsx`
- Modify: `frontend/src/components/__tests__/InsightScopedChatPanel.test.tsx`
- Modify: `src/main/java/io/agentscope/dataagent/insight/service/InsightFeedService.java`
- Modify: `src/test/java/io/agentscope/dataagent/insight/service/InsightFeedServiceTest.java`
- Modify: `src/test/java/io/agentscope/dataagent/web/api/InsightFeedControllerTest.java`

## Task 1: 补齐首页业务影响契约

**Files:**
- Create: `src/main/java/io/agentscope/dataagent/insight/service/InsightImpactRanker.java`
- Modify: `src/main/java/io/agentscope/dataagent/insight/service/InsightFeedService.java`
- Modify: `src/test/java/io/agentscope/dataagent/insight/service/InsightFeedServiceTest.java`
- Modify: `src/test/java/io/agentscope/dataagent/web/api/InsightFeedControllerTest.java`
- Modify: `frontend/src/api/insights.ts`

- [ ] **Step 1: 先写后端失败测试，锁定 feed 元数据契约**

```java
// src/test/java/io/agentscope/dataagent/insight/service/InsightFeedServiceTest.java
@Test
void exposesImpactMetadataForHomepageRanking() {
    InsightBatchEntity batch = saveBatch();
    InsightItemEntity item =
            saveItem(
                    batch,
                    202L,
                    "退款率突然抬升",
                    "最近一个窗口退款率高于上一窗口。",
                    "当前退款率 12%，上一窗口 4%。",
                    InsightStatus.NEW,
                    Instant.parse("2026-06-30T09:30:00Z"),
                    Instant.parse("2026-06-30T09:31:00Z"),
                    "refund_rate",
                    "退款率",
                    null,
                    null);

    InsightFeedService.FeedItem feedItem = insightFeedService.listFeed(10).get(0);

    assertThat(feedItem.deltaValue()).isEqualTo(8d);
    assertThat(feedItem.deltaRatio()).isEqualTo(2d);
    assertThat(feedItem.impactBand()).isEqualTo("HIGH");
    assertThat(feedItem.impactScore()).isGreaterThan(0.75d);
    assertThat(feedItem.impactSummary()).contains("退款率").contains("较基线");
}

// src/test/java/io/agentscope/dataagent/web/api/InsightFeedControllerTest.java
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
                                null,
                                8d,
                                2d,
                                0.86d,
                                "HIGH",
                                "退款率较基线上升 200%")));

webTestClient
        .get()
        .uri("/api/agents/data-agent/insights?limit=5")
        .header("Authorization", "Bearer " + token())
        .exchange()
        .expectStatus()
        .isOk()
        .expectBody()
        .jsonPath("$[0].impactBand")
        .isEqualTo("HIGH")
        .jsonPath("$[0].impactSummary")
        .isEqualTo("退款率较基线上升 200%");
```

- [ ] **Step 2: 运行后端测试，确认现在因为 feed record 缺字段而失败**

Run: `mvn -Dtest=InsightFeedServiceTest,InsightFeedControllerTest test`

Expected: FAIL，编译或断言报错，原因是 `FeedItem` 还没有 `deltaValue`、`deltaRatio`、`impactScore`、`impactBand`、`impactSummary`。

- [ ] **Step 3: 写最小实现，集中在一个 ranker 里补首页需要的影响信息**

```java
// src/main/java/io/agentscope/dataagent/insight/service/InsightImpactRanker.java
package io.agentscope.dataagent.insight.service;

import java.util.Locale;
import org.springframework.stereotype.Service;

@Service
public class InsightImpactRanker {

    public ImpactMetadata summarize(
            String kind,
            String status,
            String metricLabel,
            String metricKey,
            double currentValue,
            double baselineValue) {
        String label = (metricLabel == null || metricLabel.isBlank()) ? metricKey : metricLabel;
        double deltaValue = currentValue - baselineValue;
        double denominator = Math.max(Math.abs(baselineValue), 1d);
        double deltaRatio = deltaValue / denominator;

        double baseScore =
                switch (kind) {
                    case "ANOMALY" -> 0.58d;
                    case "ATTRIBUTION" -> 0.52d;
                    case "TREND" -> 0.48d;
                    default -> 0.38d;
                };
        double statusBoost =
                switch (status) {
                    case "NEW" -> 0.18d;
                    case "CONTINUING" -> 0.12d;
                    case "ACKNOWLEDGED" -> 0.04d;
                    default -> 0d;
                };
        double magnitude = Math.min(Math.abs(deltaRatio), 3d);
        double impactScore = Math.min(0.99d, baseScore + statusBoost + magnitude * 0.12d);
        String impactBand = impactScore >= 0.78d ? "HIGH" : impactScore >= 0.58d ? "MEDIUM" : "LOW";
        String direction = deltaValue >= 0 ? "上升" : "下降";
        String impactSummary =
                "%s较基线%s %s".formatted(label, direction, renderChange(deltaValue, deltaRatio));

        return new ImpactMetadata(deltaValue, deltaRatio, impactScore, impactBand, impactSummary);
    }

    private static String renderChange(double deltaValue, double deltaRatio) {
        if (Math.abs(deltaRatio) >= 1d) {
            return String.format(Locale.ROOT, "%.0f%%", Math.abs(deltaRatio * 100d));
        }
        if (Math.abs(deltaValue - Math.rint(deltaValue)) < 0.00001d) {
            return String.valueOf((long) Math.abs(deltaValue));
        }
        return String.format(Locale.ROOT, "%.2f", Math.abs(deltaValue));
    }

    public record ImpactMetadata(
            double deltaValue,
            double deltaRatio,
            double impactScore,
            String impactBand,
            String impactSummary) {}
}

// src/main/java/io/agentscope/dataagent/insight/service/InsightFeedService.java
private final InsightImpactRanker impactRanker;

public InsightFeedService(
        InsightItemRepository itemRepository,
        InsightEvidenceRepository evidenceRepository,
        InsightImpactRanker impactRanker) {
    this.itemRepository = itemRepository;
    this.evidenceRepository = evidenceRepository;
    this.impactRanker = impactRanker;
}

private FeedItem toFeedItem(InsightItemEntity item) {
    InsightImpactRanker.ImpactMetadata impact =
            impactRanker.summarize(
                    item.getKind(),
                    item.getStatus().name(),
                    item.getMetricLabel(),
                    item.getMetricKey(),
                    item.getCurrentValue(),
                    item.getBaselineValue());
    return new FeedItem(
            item.getRowId(),
            item.getSourceId(),
            item.getKind(),
            item.getStatus().name(),
            item.getTitle(),
            item.getSummary(),
            item.getEvidenceSummary(),
            item.getObservedAt(),
            item.getCreatedAt(),
            item.getMetricKey(),
            item.getMetricLabel(),
            item.getDimensionName(),
            item.getDimensionValue(),
            impact.deltaValue(),
            impact.deltaRatio(),
            impact.impactScore(),
            impact.impactBand(),
            impact.impactSummary());
}

public record FeedItem(
        long id,
        String sourceId,
        String kind,
        String status,
        String title,
        String summary,
        String evidenceSummary,
        Instant observedAt,
        Instant createdAt,
        String metricKey,
        String metricLabel,
        String dimensionName,
        String dimensionValue,
        double deltaValue,
        double deltaRatio,
        double impactScore,
        String impactBand,
        String impactSummary) {}

// frontend/src/api/insights.ts
export interface InsightFeedItem {
  id: number;
  sourceId: string;
  kind: string;
  status: string;
  title: string;
  summary: string;
  evidenceSummary: string | null;
  observedAt: string;
  createdAt: string;
  metricKey: string;
  metricLabel: string | null;
  dimensionName: string | null;
  dimensionValue: string | null;
  deltaValue: number;
  deltaRatio: number;
  impactScore: number;
  impactBand: 'HIGH' | 'MEDIUM' | 'LOW';
  impactSummary: string;
}
```

- [ ] **Step 4: 重新运行后端测试，确认 feed 契约已经稳定**

Run: `mvn -Dtest=InsightFeedServiceTest,InsightFeedControllerTest test`

Expected: PASS，`InsightFeedServiceTest` 和 `InsightFeedControllerTest` 都通过，首页 feed JSON 现在带有业务影响字段。

- [ ] **Step 5: 提交这个后端契约补丁**

```bash
git add src/main/java/io/agentscope/dataagent/insight/service/InsightImpactRanker.java \
  src/main/java/io/agentscope/dataagent/insight/service/InsightFeedService.java \
  src/test/java/io/agentscope/dataagent/insight/service/InsightFeedServiceTest.java \
  src/test/java/io/agentscope/dataagent/web/api/InsightFeedControllerTest.java \
  frontend/src/api/insights.ts
git commit -m "feat: expose insight impact metadata for homepage ranking"
```

## Task 2: 建立统一主题和主壳层

**Files:**
- Create: `frontend/src/styles/app.css`
- Create: `frontend/src/styles/theme.css`
- Create: `frontend/src/styles/shell.css`
- Create: `frontend/src/components/__tests__/SessionsSidebar.test.tsx`
- Modify: `frontend/src/main.tsx`
- Modify: `frontend/src/components/AppShell.tsx`
- Modify: `frontend/src/components/SessionsSidebar.tsx`
- Modify: `frontend/src/components/BackToChatHeader.tsx`
- Modify: `frontend/src/components/ChatHeader.tsx`

- [ ] **Step 1: 先写一个壳层回归测试，锁定“洞察优先入口”语义**

```tsx
// frontend/src/components/__tests__/SessionsSidebar.test.tsx
import React from 'react';
import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import SessionsSidebar from '../SessionsSidebar';

vi.mock('../../api/sessions', () => ({
  inbox: vi.fn().mockResolvedValue([]),
  deleteSession: vi.fn(),
}));

vi.mock('../../api/auth', () => ({
  clearToken: vi.fn(),
  getToken: vi.fn().mockReturnValue('token'),
  isAdmin: vi.fn().mockReturnValue(false),
}));

describe('SessionsSidebar', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('renders insight-first helper copy outside the chat route', async () => {
    render(
      <MemoryRouter initialEntries={['/insights']}>
        <SessionsSidebar refreshKey={0} />
      </MemoryRouter>,
    );

    expect(screen.getByRole('button', { name: /Insights/i })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /Chat/i })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /Workspace/i })).toBeInTheDocument();
    expect(screen.getByText('经营洞察作战面板')).toBeInTheDocument();
    expect(screen.getByText(/先识别高影响项，再进入详情和 AI 分析/)).toBeInTheDocument();
  });
});
```

- [ ] **Step 2: 运行前端测试，确认现在还没有新的壳层文案和结构**

Run (from `D:\HillSchema\frontend`): `npm test -- src/components/__tests__/SessionsSidebar.test.tsx`

Expected: FAIL，因为当前 `SessionsSidebar` 没有 `经营洞察作战面板` 和 `先识别高影响项，再进入详情和 AI 分析` 这组新文案。

- [ ] **Step 3: 写最小实现，先把 token 和壳层类名收束起来**

```tsx
// frontend/src/main.tsx
import './styles/app.css';

// frontend/src/styles/app.css
@import './theme.css';
@import './shell.css';

// frontend/src/styles/theme.css
:root {
  color-scheme: light;
  --hs-bg: #eef1ef;
  --hs-surface: rgba(255, 255, 255, 0.74);
  --hs-surface-strong: #fbfcfa;
  --hs-surface-muted: #e7ece7;
  --hs-line: #d5ddd6;
  --hs-line-strong: #b8c3bb;
  --hs-ink: #162017;
  --hs-ink-soft: #556158;
  --hs-ink-faint: #748279;
  --hs-accent: #c47a29;
  --hs-accent-soft: #f2e1cc;
  --hs-danger: #b44838;
  --hs-radius-sm: 10px;
  --hs-radius-md: 16px;
  --hs-radius-lg: 24px;
  --hs-radius-xl: 30px;
  --hs-shadow-panel: 0 24px 60px rgba(18, 24, 19, 0.08);
  --hs-shadow-soft: 0 14px 28px rgba(18, 24, 19, 0.06);
  --hs-font-body: "Noto Sans SC", "PingFang SC", "Microsoft YaHei", sans-serif;
  --hs-font-figure: "IBM Plex Sans", "Segoe UI", sans-serif;
  --hs-breakpoint-tablet: 980px;
}

* {
  box-sizing: border-box;
}

html, body, #root {
  margin: 0;
  min-height: 100%;
}

body {
  background:
    radial-gradient(circle at top left, rgba(196, 122, 41, 0.11), transparent 28%),
    linear-gradient(180deg, #f6f7f4 0%, #edf1ee 100%);
  color: var(--hs-ink);
  font-family: var(--hs-font-body);
}

button, input, textarea {
  font: inherit;
}

// frontend/src/components/AppShell.tsx
return (
  <div className="app-shell">
    <SessionsSidebar refreshKey={refreshTick} />
    <div className="app-shell__content">
      <Outlet context={ctx} />
    </div>
  </div>
);

// frontend/src/components/SessionsSidebar.tsx
const PRIMARY_NAV_ITEMS = [
  { label: 'Insights', path: '/insights', eyebrow: '主入口' },
  { label: 'Chat', path: '/chat', eyebrow: '分析助手' },
  { label: 'Workspace', path: '/workspace', eyebrow: '只读浏览' },
];

<div className="shell-sidebar__helper">
  <div className="shell-sidebar__helper-title">经营洞察作战面板</div>
  <div className="shell-sidebar__helper-text">
    {location.pathname === '/insights'
      ? '先识别高影响项，再进入详情和 AI 分析。'
      : '当前区域沿用同一套作战台语言，不再单独长成后台页面。'}
  </div>
</div>

// frontend/src/components/ChatHeader.tsx
const name = agent?.name ?? 'DataAgent';
const mark = name.slice(0, 2).toUpperCase();

<div className="page-header">
  <div className="page-header__identity">
    <div className="page-header__mark">{mark}</div>
    <div className="page-header__copy">
      <span className="page-header__eyebrow">分析助手</span>
      <span className="page-header__title">{name}</span>
      {agent?.description && <span className="page-header__subtitle">{agent.description}</span>}
    </div>
  </div>
</div>
```

```css
/* frontend/src/styles/shell.css */
.app-shell {
  display: flex;
  min-height: 100vh;
  background: transparent;
  color: var(--hs-ink);
}

.app-shell__content {
  flex: 1;
  min-width: 0;
  min-height: 100vh;
  display: flex;
  flex-direction: column;
}

.shell-sidebar {
  width: 304px;
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  gap: 16px;
  padding: 18px 16px;
  background: rgba(250, 251, 248, 0.72);
  backdrop-filter: blur(18px);
  border-right: 1px solid rgba(184, 195, 187, 0.55);
}

.shell-sidebar__nav {
  display: grid;
  gap: 10px;
}

.shell-sidebar__nav-btn {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 4px;
  width: 100%;
  padding: 14px 16px;
  border-radius: var(--hs-radius-md);
  border: 1px solid transparent;
  background: var(--hs-surface);
  color: var(--hs-ink);
  box-shadow: var(--hs-shadow-soft);
  cursor: pointer;
}

.shell-sidebar__nav-btn.is-active {
  border-color: rgba(196, 122, 41, 0.32);
  background: linear-gradient(180deg, rgba(255,255,255,0.96) 0%, rgba(242,225,204,0.72) 100%);
}

.shell-sidebar__helper {
  border-radius: var(--hs-radius-lg);
  border: 1px solid rgba(184, 195, 187, 0.65);
  background: linear-gradient(180deg, rgba(255,255,255,0.94) 0%, rgba(231,236,231,0.78) 100%);
  padding: 16px 18px;
}

.shell-sidebar__helper-title {
  font-size: 0.78rem;
  font-weight: 800;
  letter-spacing: 0.14em;
  text-transform: uppercase;
  color: var(--hs-ink-soft);
}

.shell-sidebar__helper-text {
  margin-top: 8px;
  font-size: 0.9rem;
  line-height: 1.7;
  color: var(--hs-ink);
}

.page-header,
.back-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 18px;
  padding: 18px 26px;
  border-bottom: 1px solid rgba(184, 195, 187, 0.55);
  background: rgba(251, 252, 250, 0.88);
  backdrop-filter: blur(12px);
}

.page-header__mark {
  width: 40px;
  height: 40px;
  border-radius: 14px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(180deg, #ffffff 0%, #ecefe9 100%);
  border: 1px solid rgba(184, 195, 187, 0.8);
  font-family: var(--hs-font-figure);
  font-weight: 700;
}
```

- [ ] **Step 4: 再跑壳层测试，确认主题入口和辅助文案已经就位**

Run (from `D:\HillSchema\frontend`): `npm test -- src/components/__tests__/SessionsSidebar.test.tsx`

Expected: PASS，测试能找到三个主导航按钮和新的洞察优先辅助文案。

- [ ] **Step 5: 提交主题层和壳层骨架**

```bash
git add frontend/src/main.tsx \
  frontend/src/styles/app.css \
  frontend/src/styles/theme.css \
  frontend/src/styles/shell.css \
  frontend/src/components/AppShell.tsx \
  frontend/src/components/SessionsSidebar.tsx \
  frontend/src/components/BackToChatHeader.tsx \
  frontend/src/components/ChatHeader.tsx \
  frontend/src/components/__tests__/SessionsSidebar.test.tsx
git commit -m "feat: add shared operator-console shell theme"
```

## Task 3: 重做 Insights 首页信息架构

**Files:**
- Modify: `frontend/src/styles/app.css`
- Create: `frontend/src/styles/insights.css`
- Create: `frontend/src/components/insights/insightPresentation.ts`
- Create: `frontend/src/components/insights/InsightSummaryStrip.tsx`
- Create: `frontend/src/components/insights/InsightFeaturedRail.tsx`
- Modify: `frontend/src/pages/InsightsPage.tsx`
- Modify: `frontend/src/components/insights/InsightFeedList.tsx`
- Modify: `frontend/src/components/insights/InsightCard.tsx`
- Modify: `frontend/src/pages/__tests__/InsightsPage.test.tsx`

- [ ] **Step 1: 先写首页失败测试，锁定“默认按业务影响选中”**

```tsx
// frontend/src/pages/__tests__/InsightsPage.test.tsx
vi.mocked(insightsApi.listInsights).mockResolvedValue([
  {
    id: 202,
    sourceId: 'shop-demo',
    kind: 'ANOMALY',
    status: 'NEW',
    title: '退款率突然抬升',
    summary: '最近一个窗口退款率高于上一窗口。',
    evidenceSummary: '当前退款率 12%，上一窗口 4%。',
    observedAt: '2026-06-30T09:30:00Z',
    createdAt: '2026-06-30T09:31:00Z',
    metricKey: 'refund_rate',
    metricLabel: '退款率',
    dimensionName: null,
    dimensionValue: null,
    deltaValue: 8,
    deltaRatio: 2,
    impactScore: 0.72,
    impactBand: 'MEDIUM',
    impactSummary: '退款率较基线上升 200%',
  },
  {
    id: 101,
    sourceId: 'shop-demo',
    kind: 'TREND',
    status: 'CONTINUING',
    title: '订单量持续下滑',
    summary: '订单量连续两个窗口走低。',
    evidenceSummary: '当前 18 单，上一窗口 45 单。',
    observedAt: '2026-06-30T08:00:00Z',
    createdAt: '2026-06-30T08:01:00Z',
    metricKey: 'order_count',
    metricLabel: '订单量',
    dimensionName: 'channel',
    dimensionValue: '直播',
    deltaValue: -27,
    deltaRatio: -0.6,
    impactScore: 0.93,
    impactBand: 'HIGH',
    impactSummary: '订单量较基线下降 60%',
  },
]);

render(
  <MemoryRouter initialEntries={['/insights']}>
    <Routes>
      <Route path="/insights" element={<InsightsPage />} />
    </Routes>
  </MemoryRouter>,
);

expect(await screen.findByText('经营态势')).toBeInTheDocument();
expect(await screen.findByText('今日精选')).toBeInTheDocument();
await waitFor(() => {
  expect(vi.mocked(insightsApi.getInsightDetail)).toHaveBeenCalledWith('data-agent', 101);
});
expect(screen.getByText('订单量较基线下降 60%')).toBeInTheDocument();
```

- [ ] **Step 2: 运行首页测试，确认当前页面仍然按时间流选第一条**

Run (from `D:\HillSchema\frontend`): `npm test -- src/pages/__tests__/InsightsPage.test.tsx`

Expected: FAIL，当前 `InsightsPage` 会默认选中 feed 第一条 `202`，并且页面上没有 `经营态势` 和 `今日精选` 结构。

- [ ] **Step 3: 写最小实现，把首页拆成 top strip、精选区、作战列表**

```ts
// frontend/src/components/insights/insightPresentation.ts
import { InsightDetail, InsightFeedItem } from '../../api/insights';

export type InsightSortMode = 'impact' | 'urgency' | 'newest';

const urgencyWeight: Record<string, number> = {
  NEW: 3,
  CONTINUING: 2,
  ACKNOWLEDGED: 1,
  RESOLVED: 0,
};

export function sortInsights(items: InsightFeedItem[], mode: InsightSortMode): InsightFeedItem[] {
  const copy = [...items];
  copy.sort((left, right) => {
    if (mode === 'impact') {
      return (
        right.impactScore - left.impactScore ||
        (urgencyWeight[right.status] ?? 0) - (urgencyWeight[left.status] ?? 0) ||
        right.observedAt.localeCompare(left.observedAt)
      );
    }
    if (mode === 'urgency') {
      return (
        (urgencyWeight[right.status] ?? 0) - (urgencyWeight[left.status] ?? 0) ||
        right.impactScore - left.impactScore ||
        right.observedAt.localeCompare(left.observedAt)
      );
    }
    return right.observedAt.localeCompare(left.observedAt);
  });
  return copy;
}

export function pickFeaturedInsights(items: InsightFeedItem[]): InsightFeedItem[] {
  return sortInsights(items, 'impact').slice(0, 3);
}

export function buildSummaryStrip(items: InsightFeedItem[]) {
  const highImpactCount = items.filter(item => item.impactBand === 'HIGH').length;
  const anomalyCount = items.filter(item => item.kind === 'ANOMALY').length;
  const issueCount = items.filter(item => item.status === 'NEW').length;
  const refreshedAt = items[0]?.observedAt ?? null;
  return { highImpactCount, anomalyCount, issueCount, refreshedAt };
}

export function describeDetailImpact(detail: InsightDetail): string {
  const label = detail.metricLabel || detail.metricKey;
  const delta = detail.currentValue - detail.baselineValue;
  const direction = delta >= 0 ? '上升' : '下降';
  const ratio =
    detail.baselineValue === 0 ? Math.abs(detail.currentValue) : Math.abs(delta / detail.baselineValue) * 100;
  return `${label}较基线${direction} ${ratio.toFixed(0)}%`;
}
```

```tsx
// frontend/src/components/insights/InsightSummaryStrip.tsx
export default function InsightSummaryStrip({
  highImpactCount,
  anomalyCount,
  issueCount,
  refreshedAt,
}: {
  highImpactCount: number;
  anomalyCount: number;
  issueCount: number;
  refreshedAt: string | null;
}) {
  return (
    <section className="insight-strip">
      <div className="insight-strip__title">
        <span className="insight-strip__eyebrow">经营态势</span>
        <h1>先识别高影响洞察，再决定下一步动作</h1>
      </div>
      <div className="insight-strip__metrics">
        <div className="insight-strip__metric"><span>高影响项</span><strong>{highImpactCount}</strong></div>
        <div className="insight-strip__metric"><span>异常项</span><strong>{anomalyCount}</strong></div>
        <div className="insight-strip__metric"><span>待确认问题</span><strong>{issueCount}</strong></div>
        <div className="insight-strip__metric"><span>最近刷新</span><strong>{refreshedAt ?? '—'}</strong></div>
      </div>
    </section>
  );
}

// frontend/src/components/insights/InsightFeaturedRail.tsx
export default function InsightFeaturedRail({
  items,
  selectedId,
  onSelect,
}: {
  items: InsightFeedItem[];
  selectedId: number | null;
  onSelect: (id: number) => void;
}) {
  return (
    <section className="insight-featured">
      <div className="section-heading">
        <span className="section-heading__eyebrow">今日精选</span>
        <h2>值得先认真读的 2-3 条洞察</h2>
      </div>
      <div className="insight-featured__grid">
        {items.map(item => (
          <InsightCard
            key={item.id}
            item={item}
            selected={item.id === selectedId}
            onSelect={() => onSelect(item.id)}
            variant="featured"
          />
        ))}
      </div>
    </section>
  );
}

// frontend/src/components/insights/InsightCard.tsx
export default function InsightCard({ item, selected, onSelect, variant = 'list' }: InsightCardProps) {
  return (
    <button
      type="button"
      onClick={onSelect}
      className={`insight-card insight-card--${variant} ${selected ? 'is-selected' : ''}`}
    >
      <div className={`insight-card__impact insight-card__impact--${item.impactBand.toLowerCase()}`}>
        {item.impactSummary}
      </div>
      <div className="insight-card__title">{item.title}</div>
      <div className="insight-card__summary">{item.summary}</div>
      {item.evidenceSummary && <div className="insight-card__evidence">{item.evidenceSummary}</div>}
    </button>
  );
}
```

```tsx
// frontend/src/pages/InsightsPage.tsx
const [sortMode, setSortMode] = useState<InsightSortMode>('impact');

const orderedFeed = useMemo(() => sortInsights(feed, sortMode), [feed, sortMode]);
const featured = useMemo(() => pickFeaturedInsights(feed), [feed]);
const strip = useMemo(() => buildSummaryStrip(feed), [feed]);

useEffect(() => {
  if (feedLoading || orderedFeed.length === 0) return;
  const exists = selectedId != null && orderedFeed.some(item => item.id === selectedId);
  if (exists) return;
  const next = new URLSearchParams(searchParams);
  next.set('insight', String(orderedFeed[0].id));
  next.delete('followup');
  setSearchParams(next, { replace: true });
}, [feedLoading, orderedFeed, searchParams, selectedId, setSearchParams]);

return (
  <div className="insights-page">
    <div className="insights-page__content">
      <InsightSummaryStrip {...strip} />
      <InsightFeaturedRail items={featured} selectedId={selectedId} onSelect={handleSelect} />
      <section className="insights-page__board">
        <div className="insights-page__list-panel">
          <div className="section-heading">
            <span className="section-heading__eyebrow">作战列表</span>
            <h2>默认按业务影响排序</h2>
          </div>
          <div className="insight-sort-toggle">
            {(['impact', 'urgency', 'newest'] as InsightSortMode[]).map(mode => (
              <button
                key={mode}
                type="button"
                className={mode === sortMode ? 'is-active' : ''}
                onClick={() => setSortMode(mode)}
              >
                {mode === 'impact' ? '业务影响' : mode === 'urgency' ? '紧急程度' : '最新发现'}
              </button>
            ))}
          </div>
          <InsightFeedList
            items={orderedFeed}
            loading={feedLoading}
            error={feedError}
            selectedId={selectedId}
            onSelect={handleSelect}
          />
        </div>
        <div className="insights-page__detail-panel">
          <InsightDetailPanel detail={detail} loading={detailLoading} error={detailError} />
        </div>
      </section>
    </div>
  </div>
);
```

```css
/* frontend/src/styles/app.css */
@import './theme.css';
@import './shell.css';
@import './insights.css';

/* frontend/src/styles/insights.css */
.insights-page {
  flex: 1;
  min-height: 0;
  overflow: auto;
}

.insights-page__content {
  display: flex;
  flex-direction: column;
  gap: 22px;
  padding: 24px 26px 30px;
}

.insight-strip {
  padding: 26px 28px;
  border-radius: var(--hs-radius-xl);
  background: linear-gradient(180deg, rgba(255,255,255,0.94) 0%, rgba(231,236,231,0.84) 100%);
  border: 1px solid rgba(184, 195, 187, 0.72);
  box-shadow: var(--hs-shadow-panel);
}

.insight-featured__grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 16px;
}

.insights-page__board {
  display: grid;
  grid-template-columns: minmax(360px, 460px) minmax(0, 1fr);
  gap: 18px;
  align-items: start;
}

.insight-card__impact--high { color: #8a4c16; }
.insight-card__impact--medium { color: #6d5f2f; }
.insight-card__impact--low { color: var(--hs-ink-soft); }

@media (max-width: 980px) {
  .insight-featured__grid,
  .insights-page__board {
    grid-template-columns: 1fr;
  }
}
```

- [ ] **Step 4: 再跑首页测试，确认首页已经切到“精选 + 作战列表”结构**

Run (from `D:\HillSchema\frontend`): `npm test -- src/pages/__tests__/InsightsPage.test.tsx`

Expected: PASS，页面能渲染 `经营态势`、`今日精选`，且默认会请求高影响项 `101` 的详情。

- [ ] **Step 5: 提交首页信息架构改造**

```bash
git add frontend/src/styles/app.css \
  frontend/src/styles/insights.css \
  frontend/src/components/insights/insightPresentation.ts \
  frontend/src/components/insights/InsightSummaryStrip.tsx \
  frontend/src/components/insights/InsightFeaturedRail.tsx \
  frontend/src/components/insights/InsightCard.tsx \
  frontend/src/components/insights/InsightFeedList.tsx \
  frontend/src/pages/InsightsPage.tsx \
  frontend/src/pages/__tests__/InsightsPage.test.tsx
git commit -m "feat: redesign insights homepage as operator board"
```

## Task 4: 重排详情页与 AI 辅助区

**Files:**
- Modify: `frontend/src/components/ChatPanel.tsx`
- Modify: `frontend/src/components/insights/InsightDetailPanel.tsx`
- Modify: `frontend/src/components/insights/InsightScopedChatPanel.tsx`
- Modify: `frontend/src/components/__tests__/InsightScopedChatPanel.test.tsx`

- [ ] **Step 1: 先写一个失败测试，锁定预置动作按钮**

```tsx
// frontend/src/components/__tests__/InsightScopedChatPanel.test.tsx
expect(await screen.findByRole('button', { name: '解释原因' })).toBeInTheDocument();

await user.click(screen.getByRole('button', { name: '解释原因' }));

await waitFor(() => {
  expect(transport.stream).toHaveBeenCalledWith('data-agent', {
    message: '请结合当前结论、证据和时间窗口，解释「退款率突然抬升」为什么值得优先处理。',
    sessionKey: undefined,
  });
});
```

- [ ] **Step 2: 运行 scoped chat 测试，确认当前组件还没有快捷动作**

Run (from `D:\HillSchema\frontend`): `npm test -- src/components/__tests__/InsightScopedChatPanel.test.tsx`

Expected: FAIL，因为当前 `InsightScopedChatPanel` 只渲染说明文字和 `ChatPanel`，没有 `解释原因` 之类的动作按钮，也不会直接发送预置 prompt。

- [ ] **Step 3: 写最小实现，把详情页主路径改成“结论 -> 证据 -> 上下文 -> AI”**

```tsx
// frontend/src/components/ChatPanel.tsx
export interface ChatPanelProps {
  agentId: string;
  onSessionUpdate?: () => void;
  transport?: ChatTransport;
  sessionStorageScope?: string;
  sessionParamName?: string;
  placeholder?: string;
  emptyState?: string;
  composerActions?: Array<{ label: string; prompt: string }>;
}

async function submitMessage(rawText: string) {
  const text = rawText.trim();
  if (!text || busy || restoring) return;
  setInput('');
  setBusy(true);
  const userMsg: Message = { id: nextId(), role: 'user', text, tools: [] };
  const replyMsg: Message = { id: nextId(), role: 'assistant', text: '', tools: [], pending: true };
  setMessages(prev => [...prev, userMsg, replyMsg]);
  try {
    for await (const evt of transport.stream(agentId, { message: text, sessionKey: sessionKey ?? undefined })) {
      if (evt.type === 'token') {
        const chunk = evt.data ?? '';
        setMessages(prev => prev.map(message =>
          message.id === replyMsg.id ? { ...message, text: message.text + chunk } : message,
        ));
      } else if (evt.type === 'tool_call') {
        const toolEntry: ToolEntry = {
          id: `${evt.toolName ?? 'tool'}-${Date.now()}`,
          name: evt.toolName ?? 'tool',
          input: evt.toolInput,
        };
        setMessages(prev => prev.map(message =>
          message.id === replyMsg.id ? { ...message, tools: [...message.tools, toolEntry] } : message,
        ));
      } else if (evt.type === 'tool_result') {
        setMessages(prev => prev.map(message => {
          if (message.id !== replyMsg.id) return message;
          const tools = [...message.tools];
          let lastIndex = -1;
          for (let i = tools.length - 1; i >= 0; i -= 1) {
            if (tools[i].name === (evt.toolName ?? 'tool') && !tools[i].result) {
              lastIndex = i;
              break;
            }
          }
          if (lastIndex >= 0) {
            tools[lastIndex] = { ...tools[lastIndex], result: evt.toolResult };
            return { ...message, tools };
          }
          return {
            ...message,
            tools: [...tools, { id: `${evt.toolName ?? 'tool'}-result`, name: evt.toolName ?? 'tool', result: evt.toolResult }],
          };
        }));
      } else if (evt.type === 'done') {
        if (evt.sessionKey) {
          setSessionKey(evt.sessionKey);
          persistSession(evt.sessionKey);
        }
        setMessages(prev => prev.map(message =>
          message.id === replyMsg.id ? { ...message, pending: false } : message,
        ));
      } else if (evt.type === 'error') {
        setMessages(prev => prev.map(message =>
          message.id === replyMsg.id
            ? { ...message, pending: false, text: `[error] ${evt.error ?? 'unknown'}` }
            : message,
        ));
      }
    }
    onSessionUpdate?.();
  } finally {
    setBusy(false);
    inputRef.current?.focus();
  }
}

async function handleSend() {
  await submitMessage(input);
}

{composerActions && composerActions.length > 0 && (
  <div className="chat-panel__actions">
    {composerActions.map(action => (
      <button
        key={action.label}
        type="button"
        className="chat-panel__action"
        disabled={busy || restoring}
        onClick={() => void submitMessage(action.prompt)}
      >
        {action.label}
      </button>
    ))}
  </div>
)}
```

```tsx
// frontend/src/components/insights/InsightScopedChatPanel.tsx
const actions = useMemo(
  () => [
    {
      label: '解释原因',
      prompt: `请结合当前结论、证据和时间窗口，解释「${detail.title}」为什么值得优先处理。`,
    },
    {
      label: '拆解排查步骤',
      prompt: `请把「${detail.title}」拆成 3 到 5 个优先级明确的排查步骤。`,
    },
    {
      label: '给我后续动作建议',
      prompt: `请基于当前洞察，给出后续动作建议，并说明先后顺序。`,
    },
    ...detail.followUpQuestions.slice(0, 2).map((question, index) => ({
      label: index === 0 ? '继续追问' : `追问 ${index + 2}`,
      prompt: question,
    })),
  ],
  [detail],
);

return (
  <section className="insight-assistant">
    <div className="section-heading">
      <span className="section-heading__eyebrow">AI 辅助</span>
      <h2>先看证据，再发起分析动作</h2>
    </div>
    <ChatPanel
      agentId={agentId}
      transport={transport}
      sessionParamName="followup"
      sessionStorageScope={`insight:${agentId}:${detail.id}`}
      placeholder={`围绕「${detail.title}」继续追问…`}
      emptyState="优先点一个动作，或围绕当前洞察继续提问。"
      composerActions={actions}
    />
  </section>
);
```

```tsx
// frontend/src/components/insights/InsightDetailPanel.tsx
return (
  <div className="insight-detail">
    <section className="insight-detail__hero">
      <div className="section-heading">
        <span className="section-heading__eyebrow">结论摘要</span>
        <h2>{detail.title}</h2>
      </div>
      <div className="insight-detail__impact">{describeDetailImpact(detail)}</div>
      <p className="insight-detail__summary">{detail.conclusion || detail.summary}</p>
    </section>

    <div className="insight-detail__grid">
      <div className="insight-detail__main">
        <section className="insight-detail__section">
          <h3>关键证据</h3>
          {detail.evidenceSummary && (
            <div className="insight-detail__summary-card">{detail.evidenceSummary}</div>
          )}
          <div className="insight-detail__evidence-list">
            {detail.evidence.map(item => (
              <article key={`${detail.id}-${item.evidenceKey}`} className="insight-evidence-card">
                <header className="insight-evidence-card__header">
                  <div>
                    <strong>{item.label}</strong>
                    {item.detailText && <span>{item.detailText}</span>}
                  </div>
                  {item.valueText && <em>{item.valueText}</em>}
                </header>
                <pre>{renderSnapshot(item.snapshotJson)}</pre>
              </article>
            ))}
          </div>
        </section>
        <section className="insight-detail__section">
          <h3>数据明细</h3>
          <div className="insight-detail__metric-grid">
            <MetricCell label={`${detail.metricLabel || detail.metricKey} 当前值`} value={String(detail.currentValue)} />
            <MetricCell label="基线值" value={String(detail.baselineValue)} />
            <MetricCell label="变化量" value={metricDelta(detail)} />
          </div>
        </section>
        <section className="insight-detail__section">
          <h3>分析上下文</h3>
          <div className="insight-detail__context-grid">
            <MetricCell label="观察时间" value={formatDate(detail.observedAt)} />
            <MetricCell label="窗口起点" value={formatDate(detail.windowStart)} />
            <MetricCell label="窗口终点" value={formatDate(detail.windowEnd)} />
            <MetricCell label="来源" value={detail.sourceId} />
            <MetricCell
              label="维度"
              value={detail.dimensionValue ? `${detail.dimensionName || '维度'}: ${detail.dimensionValue}` : '全局'}
            />
          </div>
        </section>
      </div>

      <div className="insight-detail__aside">
        <InsightScopedChatPanel agentId={ACTIVE_AGENT_ID} detail={detail} />
      </div>
    </div>
  </div>
);
```

- [ ] **Step 4: 再跑 scoped chat 测试，确认详情页 AI 面板已经可直接执行动作**

Run (from `D:\HillSchema\frontend`): `npm test -- src/components/__tests__/InsightScopedChatPanel.test.tsx`

Expected: PASS，测试能找到 `解释原因` 动作按钮，并验证点击后调用 `transport.stream(...)`。

- [ ] **Step 5: 提交详情页与 AI 面板改造**

```bash
git add frontend/src/components/ChatPanel.tsx \
  frontend/src/components/insights/InsightDetailPanel.tsx \
  frontend/src/components/insights/InsightScopedChatPanel.tsx \
  frontend/src/components/__tests__/InsightScopedChatPanel.test.tsx
git commit -m "feat: restructure insight detail and scoped ai assistant"
```

## Task 5: 登录页与 Workspace 轻量跟随

**Files:**
- Modify: `frontend/src/styles/app.css`
- Create: `frontend/src/styles/auth.css`
- Create: `frontend/src/styles/workspace.css`
- Create: `frontend/src/pages/__tests__/LoginPage.test.tsx`
- Modify: `frontend/src/pages/LoginPage.tsx`
- Modify: `frontend/src/pages/WorkspacePage.tsx`
- Modify: `frontend/src/pages/__tests__/WorkspacePage.test.tsx`

- [ ] **Step 1: 先写失败测试，锁定登录页中文入口和 Workspace 摘要卡**

```tsx
// frontend/src/pages/__tests__/LoginPage.test.tsx
import React from 'react';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import LoginPage from '../LoginPage';
import * as authApi from '../../api/auth';

vi.mock('../../api/auth', () => ({
  login: vi.fn(),
  saveToken: vi.fn(),
}));

const navigate = vi.fn();
vi.mock('react-router-dom', async () => {
  const actual = await vi.importActual<typeof import('react-router-dom')>('react-router-dom');
  return { ...actual, useNavigate: () => navigate };
});

describe('LoginPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('submits credentials and redirects to insights', async () => {
    vi.mocked(authApi.login).mockResolvedValue({ token: 'token' });
    const user = userEvent.setup();

    render(
      <MemoryRouter>
        <LoginPage />
      </MemoryRouter>,
    );

    expect(screen.getByText('进入经营洞察作战台')).toBeInTheDocument();

    await user.type(screen.getByLabelText('用户名'), 'analyst');
    await user.type(screen.getByLabelText('密码'), 'secret');
    await user.click(screen.getByRole('button', { name: '进入作战台' }));

    await waitFor(() => {
      expect(authApi.login).toHaveBeenCalledWith('analyst', 'secret');
      expect(authApi.saveToken).toHaveBeenCalledWith('token');
      expect(navigate).toHaveBeenCalledWith('/insights', { replace: true });
    });
  });
});

// frontend/src/pages/__tests__/WorkspacePage.test.tsx
expect(await screen.findByText('Workspace 浏览区')).toBeInTheDocument();
expect(await screen.findByText('已安装技能')).toBeInTheDocument();
expect(await screen.findByText('2')).toBeInTheDocument();
```

- [ ] **Step 2: 运行登录页和 Workspace 测试，确认当前文案与摘要结构都还不存在**

Run (from `D:\HillSchema\frontend`): `npm test -- src/pages/__tests__/LoginPage.test.tsx src/pages/__tests__/WorkspacePage.test.tsx`

Expected: FAIL，当前登录页仍是英文 `Sign in`，Workspace 也还没有 `Workspace 浏览区` 和摘要卡。

- [ ] **Step 3: 写最小实现，让登录页和 Workspace 跟上新的主产品语言**

```css
/* frontend/src/styles/app.css */
@import './theme.css';
@import './shell.css';
@import './insights.css';
@import './auth.css';
@import './workspace.css';

/* frontend/src/styles/auth.css */
.login-page {
  min-height: 100vh;
  display: grid;
  grid-template-columns: minmax(0, 1.1fr) minmax(360px, 420px);
  gap: 28px;
  padding: 32px;
  background:
    radial-gradient(circle at top right, rgba(196,122,41,0.14), transparent 30%),
    linear-gradient(180deg, #f6f7f4 0%, #edf1ee 100%);
}

.login-page__hero,
.login-page__card {
  border-radius: var(--hs-radius-xl);
  border: 1px solid rgba(184, 195, 187, 0.64);
  background: rgba(255,255,255,0.82);
  box-shadow: var(--hs-shadow-panel);
}

/* frontend/src/styles/workspace.css */
.workspace-page {
  display: flex;
  flex-direction: column;
  min-height: 0;
  flex: 1;
}

.workspace-page__summary {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
  padding: 18px 24px 0;
}
```

```tsx
// frontend/src/pages/LoginPage.tsx
return (
  <div className="login-page">
    <section className="login-page__hero">
      <div className="section-heading">
        <span className="section-heading__eyebrow">HillSchema</span>
        <h1>进入经营洞察作战台</h1>
      </div>
      <p>
        默认入口不再是通用聊天页，而是高影响洞察工作面。先看哪里值得处理，再决定要不要调 AI 深挖。
      </p>
    </section>

    <form className="login-page__card" onSubmit={handleSubmit}>
      <label htmlFor="username">用户名</label>
      <input id="username" type="text" value={username} onChange={e => setUsername(e.target.value)} />
      <label htmlFor="password">密码</label>
      <input id="password" type="password" value={password} onChange={e => setPassword(e.target.value)} />
      {error && <div className="login-page__error">{error === 'Invalid username or password' ? '用户名或密码错误' : error}</div>}
      <button type="submit" disabled={loading}>{loading ? '正在进入…' : '进入作战台'}</button>
    </form>
  </div>
);

// frontend/src/pages/WorkspacePage.tsx
return (
  <div className="workspace-page">
    <BackToChatHeader title="Workspace 浏览区" subtitle="延续主产品语言的只读文件视图" />
    {summary && (
      <section className="workspace-page__summary">
        <div className="workspace-stat"><span>已安装技能</span><strong>{summary.skillCount}</strong></div>
        <div className="workspace-stat"><span>子代理</span><strong>{summary.subagentCount}</strong></div>
        <div className="workspace-stat"><span>每日记忆</span><strong>{summary.dailyMemoryCount}</strong></div>
        <div className="workspace-stat"><span>AGENTS.md</span><strong>{summary.agentsMdExists ? '已就绪' : '缺失'}</strong></div>
      </section>
    )}
    {summary?.workspacePath && (
      <div className="workspace-page__path" title={summary.workspacePath}>
        <span>Path</span>
        <strong>{summary.workspacePath}</strong>
      </div>
    )}
    <div className="workspace-page__hint">
      只读浏览区。编辑仍然通过 Skills、Subagents、Tools 页面或直接与 agent 对话完成。
    </div>
    <div className="workspace-page__main">
      <WorkspaceFileTree
        agentId={agentId}
        selectedPath={selected}
        onSelect={p => setSelected(p || null)}
        refreshKey={refreshKey}
        onRefresh={() => setRefreshKey(k => k + 1)}
      />
      <WorkspaceEditor
        agentId={agentId}
        path={selected}
        refreshKey={refreshKey}
      />
    </div>
  </div>
);
```

- [ ] **Step 4: 再跑登录页和 Workspace 测试，确认轻量跟随已经落地**

Run (from `D:\HillSchema\frontend`): `npm test -- src/pages/__tests__/LoginPage.test.tsx src/pages/__tests__/WorkspacePage.test.tsx`

Expected: PASS，登录页成功展示中文入口并能跳转到 `/insights`；Workspace 能渲染新的摘要卡和标题。

- [ ] **Step 5: 提交登录页和 Workspace 跟随改造**

```bash
git add frontend/src/styles/app.css \
  frontend/src/styles/auth.css \
  frontend/src/styles/workspace.css \
  frontend/src/pages/LoginPage.tsx \
  frontend/src/pages/WorkspacePage.tsx \
  frontend/src/pages/__tests__/LoginPage.test.tsx \
  frontend/src/pages/__tests__/WorkspacePage.test.tsx
git commit -m "feat: align login and workspace with operator-console theme"
```

## Task 6: 联调、构建与验收

**Files:**
- Test: `frontend/src/components/__tests__/SessionsSidebar.test.tsx`
- Test: `frontend/src/pages/__tests__/InsightsPage.test.tsx`
- Test: `frontend/src/components/__tests__/InsightScopedChatPanel.test.tsx`
- Test: `frontend/src/pages/__tests__/LoginPage.test.tsx`
- Test: `frontend/src/pages/__tests__/WorkspacePage.test.tsx`
- Test: `src/test/java/io/agentscope/dataagent/insight/service/InsightFeedServiceTest.java`
- Test: `src/test/java/io/agentscope/dataagent/web/api/InsightFeedControllerTest.java`

- [ ] **Step 1: 跑前端聚焦测试，确认五个核心回归点全部稳定**

Run (from `D:\HillSchema\frontend`): `npm test -- src/components/__tests__/SessionsSidebar.test.tsx src/pages/__tests__/InsightsPage.test.tsx src/components/__tests__/InsightScopedChatPanel.test.tsx src/pages/__tests__/LoginPage.test.tsx src/pages/__tests__/WorkspacePage.test.tsx`

Expected: PASS，Vitest 输出 5 个目标测试文件全部通过。

- [ ] **Step 2: 跑后端 feed 契约测试，确认没有把首页数据接口写坏**

Run: `mvn -Dtest=InsightFeedServiceTest,InsightFeedControllerTest test`

Expected: PASS，首页 feed 元数据序列化和服务层计算都通过。

- [ ] **Step 3: 跑前端构建，确认 CSS 和组件拆分没有引入类型或打包错误**

Run (from `D:\HillSchema\frontend`): `npm run build`

Expected: PASS，输出 `vite build` 成功，`tsc --noEmit` 不报类型错误。

- [ ] **Step 4: 做一次人工验收，严格按 spec 检查主路径**

Run:

```text
1. 打开 /login，确认文案是“进入经营洞察作战台”，而不是默认英文登录卡。
2. 登录后落到 /insights，确认第一屏先看到 top strip 和精选洞察，不是聊天输入框。
3. 确认首页默认选中高 impact 项，而不是简单按最新时间选第一条。
4. 点进详情后，按顺序看到“结论摘要 -> 关键证据 -> 数据明细 -> 分析上下文 -> AI 辅助”。
5. 点击“解释原因”或“拆解排查步骤”，确认 scoped chat 直接围绕当前洞察发起请求。
6. 打开 /workspace，确认只是轻量跟随新语言，没有被重做成另一套产品。
7. 把窗口缩到窄屏，确认首页从双列变单列、详情区把 AI 面板下沉到主内容之后。
```

Expected: 所有路径都符合 spec 的三条总原则：`业务影响优先`、`证据优先`、`AI 辅助`。

- [ ] **Step 5: 提交最终验收结果**

```bash
git add frontend/src \
  src/main/java/io/agentscope/dataagent/insight/service/InsightImpactRanker.java \
  src/main/java/io/agentscope/dataagent/insight/service/InsightFeedService.java \
  src/test/java/io/agentscope/dataagent/insight/service/InsightFeedServiceTest.java \
  src/test/java/io/agentscope/dataagent/web/api/InsightFeedControllerTest.java
git commit -m "feat: ship insight-first operator console redesign"
```

## 顺序与依赖

1. 先做 `Task 1`，否则前端拿不到首页排序和业务影响表达需要的数据。
2. `Task 2` 先把 token 和壳层落下，后面页面才不会继续扩散内联样式。
3. `Task 3` 在 `Task 1` 和 `Task 2` 之后做，因为首页既依赖 feed metadata，也依赖统一样式层。
4. `Task 4` 必须放在 `Task 3` 后面，这样详情页和 AI 面板能复用首页已经建立的洞察语义与类名。
5. `Task 5` 最后补登录和 Workspace，保证范围控制在 spec 明确覆盖的页面。
6. `Task 6` 只在前五个任务都完成后执行。

## 风险控制

- 不要把 admin/config 页面拖进来。它们不在本轮设计预算里。
- 不要在 `InsightDetail` 上追加第二套与首页平行的 impact contract。详情页已有 `currentValue` / `baselineValue`，前端本地计算就够了。
- 不要一边保留大面积 `style={{...}}`，一边又新建 CSS token。主壳层和核心用户面一旦进入本计划，就应转到统一类名和变量体系。
- 不要为了预置 AI 动作重写整套聊天组件。只扩展 `ChatPanel` 的动作入口，保留现有 transport / session / token 流。

## 验收映射

- Spec 里的 `色彩/字体/token/禁止项`：`Task 2`
- Spec 里的 `首页 top strip / 精选区 / 作战列表 / 排序规则`：`Task 3`
- Spec 里的 `详情页主路径 / AI 辅助角色 / 上下文连续性`：`Task 4`
- Spec 里的 `登录页 / AppShell / 导航 / Workspace 轻量跟随`：`Task 2` + `Task 5`
- Spec 里的 `加载态 / 空态 / 错误态 / 响应式`：`Task 3` + `Task 4` + `Task 6`
