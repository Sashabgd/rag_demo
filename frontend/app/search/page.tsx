'use client';

import { useState } from 'react';
import { searchDocuments, type SearchResultItem } from '@/lib/api';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Badge } from '@/components/ui/badge';
import { Search } from 'lucide-react';

export default function SearchPage() {
  const [query, setQuery] = useState('');
  const [rerank, setRerank] = useState(true);
  const [results, setResults] = useState<SearchResultItem[]>([]);
  const [rerankType, setRerankType] = useState('');
  const [loading, setLoading] = useState(false);

  const handleSearch = async () => {
    if (!query.trim()) return;
    setLoading(true);
    try {
      const res = await searchDocuments(query, 5, rerank);
      setResults(res.results);
      setRerankType(res.rerankType);
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className="mx-auto max-w-3xl space-y-6">
      <div>
        <h1 className="text-2xl font-bold">Vektor pretraga</h1>
        <p className="text-muted-foreground">
          Direktna KNN pretraga u Redis-u + opcioni BGE reranker
        </p>
      </div>

      <div className="flex gap-2">
        <Input
          placeholder="Unesi upit..."
          value={query}
          onChange={(e) => setQuery(e.target.value)}
          onKeyDown={(e) => e.key === 'Enter' && handleSearch()}
        />
        <Button onClick={handleSearch} disabled={loading}>
          <Search className="h-4 w-4" />
          {loading ? '...' : 'Search'}
        </Button>
      </div>

      <label className="flex items-center gap-2 text-sm">
        <input
          type="checkbox"
          checked={rerank}
          onChange={(e) => setRerank(e.target.checked)}
          className="rounded"
        />
        Reranker uključen (BGE v2-m3)
      </label>

      {rerankType && (
        <p className="text-xs text-muted-foreground">
          Rerank tip: <Badge variant="secondary">{rerankType}</Badge>
        </p>
      )}

      <div className="space-y-3">
        {results.map((r, i) => (
          <Card key={`${r.chunkId}-${i}`}>
            <CardHeader className="py-3">
              <div className="flex items-center justify-between">
                <CardTitle className="text-sm font-medium">{r.source}</CardTitle>
                <Badge>score: {r.score.toFixed(4)}</Badge>
              </div>
              <p className="text-xs text-muted-foreground">
                doc:{r.documentId} · chunk:{r.chunkId} · [{r.startIndex}–{r.endIndex}]
              </p>
            </CardHeader>
            <CardContent>
              <p className="text-sm whitespace-pre-wrap line-clamp-4">{r.content}</p>
            </CardContent>
          </Card>
        ))}
      </div>
    </div>
  );
}
