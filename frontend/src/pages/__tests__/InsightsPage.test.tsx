import React from 'react';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import InsightsPage from '../InsightsPage';
import * as insightsApi from '../../api/insights';

vi.mock('../../api/insights', () => ({
  listInsights: vi.fn(),
  getInsightDetail: vi.fn(),
}));

vi.mock('react-router-dom', async () => {
  const actual = await vi.importActual<typeof import('react-router-dom')>('react-router-dom');
  return {
    ...actual,
    useOutletContext: () => ({
      agent: {
        id: 'data-agent',
        name: 'DataAgent',
        description: 'Insight-first operator',
        identityEmoji: 'AI',
        scope: 'global',
        createdAt: 0,
        updatedAt: 0,
        tierForCurrentUser: 'RUN',
      },
      agentLoading: false,
      agentError: null,
      bumpSidebar: vi.fn(),
    }),
  };
});

describe('InsightsPage', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('loads the feed, selects the first insight, and switches detail on click', async () => {
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
      },
    ]);

    vi.mocked(insightsApi.getInsightDetail)
      .mockResolvedValueOnce({
        id: 202,
        sourceId: 'shop-demo',
        kind: 'ANOMALY',
        status: 'NEW',
        title: '退款率突然抬升',
        summary: '最近一个窗口退款率高于上一窗口。',
        conclusion: '售后问题仍在持续，优先检查商品与履约链路。',
        evidenceSummary: '当前退款率 12%，上一窗口 4%。',
        observedAt: '2026-06-30T09:30:00Z',
        createdAt: '2026-06-30T09:31:00Z',
        windowStart: '2026-06-29T09:30:00Z',
        windowEnd: '2026-06-30T09:30:00Z',
        metricKey: 'refund_rate',
        metricLabel: '退款率',
        currentValue: 12,
        baselineValue: 4,
        dimensionName: null,
        dimensionValue: null,
        followUpQuestions: ['是哪个商品贡献了大部分退款？', '问题现在是否还在持续？'],
        evidence: [
          {
            evidenceKey: 'refundRate',
            label: '当前退款率',
            valueText: '12%',
            detailText: '最近 24 小时退款订单占比',
            snapshotJson: '{"metric":"refund_rate","value":12}',
          },
        ],
      })
      .mockResolvedValueOnce({
        id: 101,
        sourceId: 'shop-demo',
        kind: 'TREND',
        status: 'CONTINUING',
        title: '订单量持续下滑',
        summary: '订单量连续两个窗口走低。',
        conclusion: '直播渠道转化偏弱，需要结合投放与库存继续排查。',
        evidenceSummary: '当前 18 单，上一窗口 45 单。',
        observedAt: '2026-06-30T08:00:00Z',
        createdAt: '2026-06-30T08:01:00Z',
        windowStart: '2026-06-29T08:00:00Z',
        windowEnd: '2026-06-30T08:00:00Z',
        metricKey: 'order_count',
        metricLabel: '订单量',
        currentValue: 18,
        baselineValue: 45,
        dimensionName: 'channel',
        dimensionValue: '直播',
        followUpQuestions: ['哪个维度拖累最大？'],
        evidence: [
          {
            evidenceKey: 'orderCount',
            label: '当前订单量',
            valueText: '18',
            detailText: '最近 24 小时订单数',
            snapshotJson: '{"metric":"order_count","value":18}',
          },
        ],
      });

    const user = userEvent.setup();
    render(
      <MemoryRouter initialEntries={['/insights']}>
        <Routes>
          <Route path="/insights" element={<InsightsPage />} />
        </Routes>
      </MemoryRouter>,
    );

    expect(await screen.findByText('退款率突然抬升')).toBeInTheDocument();
    expect(await screen.findByText('售后问题仍在持续，优先检查商品与履约链路。')).toBeInTheDocument();
    expect(vi.mocked(insightsApi.getInsightDetail)).toHaveBeenCalledWith('data-agent', 202);

    await user.click(screen.getByRole('button', { name: /订单量持续下滑/i }));

    await waitFor(() => {
      expect(vi.mocked(insightsApi.getInsightDetail)).toHaveBeenLastCalledWith('data-agent', 101);
    });
    expect(await screen.findByText('直播渠道转化偏弱，需要结合投放与库存继续排查。')).toBeInTheDocument();
  });
});
