import React, { useMemo } from 'react';
import { InsightDetail } from '../../api/insights';
import { createInsightChatTransport } from '../../api/insightChat';
import ChatPanel from '../ChatPanel';

export interface InsightScopedChatPanelProps {
  agentId: string;
  detail: InsightDetail;
}

export default function InsightScopedChatPanel({ agentId, detail }: InsightScopedChatPanelProps) {
  const transport = useMemo(() => createInsightChatTransport(detail.id), [detail.id]);

  return (
    <section
      style={{
        borderRadius: 20,
        border: '1px solid #e2e8f0',
        background: '#fcfdff',
        padding: '18px 18px 20px',
      }}
    >
      <div style={{ fontSize: '0.78rem', color: '#64748b', textTransform: 'uppercase', letterSpacing: '0.12em', fontWeight: 800 }}>
        问题上下文对话
      </div>
      <div style={{ marginTop: 8, fontSize: '1.08rem', lineHeight: 1.5, color: '#0f172a', fontWeight: 700 }}>
        围绕当前问题继续追问
      </div>
      <div style={{ marginTop: 8, fontSize: '0.92rem', lineHeight: 1.7, color: '#475569' }}>
        这里的回答会显式绑定当前问题、结论和证据快照，避免退化成脱离上下文的泛化问答。
      </div>

      <div
        style={{
          marginTop: 16,
          height: 420,
          borderRadius: 18,
          overflow: 'hidden',
          border: '1px solid #dbe4ff',
          background: '#ffffff',
        }}
      >
        <ChatPanel
          agentId={agentId}
          transport={transport}
          sessionParamName="followup"
          sessionStorageScope={`insight:${agentId}:${detail.id}`}
          placeholder={`继续追问「${detail.title}」…`}
          emptyState="继续围绕当前问题提问，例如“为什么会发生？”或“下一步应该查什么？”"
        />
      </div>
    </section>
  );
}
