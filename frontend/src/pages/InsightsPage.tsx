import React, { useEffect, useMemo, useState } from 'react';
import { useNavigate, useOutletContext, useSearchParams } from 'react-router-dom';
import { ACTIVE_AGENT_ID } from '../api/activeAgent';
import { getInsightDetail, InsightDetail, InsightFeedItem, listInsights } from '../api/insights';
import InsightDetailPanel from '../components/insights/InsightDetailPanel';
import InsightFeedList from '../components/insights/InsightFeedList';
import { ShellOutletContext } from '../components/EditTierGate';

function parseInsightId(raw: string | null): number | null {
  if (!raw) return null;
  const parsed = Number(raw);
  return Number.isFinite(parsed) ? parsed : null;
}

export default function InsightsPage() {
  const navigate = useNavigate();
  const ctx = useOutletContext<ShellOutletContext>();
  const [searchParams, setSearchParams] = useSearchParams();

  const [feed, setFeed] = useState<InsightFeedItem[]>([]);
  const [feedLoading, setFeedLoading] = useState(true);
  const [feedError, setFeedError] = useState<string | null>(null);

  const [detail, setDetail] = useState<InsightDetail | null>(null);
  const [detailLoading, setDetailLoading] = useState(false);
  const [detailError, setDetailError] = useState<string | null>(null);

  const selectedId = parseInsightId(searchParams.get('insight'));

  useEffect(() => {
    let cancelled = false;
    setFeedLoading(true);
    setFeedError(null);
    listInsights(ACTIVE_AGENT_ID, 50)
      .then(items => {
        if (!cancelled) setFeed(items);
      })
      .catch(error => {
        if (!cancelled) setFeedError(error instanceof Error ? error.message : 'Failed to load insights');
      })
      .finally(() => {
        if (!cancelled) setFeedLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, []);

  useEffect(() => {
    if (feedLoading || feed.length === 0) return;
    const exists = selectedId != null && feed.some(item => item.id === selectedId);
    if (exists) return;
    const next = new URLSearchParams(searchParams);
    next.set('insight', String(feed[0].id));
    next.delete('followup');
    setSearchParams(next, { replace: true });
  }, [feed, feedLoading, searchParams, selectedId, setSearchParams]);

  useEffect(() => {
    if (selectedId == null) {
      setDetail(null);
      setDetailError(null);
      setDetailLoading(false);
      return;
    }
    let cancelled = false;
    setDetailLoading(true);
    setDetailError(null);
    getInsightDetail(ACTIVE_AGENT_ID, selectedId)
      .then(payload => {
        if (!cancelled) setDetail(payload);
      })
      .catch(error => {
        if (!cancelled) {
          setDetail(null);
          setDetailError(error instanceof Error ? error.message : 'Failed to load insight detail');
        }
      })
      .finally(() => {
        if (!cancelled) setDetailLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, [selectedId]);

  const subtitle = useMemo(() => {
    if (ctx.agentLoading) return '正在加载当前数据代理…';
    if (ctx.agentError) return ctx.agentError;
    return ctx.agent?.description || '系统每分钟整理一批已生成的问题消息，优先展示值得继续追查的异常与趋势。';
  }, [ctx.agent, ctx.agentError, ctx.agentLoading]);

  function handleSelect(id: number) {
    const next = new URLSearchParams(searchParams);
    next.set('insight', String(id));
    next.delete('followup');
    setSearchParams(next, { replace: true });
  }

  return (
    <div
      style={{
        height: '100%',
        overflowY: 'auto',
        background: 'linear-gradient(180deg,#eff6ff 0%,#f8fafc 34%,#fff7ed 100%)',
      }}
    >
      <div style={{ padding: '26px 28px 30px', display: 'flex', flexDirection: 'column', gap: 20 }}>
        <section
          style={{
            background: 'linear-gradient(135deg,rgba(15,23,42,0.96) 0%,rgba(30,41,59,0.92) 56%,rgba(55,48,163,0.86) 100%)',
            color: '#f8fafc',
            borderRadius: 28,
            padding: '26px 28px',
            boxShadow: '0 28px 80px rgba(30,41,59,0.28)',
          }}
        >
          <div style={{ display: 'flex', justifyContent: 'space-between', gap: 20, flexWrap: 'wrap' }}>
            <div style={{ maxWidth: 820 }}>
              <div style={{ fontSize: '0.78rem', letterSpacing: '0.16em', textTransform: 'uppercase', color: '#c7d2fe', fontWeight: 800 }}>
                Insight Feed
              </div>
              <h1 style={{ margin: '12px 0 0', fontSize: '2.2rem', lineHeight: 1.05, letterSpacing: '-0.03em' }}>
                先看问题流，再决定下一步要问什么
              </h1>
              <p style={{ margin: '16px 0 0', maxWidth: 760, color: '#cbd5e1', fontSize: '0.98rem', lineHeight: 1.75 }}>
                {subtitle}
              </p>
            </div>

            <div style={{ display: 'flex', gap: 10, alignItems: 'flex-start', flexWrap: 'wrap' }}>
              <button type="button" onClick={() => navigate('/chat')} style={actionButton}>
                打开 Chat
              </button>
              <button type="button" onClick={() => navigate('/workspace')} style={secondaryActionButton}>
                Workspace
              </button>
            </div>
          </div>
        </section>

        <section
          style={{
            display: 'flex',
            gap: 20,
            alignItems: 'flex-start',
            flexWrap: 'wrap',
          }}
        >
          <div style={{ flex: '0 0 360px', minWidth: 320, maxWidth: 420 }}>
            <div style={{ marginBottom: 12, padding: '0 6px' }}>
              <div style={{ fontSize: '0.78rem', letterSpacing: '0.14em', textTransform: 'uppercase', color: '#64748b', fontWeight: 800 }}>
                最近问题流
              </div>
              <div style={{ marginTop: 6, fontSize: '0.9rem', color: '#475569', lineHeight: 1.65 }}>
                首页只展示已经生成好的问题消息，点击后查看静态结论和证据快照。
              </div>
            </div>
            <InsightFeedList
              items={feed}
              loading={feedLoading}
              error={feedError}
              selectedId={selectedId}
              onSelect={handleSelect}
            />
          </div>

          <div style={{ flex: '1 1 620px', minWidth: 340 }}>
            <InsightDetailPanel
              detail={detail}
              loading={detailLoading}
              error={detailError}
            />
          </div>
        </section>
      </div>
    </div>
  );
}

const actionButton: React.CSSProperties = {
  border: '1px solid rgba(224,231,255,0.35)',
  background: '#ffffff',
  color: '#1e1b4b',
  borderRadius: 999,
  padding: '12px 18px',
  fontSize: '0.9rem',
  fontWeight: 700,
  cursor: 'pointer',
  boxShadow: '0 12px 30px rgba(15,23,42,0.14)',
};

const secondaryActionButton: React.CSSProperties = {
  border: '1px solid rgba(255,255,255,0.22)',
  background: 'rgba(255,255,255,0.10)',
  color: '#f8fafc',
  borderRadius: 999,
  padding: '12px 18px',
  fontSize: '0.9rem',
  fontWeight: 700,
  cursor: 'pointer',
  backdropFilter: 'blur(10px)',
};
