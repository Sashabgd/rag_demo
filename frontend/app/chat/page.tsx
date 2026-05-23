'use client';

import { useEffect, useRef, useState } from 'react';
import { flushSync } from 'react-dom';
import ReactMarkdown from 'react-markdown';
import remarkGfm from 'remark-gfm';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Card, CardHeader } from '@/components/ui/card';
import { Badge } from '@/components/ui/badge';
import { Send, Search } from 'lucide-react';
import { flushSseBuffer, parseSseBlocks, type SsePayload } from '@/lib/sse';

interface ToolCallInfo {
  name: string;
  query: string;
}

interface ToolResultItem {
  content: string;
  source: string;
  score: number;
  chunk_id?: number;
}

interface Message {
  role: 'user' | 'assistant';
  content: string;
  toolCalls?: ToolCallInfo[];
  toolResults?: ToolResultItem[];
}

export default function ChatPage() {
  const [messages, setMessages] = useState<Message[]>([]);
  const [input, setInput] = useState('');
  const [streaming, setStreaming] = useState(false);
  const [expandedTools, setExpandedTools] = useState<Set<number>>(new Set());
  const abortRef = useRef<AbortController | null>(null);
  const assistantIdxRef = useRef(-1);

  useEffect(() => {
    return () => abortRef.current?.abort();
  }, []);

  const applyEvent = (payload: SsePayload) => {
    const idx = assistantIdxRef.current;
    const { type, data } = payload;

    if (type === 'token' && data) {
      flushSync(() => {
        setMessages((prev) => {
          const i = idx >= 0 ? idx : prev.length - 1;
          if (i < 0 || !prev[i]) return prev;
          const copy = [...prev];
          copy[i] = { ...copy[i], content: copy[i].content + data };
          return copy;
        });
      });
      return;
    }

    if (type === 'tool_call') {
      try {
        const tc = JSON.parse(data) as ToolCallInfo;
        setMessages((prev) => {
          const i = idx >= 0 ? idx : prev.length - 1;
          const copy = [...prev];
          copy[i] = {
            ...copy[i],
            toolCalls: [...(copy[i].toolCalls || []), tc],
          };
          return copy;
        });
      } catch {
        /* ignore */
      }
      return;
    }

    if (type === 'tool_result') {
      try {
        const tr = JSON.parse(data) as { data: ToolResultItem[] };
        setMessages((prev) => {
          const i = idx >= 0 ? idx : prev.length - 1;
          const copy = [...prev];
          copy[i] = { ...copy[i], toolResults: tr.data || [] };
          return copy;
        });
      } catch {
        /* ignore */
      }
      return;
    }

    if (type === 'error') {
      setMessages((prev) => {
        const i = idx >= 0 ? idx : prev.length - 1;
        const copy = [...prev];
        copy[i] = { ...copy[i], content: data || 'Greška' };
        return copy;
      });
    }

    // type === 'done' — stream finished normally
  };

  const sendMessage = async () => {
    if (!input.trim() || streaming) return;
    const userMsg = input.trim();
    setInput('');

    abortRef.current?.abort();
    const controller = new AbortController();
    abortRef.current = controller;

    assistantIdxRef.current = -1;
    setMessages((prev) => {
      assistantIdxRef.current = prev.length + 1;
      return [
        ...prev,
        { role: 'user', content: userMsg },
        { role: 'assistant', content: '', toolCalls: [], toolResults: [] },
      ];
    });
    setStreaming(true);

    try {
      const response = await fetch('/api/chat', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          Accept: 'text/event-stream',
        },
        body: JSON.stringify({ message: userMsg }),
        signal: controller.signal,
        cache: 'no-store',
      });

      if (!response.ok) {
        throw new Error(`Chat failed (${response.status})`);
      }

      const reader = response.body?.getReader();
      const decoder = new TextDecoder();
      if (!reader) throw new Error('No stream');

      let buffer = '';

      const drainBuffer = (final: boolean) => {
        if (final) {
          const tailEvents = flushSseBuffer(buffer);
          buffer = '';
          tailEvents.forEach(applyEvent);
          return;
        }
        const { events, rest } = parseSseBlocks(buffer);
        buffer = rest;
        events.forEach(applyEvent);
      };

      while (true) {
        const { done, value } = await reader.read();

        if (value) {
          buffer += decoder.decode(value, { stream: true });
          drainBuffer(false);
        }

        if (done) {
          buffer += decoder.decode();
          drainBuffer(true);
          break;
        }
      }
    } catch (e) {
      if ((e as Error).name === 'AbortError') {
        return;
      }
      setMessages((prev) => {
        const i = assistantIdxRef.current >= 0 ? assistantIdxRef.current : prev.length - 1;
        const copy = [...prev];
        if (copy[i]) {
          copy[i] = {
            ...copy[i],
            content: `Greška: ${e instanceof Error ? e.message : 'Unknown'}`,
          };
        }
        return copy;
      });
    } finally {
      setStreaming(false);
    }
  };

  return (
    <div className="mx-auto flex h-[calc(100vh-4rem)] max-w-3xl flex-col gap-4">
      <div>
        <h1 className="text-2xl font-bold">Agentic RAG Chat</h1>
        <p className="text-muted-foreground">
          Gemini 2.5 Flash poziva <code className="text-xs">search_documents</code> tool
        </p>
      </div>

      <div className="flex-1 space-y-4 overflow-y-auto rounded-lg border border-border p-4">
        {messages.length === 0 && (
          <p className="text-center text-muted-foreground text-sm">
            Postavi pitanje o sadržaju uploadovanog dokumenta...
          </p>
        )}
        {messages.map((msg, i) => (
          <div
            key={i}
            className={`flex ${msg.role === 'user' ? 'justify-end' : 'justify-start'}`}
          >
            <div
              className={`max-w-[85%] rounded-lg px-4 py-2 text-sm ${
                msg.role === 'user'
                  ? 'bg-primary text-primary-foreground'
                  : 'bg-muted'
              }`}
            >
              {msg.role === 'assistant' && msg.toolCalls && msg.toolCalls.length > 0 && (
                <div className="mb-2 space-y-1">
                  {msg.toolCalls.map((tc, j) => (
                    <Card key={j} className="border-amber-200 bg-amber-50">
                      <CardHeader className="py-2 px-3">
                        <div className="flex items-center gap-2 text-xs font-medium text-amber-800">
                          <Search className="h-3 w-3" />
                          {tc.name}(query=&quot;{tc.query}&quot;)
                        </div>
                      </CardHeader>
                    </Card>
                  ))}
                </div>
              )}

              {msg.role === 'assistant' && msg.toolResults && msg.toolResults.length > 0 && (
                <div className="mb-2">
                  <button
                    type="button"
                    className="text-xs text-primary underline"
                    onClick={() =>
                      setExpandedTools((s) => {
                        const n = new Set(s);
                        n.has(i) ? n.delete(i) : n.add(i);
                        return n;
                      })
                    }
                  >
                    {expandedTools.has(i) ? 'Sakrij' : 'Prikaži'} chunkove (
                    {msg.toolResults!.length})
                  </button>
                  {expandedTools.has(i) && (
                    <table className="mt-1 w-full text-xs">
                      <thead>
                        <tr className="text-muted-foreground">
                          <th className="text-left">Source</th>
                          <th className="text-left">Score</th>
                        </tr>
                      </thead>
                      <tbody>
                        {msg.toolResults.map((r, j) => (
                          <tr key={j} className="border-t border-border/50">
                            <td className="py-1 pr-2">{r.source}</td>
                            <td>
                              <Badge variant="secondary">{r.score?.toFixed(3)}</Badge>
                            </td>
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  )}
                </div>
              )}

              {msg.role === 'assistant' ? (
                <ReactMarkdown remarkPlugins={[remarkGfm]} className="prose prose-sm max-w-none">
                  {msg.content}
                </ReactMarkdown>
              ) : (
                msg.content
              )}
            </div>
          </div>
        ))}
      </div>

      <div className="flex gap-2">
        <Input
          placeholder="Pitaj nešto o dokumentu..."
          value={input}
          onChange={(e) => setInput(e.target.value)}
          onKeyDown={(e) => e.key === 'Enter' && !e.shiftKey && sendMessage()}
          disabled={streaming}
        />
        <Button onClick={sendMessage} disabled={streaming || !input.trim()}>
          <Send className="h-4 w-4" />
        </Button>
      </div>
    </div>
  );
}
