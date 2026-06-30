import React from 'react';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { InsightDetail } from '../../api/insights';
import InsightScopedChatPanel from '../insights/InsightScopedChatPanel';
import * as insightChatApi from '../../api/insightChat';

vi.mock('../../api/insightChat', () => ({
  createInsightChatTransport: vi.fn(),
}));

describe('InsightScopedChatPanel', () => {
  const detail: InsightDetail = {
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
    followUpQuestions: ['问题现在是否还在持续？'],
    evidence: [],
  };

  beforeEach(() => {
    vi.clearAllMocks();
    localStorage.clear();
  });

  it('renders issue-scoped chat and stores its session independently', async () => {
    const transport = {
      currentSession: vi.fn().mockResolvedValue({ sessionKey: null, exists: false }),
      turns: vi.fn().mockImplementation(async (_agentId: string, sessionKey: string) => {
        if (sessionKey !== 'conv-1') return [];
        return [
          {
            id: 'u-1',
            parentId: null,
            role: 'USER' as const,
            content: '为什么会发生？',
            timestampMs: 1,
            toolName: null,
            toolInput: null,
            toolResult: null,
          },
          {
            id: 'a-1',
            parentId: 'u-1',
            role: 'ASSISTANT' as const,
            content: '先检查退款商品与履约异常。',
            timestampMs: 2,
            toolName: null,
            toolInput: null,
            toolResult: null,
          },
        ];
      }),
      stream: vi.fn(async function* () {
        yield { type: 'token', data: '先检查退款商品与履约异常。' };
        yield { type: 'done', sessionKey: 'conv-1' };
      }),
    };
    vi.mocked(insightChatApi.createInsightChatTransport).mockReturnValue(transport);

    const user = userEvent.setup();
    render(
      <MemoryRouter initialEntries={['/insights?insight=202']}>
        <Routes>
          <Route
            path="/insights"
            element={<InsightScopedChatPanel agentId="data-agent" detail={detail} />}
          />
        </Routes>
      </MemoryRouter>,
    );

    expect(await screen.findByText('围绕当前问题继续追问')).toBeInTheDocument();
    expect(vi.mocked(insightChatApi.createInsightChatTransport)).toHaveBeenCalledWith(202);
    await waitFor(() => {
      expect(transport.currentSession).toHaveBeenCalledWith('data-agent', undefined);
    });

    await user.type(
      screen.getByPlaceholderText('继续追问「退款率突然抬升」…'),
      '为什么会发生？',
    );
    await waitFor(() => {
      expect(screen.getByRole('button', { name: 'Send' })).toBeEnabled();
    });
    await user.click(screen.getByRole('button', { name: 'Send' }));

    await waitFor(() => {
      expect(transport.stream).toHaveBeenCalledWith('data-agent', {
        message: '为什么会发生？',
        sessionKey: undefined,
      });
    });
    expect(await screen.findByText('先检查退款商品与履约异常。')).toBeInTheDocument();
    expect(localStorage.getItem('claw_chat_session:insight:data-agent:202')).toBe('conv-1');
  });
});
