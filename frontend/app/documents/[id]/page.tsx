'use client';

import { useEffect, useState } from 'react';
import { useParams } from 'next/navigation';
import { getDocument, type DocumentDetail, type DocumentStatus } from '@/lib/api';
import { StageCard, statusBadge, type StageState } from '@/components/pipeline/StageCard';
import { Badge } from '@/components/ui/badge';

const STATUS_ORDER: DocumentStatus[] = ['UPLOADED', 'PARSED', 'CHUNKED', 'EMBEDDED'];

function stageState(current: DocumentStatus, minDone: DocumentStatus, activeAt?: DocumentStatus): StageState {
  if (current === 'FAILED') return 'pending';
  const cur = STATUS_ORDER.indexOf(current);
  const doneIdx = STATUS_ORDER.indexOf(minDone);
  const activeIdx = activeAt ? STATUS_ORDER.indexOf(activeAt) : doneIdx;
  if (cur >= doneIdx) return 'done';
  if (cur >= activeIdx || current === activeAt) return 'active';
  return 'pending';
}

export default function DocumentDetailPage() {
  const params = useParams();
  const id = Number(params.id);
  const [doc, setDoc] = useState<DocumentDetail | null>(null);

  useEffect(() => {
    if (!id) return;
    const poll = () => getDocument(id).then(setDoc);
    poll();
    const interval = setInterval(poll, 700);
    return () => clearInterval(interval);
  }, [id]);

  if (!doc) {
    return <p className="text-muted-foreground">Učitavam dokument...</p>;
  }

  const s = doc.status;

  return (
    <div className="mx-auto max-w-3xl space-y-4">
      <div>
        <h1 className="text-2xl font-bold">{doc.name}</h1>
        <p className="text-muted-foreground">
          Pipeline status: {statusBadge(s)}
        </p>
      </div>

      <StageCard
        step={1}
        title="Upload dokumenta"
        description="Fajl primljen na server"
        state="done"
      >
        <dl className="grid grid-cols-2 gap-2 text-sm">
          <dt className="text-muted-foreground">Tip</dt>
          <dd>{doc.fileType}</dd>
          <dt className="text-muted-foreground">Veličina</dt>
          <dd>{(doc.fileSize / 1024).toFixed(1)} KB</dd>
          <dt className="text-muted-foreground">Upload</dt>
          <dd>{new Date(doc.uploadedAt).toLocaleString('sr')}</dd>
        </dl>
      </StageCard>

      <StageCard
        step={2}
        title="Parsiranje"
        description="Ekstrakcija teksta iz dokumenta"
        state={stageState(s, 'PARSED', 'UPLOADED')}
      >
        {doc.textLength != null && (
          <div className="space-y-2 text-sm">
            <p>
              <strong>{doc.textLength.toLocaleString()}</strong> karaktera ekstrahovano
            </p>
            {doc.textPreview && (
              <pre className="max-h-40 overflow-auto rounded bg-muted p-3 text-xs whitespace-pre-wrap">
                {doc.textPreview}
              </pre>
            )}
          </div>
        )}
      </StageCard>

      <StageCard
        step={3}
        title="Snimanje u bazu"
        description="PostgreSQL — tekst + metapodaci"
        state={stageState(s, 'PARSED', 'UPLOADED')}
      >
        {doc.parsedAt && (
          <p className="text-sm">
            Document ID: <code className="rounded bg-muted px-1">{doc.id}</code> · sačuvano{' '}
            {new Date(doc.parsedAt).toLocaleString('sr')}
          </p>
        )}
      </StageCard>

      <StageCard
        step={4}
        title="Chunkovanje"
        description="StaticSplitter — 1200 znakova, overlap 200"
        state={stageState(s, 'CHUNKED', 'PARSED')}
      >
        {doc.chunks.length > 0 && (
          <div className="space-y-2">
            <p className="text-sm font-medium">{doc.chunks.length} chunkova</p>
            <div className="max-h-48 space-y-1 overflow-auto">
              {doc.chunks.map((c) => (
                <div
                  key={c.id}
                  className="rounded border border-border bg-muted/50 p-2 text-xs"
                >
                  <span className="font-mono text-primary">
                    [{c.startIndex}–{c.endIndex}]
                  </span>{' '}
                  {c.contentPreview}
                </div>
              ))}
            </div>
          </div>
        )}
      </StageCard>

      <StageCard
        step={5}
        title="Embedovanje u Redis"
        description="e5-small → Redis Stack index rag_demo"
        state={stageState(s, 'EMBEDDED', 'CHUNKED')}
      >
        {doc.chunks.some((c) => c.vectorStoreId) && (
          <table className="w-full text-xs">
            <thead>
              <tr className="border-b text-left text-muted-foreground">
                <th className="pb-1">Chunk ID</th>
                <th className="pb-1">Redis Key</th>
                <th className="pb-1">Status</th>
              </tr>
            </thead>
            <tbody>
              {doc.chunks.map((c) => (
                <tr key={c.id} className="border-b border-border/50">
                  <td className="py-1 font-mono">{c.id}</td>
                  <td className="py-1 font-mono truncate max-w-[200px]">
                    {c.vectorStoreId || '—'}
                  </td>
                  <td className="py-1">
                    <Badge variant={c.vectorStoreId ? 'success' : 'pending'}>
                      {c.status}
                    </Badge>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </StageCard>
    </div>
  );
}
