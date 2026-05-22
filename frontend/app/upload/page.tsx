'use client';

import { useCallback, useState } from 'react';
import { useRouter } from 'next/navigation';
import { uploadDocument } from '@/lib/api';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { Upload, FileUp } from 'lucide-react';

export default function UploadPage() {
  const router = useRouter();
  const [dragging, setDragging] = useState(false);
  const [uploading, setUploading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const handleFile = useCallback(
    async (file: File) => {
      const ext = file.name.split('.').pop()?.toLowerCase();
      if (!['pdf', 'docx', 'txt'].includes(ext || '')) {
        setError('Podržani formati: PDF, DOCX, TXT');
        return;
      }
      setError(null);
      setUploading(true);
      try {
        const doc = await uploadDocument(file);
        router.push(`/documents/${doc.id}`);
      } catch (e: unknown) {
        setError(e instanceof Error ? e.message : 'Upload failed');
      } finally {
        setUploading(false);
      }
    },
    [router],
  );

  const onDrop = useCallback(
    (e: React.DragEvent) => {
      e.preventDefault();
      setDragging(false);
      const file = e.dataTransfer.files[0];
      if (file) handleFile(file);
    },
    [handleFile],
  );

  return (
    <div className="mx-auto max-w-xl space-y-6">
      <div>
        <h1 className="text-2xl font-bold">Upload dokumenta</h1>
        <p className="text-muted-foreground">Korak 1 pipeline-a — prevuci fajl ili klikni za izbor</p>
      </div>

      <Card
        className={`cursor-pointer border-2 border-dashed transition-colors ${
          dragging ? 'border-primary bg-accent' : 'border-border hover:border-primary/50'
        }`}
        onDragOver={(e) => {
          e.preventDefault();
          setDragging(true);
        }}
        onDragLeave={() => setDragging(false)}
        onDrop={onDrop}
        onClick={() => document.getElementById('file-input')?.click()}
      >
        <CardHeader className="text-center">
          <div className="mx-auto mb-2 flex h-12 w-12 items-center justify-center rounded-full bg-accent">
            <Upload className="h-6 w-6 text-primary" />
          </div>
          <CardTitle>Prevuci dokument ovde</CardTitle>
          <CardDescription>PDF, DOCX ili TXT — max 50MB</CardDescription>
        </CardHeader>
        <CardContent className="flex justify-center pb-6">
          <Button disabled={uploading} onClick={(e) => e.stopPropagation()}>
            <label className="flex cursor-pointer items-center gap-2">
              <FileUp className="h-4 w-4" />
              {uploading ? 'Uploadujem...' : 'Izaberi fajl'}
              <input
                id="file-input"
                type="file"
                accept=".pdf,.docx,.txt"
                className="hidden"
                onChange={(e) => {
                  const file = e.target.files?.[0];
                  if (file) handleFile(file);
                }}
              />
            </label>
          </Button>
        </CardContent>
      </Card>

      {error && <p className="text-sm text-red-600">{error}</p>}
    </div>
  );
}
