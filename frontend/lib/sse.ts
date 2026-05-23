export interface SsePayload {
  type: string;
  data: string;
}

/**
 * Parse SSE blocks separated by blank lines. Returns incomplete tail in `rest`.
 */
export function parseSseBlocks(buffer: string): { events: SsePayload[]; rest: string } {
  const events: SsePayload[] = [];
  const normalized = buffer.replace(/\r\n/g, '\n');
  const blocks = normalized.split('\n\n');
  const rest = blocks.pop() ?? '';

  for (const block of blocks) {
    if (!block.trim()) continue;

    let eventName = 'message';
    const dataLines: string[] = [];

    for (const line of block.split('\n')) {
      if (line.startsWith('event:')) {
        eventName = line.slice(6).trim();
      } else if (line.startsWith('data:')) {
        dataLines.push(line.slice(5).trimStart());
      }
    }

    if (dataLines.length === 0) continue;

    const raw = dataLines.join('\n');
    try {
      const parsed = JSON.parse(raw) as SsePayload;
      events.push(parsed);
    } catch {
      events.push({ type: eventName, data: raw });
    }
  }

  return { events, rest };
}

/** Flush trailing block that may not end with \\n\\n */
export function flushSseBuffer(buffer: string): SsePayload[] {
  const trimmed = buffer.trim();
  if (!trimmed) return [];
  const { events } = parseSseBlocks(trimmed + '\n\n');
  return events;
}
