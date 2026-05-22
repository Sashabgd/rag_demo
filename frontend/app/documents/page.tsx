'use client';

import { useEffect, useState } from 'react';
import Link from 'next/link';
import { listDocuments, type DocumentSummary } from '@/lib/api';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { statusBadge } from '@/components/pipeline/StageCard';
import { Button } from '@/components/ui/button';
import { FileText } from 'lucide-react';

export default function DocumentsPage() {
  const [docs, setDocs] = useState<DocumentSummary[]>([]);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    listDocuments()
      .then(setDocs)
      .finally(() => setLoading(false));
  }, []);

  return (
    <div className="mx-auto max-w-3xl space-y-6">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold">Dokumenti</h1>
          <p className="text-muted-foreground">Svi uploadovani dokumenti i njihov status</p>
        </div>
        <Link href="/upload">
          <Button>+ Upload</Button>
        </Link>
      </div>

      {loading && <p className="text-muted-foreground">Učitavam...</p>}
      {!loading && docs.length === 0 && (
        <p className="text-muted-foreground">Nema dokumenata. Uploaduj prvi fajl.</p>
      )}

      <div className="space-y-3">
        {docs.map((doc) => (
          <Link key={doc.id} href={`/documents/${doc.id}`}>
            <Card className="cursor-pointer transition-shadow hover:shadow-md">
              <CardHeader className="flex flex-row items-center justify-between py-4">
                <div className="flex items-center gap-3">
                  <FileText className="h-5 w-5 text-primary" />
                  <div>
                    <CardTitle className="text-base">{doc.name}</CardTitle>
                    <p className="text-xs text-muted-foreground">
                      {doc.fileType.toUpperCase()} · {(doc.fileSize / 1024).toFixed(1)} KB ·{' '}
                      {doc.chunkCount} chunkova
                    </p>
                  </div>
                </div>
                {statusBadge(doc.status)}
              </CardHeader>
            </Card>
          </Link>
        ))}
      </div>
    </div>
  );
}
