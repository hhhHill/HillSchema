import React from 'react';
import { InsightFeedItem } from '../../api/insights';
import InsightCard from './InsightCard';

export interface InsightFeedListProps {
  items: InsightFeedItem[];
  loading: boolean;
  error: string | null;
  selectedId: number | null;
  onSelect: (id: number) => void;
}

export default function InsightFeedList({
  items,
  loading,
  error,
  selectedId,
  onSelect,
}: InsightFeedListProps) {
  if (loading) {
    return <div style={stateBox}>正在读取最近生成的问题消息…</div>;
  }
  if (error) {
    return <div style={{ ...stateBox, color: '#b91c1c', borderColor: '#fecaca', background: '#fef2f2' }}>{error}</div>;
  }
  if (items.length === 0) {
    return <div style={stateBox}>还没有生成任何问题消息。等待后台调度写入后，这里会出现自动洞察流。</div>;
  }

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 14 }}>
      {items.map(item => (
        <InsightCard
          key={item.id}
          item={item}
          selected={item.id === selectedId}
          onSelect={() => onSelect(item.id)}
        />
      ))}
    </div>
  );
}

const stateBox: React.CSSProperties = {
  padding: '20px 18px',
  borderRadius: 18,
  border: '1px dashed #cbd5e1',
  background: 'rgba(255,255,255,0.78)',
  color: '#64748b',
  lineHeight: 1.6,
  fontSize: '0.88rem',
};
