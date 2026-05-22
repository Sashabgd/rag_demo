'use client';

import { useEffect, useRef, useState } from 'react';
import ReactMarkdown from 'react-markdown';
import remarkGfm from 'remark-gfm';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Badge } from '@/components/ui/badge';
import { Send, Search } from 'lucide-react';

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

  useEffect(() => {
    return () => abortRef.current?.abort();
  }, []);

  const sendMessage = async () => {
    if (!input.trim() || streaming) return;
    const userMsg = input.trim();
    setInput('');

    // Prekini prethodni SSE ako je još aktivan (sprečava Broken pipe na backendu)
    abortRef.current?.abort();
    abortRef.current = new AbortController();

    let assistantIdx = -1;
    setMessages((prev) => {
      assistantIdx = prev.length + 1;
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
        signal: abortRef.current.signal,
      });

      if (!response.ok) {
        throw new Error(`Chat failed (${response.status})`);
      }

      const reader = response.body?.getReader();
      const decoder = new TextDecoder();
      if (!reader) throw new Error('No stream');

      let buffer = '';

      while (true) {
        const { done, value } = await reader.read();
        if (done) break;
        buffer += decoder.decode(value, { stream: true });

        const lines = buffer.split('\n');
        buffer = lines.pop() || '';

        for (const line of lines) {
          if (!line.startsWith('data:')) continue;
          const raw = line.slice(5).trim();
          if (!raw || raw === '[DONE]') continue;

          try {
            const parsed = JSON.parse(raw) as { type: string; data: string };
            const { type, data } = parsed;

            if (type === 'token' && data) {
              setMessages((prev) => {
                const idx = assistantIdx >= 0 ? assistantIdx : prev.length - 1;
                const copy = [...prev];
                const msg = { ...copy[idx] };
                msg.content += data;
                copy[idx] = msg;
                return copy;
              });
            } else if (type === 'tool_call') {
              const tc = JSON.parse(data) as ToolCallInfo;
              setMessages((prev) => {
                const idx = assistantIdx >= 0 ? assistantIdx : prev.length - 1;
                const copy = [...prev];
                const msg = { ...copy[idx] };
                msg.toolCalls = [...(msg.toolCalls || []), tc];
                copy[idx] = msg;
                return copy;
              });
            } else if (type === 'tool_result') {
              const tr = JSON.parse(data) as { data: ToolResultItem[] };
              setMessages((prev) => {
                const idx = assistantIdx >= 0 ? assistantIdx : prev.length - 1;
                const copy = [...prev];
                const msg = { ...copy[idx] };
                msg.toolResults = tr.data || [];
                copy[idx] = msg;
                return copy;
              });
            } else if (type === 'error') {
              setMessages((prev) => {
                const idx = assistantIdx >= 0 ? assistantIdx : prev.length - 1;
                const copy = [...prev];
                copy[idx] = { ...copy[idx], content: data || 'Greška' };
                return copy;
              });
            }
          } catch {
            // skip malformed SSE lines
          }
        }
      }
    } catch (e) {
      if ((e as Error).name === 'AbortError') {
        return;
      }
      setMessages((prev) => {
        const idx = assistantIdx >= 0 ? assistantIdx : prev.length - 1;
        const copy = [...prev];
        if (copy[idx]) {
          copy[idx] = {
            ...copy[idx],
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
          onKeyDown={(e) => e.key === 'Enter' && sendMessage()}
          disabled={streaming}
        />
        <Button onClick={sendMessage} disabled={streaming || !input.trim()}>
          <Send className="h-4 w-4" />
        </Button>
      </div>
    </div>
  );
}
