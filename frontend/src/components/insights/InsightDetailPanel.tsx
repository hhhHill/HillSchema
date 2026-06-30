import React from 'react';
import { InsightDetail } from '../../api/insights';
import { ACTIVE_AGENT_ID } from '../../api/activeAgent';
import InsightStatusPill from './InsightStatusPill';
import InsightScopedChatPanel from './InsightScopedChatPanel';

export interface InsightDetailPanelProps {
  detail: InsightDetail | null;
  loading: boolean;
  error: string | null;
}

function formatDate(value: string): string {
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value;
  return new Intl.DateTimeFormat('zh-CN', {
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  }).format(date);
}

function renderSnapshot(snapshotJson: string | null): string {
  if (!snapshotJson) return '无快照';
  try {
    return JSON.stringify(JSON.parse(snapshotJson), null, 2);
  } catch {
    return snapshotJson;
  }
}

function metricDelta(detail: InsightDetail): string {
  const current = detail.currentValue;
  const baseline = detail.baselineValue;
  const delta = current - baseline;
  const direction = delta >= 0 ? '+' : '';
  return `${direction}${delta.toFixed(Math.abs(delta) % 1 < 0.00001 ? 0 : 2)}`;
}

export default function InsightDetailPanel({ detail, loading, error }: InsightDetailPanelProps) {
  if (loading) {
    return <div style={stateCard}>正在读取问题详情与证据快照…</div>;
  }
  if (error) {
    return <div style={{ ...stateCard, color: '#b91c1c', borderColor: '#fecaca', background: '#fef2f2' }}>{error}</div>;
  }
  if (!detail) {
    return <div style={stateCard}>从左侧选择一条问题消息，即可查看静态结论、证据和快照。</div>;
  }

  const metricLabel = detail.metricLabel || detail.metricKey;

  return (
    <div
      style={{
        background: '#ffffff',
        borderRadius: 24,
        border: '1px solid #dbe4ff',
        boxShadow: '0 24px 60px rgba(15,23,42,0.10)',
        overflow: 'hidden',
      }}
    >
      <div
        style={{
          padding: '26px 28px 22px',
          background: 'linear-gradient(135deg,#eff6ff 0%,#eef2ff 52%,#fff7ed 100%)',
          borderBottom: '1px solid #dbe4ff',
        }}
      >
        <div style={{ display: 'flex', flexWrap: 'wrap', gap: 10, alignItems: 'center', marginBottom: 14 }}>
          <InsightStatusPill status={detail.status} />
          <span style={metaChip}>{metricLabel}</span>
          <span style={metaChip}>{detail.sourceId}</span>
          {detail.dimensionValue && <span style={metaChip}>{detail.dimensionName || '维度'}: {detail.dimensionValue}</span>}
        </div>
        <h2 style={{ margin: 0, fontSize: '1.65rem', lineHeight: 1.2, color: '#0f172a' }}>{detail.title}</h2>
        <p style={{ margin: '12px 0 0', fontSize: '0.96rem', lineHeight: 1.7, color: '#334155', maxWidth: 920 }}>
          {detail.summary}
        </p>
      </div>

      <div style={{ padding: '24px 28px 30px', display: 'flex', flexDirection: 'column', gap: 22 }}>
        <section style={sectionCard}>
          <div style={sectionTitle}>静态结论</div>
          <div style={{ fontSize: '1rem', lineHeight: 1.8, color: '#0f172a' }}>
            {detail.conclusion || '当前暂无额外结论。'}
          </div>
        </section>

        <section style={sectionCard}>
          <div style={sectionTitle}>问题窗口</div>
          <div style={metricGrid}>
            <MetricCell label="观察时间" value={formatDate(detail.observedAt)} />
            <MetricCell label="窗口起点" value={formatDate(detail.windowStart)} />
            <MetricCell label="窗口终点" value={formatDate(detail.windowEnd)} />
            <MetricCell label={`${metricLabel} 当前值`} value={String(detail.currentValue)} />
            <MetricCell label="上一窗口基线" value={String(detail.baselineValue)} />
            <MetricCell label="变化量" value={metricDelta(detail)} />
          </div>
        </section>

        <section style={sectionCard}>
          <div style={sectionTitle}>证据快照</div>
          {detail.evidenceSummary && (
            <div
              style={{
                padding: '12px 14px',
                borderRadius: 14,
                background: '#f8fafc',
                border: '1px solid #e2e8f0',
                fontSize: '0.88rem',
                color: '#475569',
                lineHeight: 1.7,
                marginBottom: 16,
              }}
            >
              {detail.evidenceSummary}
            </div>
          )}
          <div style={{ display: 'flex', flexDirection: 'column', gap: 14 }}>
            {detail.evidence.length === 0 && (
              <div style={{ color: '#64748b', fontSize: '0.88rem' }}>当前问题没有额外证据项。</div>
            )}
            {detail.evidence.map(item => (
              <div
                key={`${detail.id}-${item.evidenceKey}`}
                style={{
                  borderRadius: 18,
                  border: '1px solid #e2e8f0',
                  background: '#ffffff',
                  overflow: 'hidden',
                }}
              >
                <div
                  style={{
                    padding: '14px 16px',
                    display: 'flex',
                    justifyContent: 'space-between',
                    gap: 12,
                    alignItems: 'center',
                    background: '#f8fafc',
                    borderBottom: '1px solid #e2e8f0',
                  }}
                >
                  <div>
                    <div style={{ fontSize: '0.92rem', fontWeight: 700, color: '#0f172a' }}>{item.label}</div>
                    {item.detailText && (
                      <div style={{ marginTop: 4, fontSize: '0.8rem', color: '#64748b' }}>{item.detailText}</div>
                    )}
                  </div>
                  {item.valueText && (
                    <div style={{ fontSize: '0.95rem', fontWeight: 700, color: '#312e81' }}>{item.valueText}</div>
                  )}
                </div>
                <pre
                  style={{
                    margin: 0,
                    padding: '16px',
                    whiteSpace: 'pre-wrap',
                    wordBreak: 'break-word',
                    background: '#0f172a',
                    color: '#e2e8f0',
                    fontSize: '0.8rem',
                    lineHeight: 1.6,
                    fontFamily: 'ui-monospace, SFMono-Regular, Menlo, monospace',
                  }}
                >
                  {renderSnapshot(item.snapshotJson)}
                </pre>
              </div>
            ))}
          </div>
        </section>

        <section style={sectionCard}>
          <div style={sectionTitle}>建议继续追问</div>
          <div style={{ display: 'flex', flexWrap: 'wrap', gap: 10 }}>
            {detail.followUpQuestions.length === 0 && (
              <div style={{ color: '#64748b', fontSize: '0.88rem' }}>当前问题暂无推荐追问。</div>
            )}
            {detail.followUpQuestions.map(question => (
              <span
                key={question}
                style={{
                  display: 'inline-flex',
                  alignItems: 'center',
                  padding: '10px 12px',
                  borderRadius: 999,
                  background: '#eef2ff',
                  border: '1px solid #c7d2fe',
                  color: '#3730a3',
                  fontSize: '0.84rem',
                  fontWeight: 600,
                }}
              >
                {question}
              </span>
            ))}
          </div>
        </section>

        <InsightScopedChatPanel agentId={ACTIVE_AGENT_ID} detail={detail} />
      </div>
    </div>
  );
}

