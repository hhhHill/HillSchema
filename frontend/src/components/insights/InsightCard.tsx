import React from 'react';
import { InsightFeedItem } from '../../api/insights';
import InsightStatusPill from './InsightStatusPill';

export interface InsightCardProps {
  item: InsightFeedItem;
  selected: boolean;
  onSelect: () => void;
}

function formatObservedAt(value: string): string {
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value;
  return new Intl.DateTimeFormat('zh-CN', {
    month: 'short',
    day: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  }).format(date);
}

export default function InsightCard({ item, selected, onSelect }: InsightCardProps) {
  const metricLabel = item.metricLabel || item.metricKey;
  const dimension = item.dimensionValue
    ? `${item.dimensionName || '维度'}: ${item.dimensionValue}`
    : null;

  return (
    <button
      type="button"
      onClick={onSelect}
      style={{
        width: '100%',
        textAlign: 'left',
        borderRadius: 18,
        border: selected ? '1px solid #a5b4fc' : '1px solid #e2e8f0',
        background: selected
          ? 'linear-gradient(180deg,#eef2ff 0%,#ffffff 100%)'
          : '#ffffff',
        boxShadow: selected
          ? '0 12px 30px rgba(99,102,241,0.12)'
          : '0 10px 24px rgba(15,23,42,0.05)',
        padding: '18px 18px 16px',
        cursor: 'pointer',
        transition: 'transform 0.12s ease, box-shadow 0.12s ease, border-color 0.12s ease',
      }}
    >
      <div style={{ display: 'flex', alignItems: 'flex-start', gap: 12, marginBottom: 12 }}>
        <div style={{ flex: 1, minWidth: 0 }}>
          <div
            style={{
              fontSize: '1rem',
              fontWeight: 700,
              color: '#0f172a',
              lineHeight: 1.35,
              marginBottom: 6,
            }}
          >
            {item.title}
          </div>
          <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap', alignItems: 'center' }}>
            <InsightStatusPill status={item.status} />
            <span style={{ fontSize: '0.76rem', color: '#64748b', fontWeight: 600 }}>
              {metricLabel}
            </span>
            <span style={{ fontSize: '0.76rem', color: '#94a3b8' }}>{formatObservedAt(item.observedAt)}</span>
          </div>
        </div>
      </div>

      <div style={{ fontSize: '0.86rem', color: '#334155', lineHeight: 1.6 }}>
        {item.summary}
      </div>

      {item.evidenceSummary && (
        <div
          style={{
            marginTop: 12,
            padding: '11px 12px',
            background: '#f8fafc',
            borderRadius: 12,
            border: '1px solid #e2e8f0',
            fontSize: '0.8rem',
            color: '#475569',
            lineHeight: 1.55,
          }}
        >
          {item.evidenceSummary}
        </div>
      )}

      {dimension && (
        <div style={{ marginTop: 10, fontSize: '0.78rem', color: '#7c3aed', fontWeight: 600 }}>
          {dimension}
        </div>
      )}
    </button>
  );
}
