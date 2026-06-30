import React from 'react';

const STATUS_META: Record<string, { label: string; fg: string; bg: string; border: string }> = {
  NEW: { label: '新问题', fg: '#9f1239', bg: '#fff1f2', border: '#fecdd3' },
  CONTINUING: { label: '持续中', fg: '#92400e', bg: '#fffbeb', border: '#fde68a' },
  RESOLVED: { label: '已恢复', fg: '#166534', bg: '#ecfdf5', border: '#bbf7d0' },
};

export interface InsightStatusPillProps {
  status: string;
}

export default function InsightStatusPill({ status }: InsightStatusPillProps) {
  const meta = STATUS_META[status] ?? {
    label: status,
    fg: '#334155',
    bg: '#f8fafc',
    border: '#cbd5e1',
  };

  return (
    <span
      style={{
        display: 'inline-flex',
        alignItems: 'center',
        justifyContent: 'center',
        padding: '4px 9px',
        borderRadius: 999,
        fontSize: '0.72rem',
        fontWeight: 700,
        letterSpacing: '0.04em',
        color: meta.fg,
        background: meta.bg,
        border: `1px solid ${meta.border}`,
        textTransform: 'uppercase',
      }}
    >
      {meta.label}
    </span>
  );
}
