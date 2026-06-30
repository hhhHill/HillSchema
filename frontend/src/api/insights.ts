import { getToken } from './auth';

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
}

export interface InsightEvidence {
  evidenceKey: string;
  label: string;
  valueText: string | null;
  detailText: string | null;
  snapshotJson: string | null;
}

export interface InsightDetail {
  id: number;
  sourceId: string;
  kind: string;
  status: string;
  title: string;
  summary: string;
  conclusion: string | null;
  evidenceSummary: string | null;
  observedAt: string;
  createdAt: string;
  windowStart: string;
  windowEnd: string;
  metricKey: string;
  metricLabel: string | null;
  currentValue: number;
  baselineValue: number;
  dimensionName: string | null;
  dimensionValue: string | null;
  followUpQuestions: string[];
  evidence: InsightEvidence[];
}

function authHeaders(): Record<string, string> {
  const token = getToken();
  return token ? { Authorization: `Bearer ${token}` } : {};
}

export async function listInsights(agentId: string, limit = 50): Promise<InsightFeedItem[]> {
  const res = await fetch(
    `/api/agents/${encodeURIComponent(agentId)}/insights?limit=${encodeURIComponent(String(limit))}`,
    { headers: authHeaders() },
  );
  if (!res.ok) throw new Error(`Failed to load insights: ${res.status}`);
  return res.json();
}

export async function getInsightDetail(agentId: string, insightId: number): Promise<InsightDetail> {
  const res = await fetch(
    `/api/agents/${encodeURIComponent(agentId)}/insights/${encodeURIComponent(String(insightId))}`,
    { headers: authHeaders() },
  );
  if (!res.ok) throw new Error(`Failed to load insight detail: ${res.status}`);
  return res.json();
}
