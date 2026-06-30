import { ChatRequest, CurrentSession, currentSession } from './chat';
import { ChatTransport } from './chatTransport';
import { getToken } from './auth';
import { turns } from './sessions';

export interface InsightChatResponse {
  reply: string;
  sessionKey: string;
}

function authHeaders(): Record<string, string> {
  const token = getToken();
  return token ? { Authorization: `Bearer ${token}` } : {};
}

export function toScopedInsightSessionKey(itemId: number, sessionKey: string): string {
  const prefix = `insight:${itemId}:`;
  return sessionKey.startsWith(prefix) ? sessionKey : `${prefix}${sessionKey}`;
}

export function fromScopedInsightSessionKey(itemId: number, sessionKey: string | null): string | null {
  if (!sessionKey) return null;
  const prefix = `insight:${itemId}:`;
  return sessionKey.startsWith(prefix) ? sessionKey.slice(prefix.length) : sessionKey;
}

export async function sendInsightChat(
  agentId: string,
  itemId: number,
  req: ChatRequest,
): Promise<InsightChatResponse> {
  const res = await fetch(`/api/agents/${encodeURIComponent(agentId)}/insights/${encodeURIComponent(String(itemId))}/chat/send`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', ...authHeaders() },
    body: JSON.stringify(req),
  });
  if (!res.ok) throw new Error(`Insight chat failed: ${res.status}`);
  return res.json();
}

async function resolveInsightSession(
  agentId: string,
  itemId: number,
  sessionKey?: string,
): Promise<CurrentSession> {
  const scopedKey = sessionKey ? toScopedInsightSessionKey(itemId, sessionKey) : undefined;
  const resolved = await currentSession(agentId, scopedKey);
  return {
    sessionKey: fromScopedInsightSessionKey(itemId, resolved.sessionKey),
    exists: resolved.exists,
  };
}

async function listInsightTurns(agentId: string, itemId: number, sessionKey: string) {
  return turns(agentId, toScopedInsightSessionKey(itemId, sessionKey));
}

export function createInsightChatTransport(itemId: number): ChatTransport {
  return {
    currentSession: (agentId, sessionKey) => resolveInsightSession(agentId, itemId, sessionKey),
    async *stream(agentId, req) {
      const reply = await sendInsightChat(agentId, itemId, req);
      if (reply.reply) {
        yield { type: 'token', data: reply.reply };
      }
      yield { type: 'done', sessionKey: reply.sessionKey };
    },
    turns: (agentId, sessionKey) => listInsightTurns(agentId, itemId, sessionKey),
  };
}
