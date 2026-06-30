import { ChatEvent, ChatRequest, CurrentSession, currentSession, stream } from './chat';
import { TurnEntry, turns } from './sessions';

export interface ChatTransport {
  currentSession(agentId: string, sessionKey?: string): Promise<CurrentSession>;
  stream(agentId: string, req: ChatRequest): AsyncGenerator<ChatEvent>;
  turns(agentId: string, sessionKey: string): Promise<TurnEntry[]>;
}

export const defaultChatTransport: ChatTransport = {
  currentSession,
  stream,
  turns,
};