function MetricCell({ label, value }: { label: string; value: string }) {
  return (
    <div
      style={{
        padding: '14px 16px',
        borderRadius: 16,
        background: '#ffffff',
        border: '1px solid #e2e8f0',
      }}
    >
      <div style={{ fontSize: '0.74rem', color: '#94a3b8', textTransform: 'uppercase', letterSpacing: '0.08em', fontWeight: 700 }}>
        {label}
      </div>
      <div style={{ marginTop: 8, fontSize: '1rem', color: '#0f172a', fontWeight: 700, lineHeight: 1.4 }}>{value}</div>
    </div>
  );
}

const sectionCard: React.CSSProperties = {
  borderRadius: 20,
  border: '1px solid #e2e8f0',
  background: '#fcfdff',
  padding: '18px 18px 20px',
};

const stateCard: React.CSSProperties = {
  padding: '24px 22px',
  borderRadius: 24,
  border: '1px dashed #cbd5e1',
  background: 'rgba(255,255,255,0.78)',
  color: '#64748b',
  fontSize: '0.92rem',
  lineHeight: 1.7,
};

const sectionTitle: React.CSSProperties = {
  fontSize: '0.78rem',
  color: '#64748b',
  textTransform: 'uppercase',
  letterSpacing: '0.12em',
  fontWeight: 800,
  marginBottom: 14,
};

const metaChip: React.CSSProperties = {
  display: 'inline-flex',
  alignItems: 'center',
  padding: '5px 10px',
  borderRadius: 999,
  background: 'rgba(255,255,255,0.88)',
  border: '1px solid rgba(165,180,252,0.55)',
  color: '#3730a3',
  fontSize: '0.75rem',
  fontWeight: 700,
};

const metricGrid: React.CSSProperties = {
  display: 'grid',
  gridTemplateColumns: 'repeat(auto-fit, minmax(170px, 1fr))',
  gap: 12,
};
