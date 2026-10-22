'use client';

import { useEffect, useState } from 'react';
import {
  listEmbeddingModels,
  createEmbeddingModel,
  updateEmbeddingModel,
  deleteEmbeddingModel,
  setActiveEmbeddingModel,
  extractError,
  type EmbeddingModel,
  type EmbeddingModelInput,
} from '@/lib/api';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Badge } from '@/components/ui/badge';
import { Cpu, Layers, Pencil, Trash2 } from 'lucide-react';

const EMPTY_FORM: EmbeddingModelInput = {
  name: '',
  baseUrl: '',
  modelName: '',
  apiKey: '',
};

export default function EmbeddingsPage() {
  const [models, setModels] = useState<EmbeddingModel[]>([]);
  const [loading, setLoading] = useState(true);
  const [form, setForm] = useState<EmbeddingModelInput>(EMPTY_FORM);
  const [editingId, setEditingId] = useState<number | null>(null);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const load = () => {
    setLoading(true);
    listEmbeddingModels()
      .then(setModels)
      .catch((e) => setError(extractError(e)))
      .finally(() => setLoading(false));
  };

  useEffect(() => {
    load();
  }, []);

  const resetForm = () => {
    setForm(EMPTY_FORM);
    setEditingId(null);
    setError(null);
  };

  const startEdit = (model: EmbeddingModel) => {
    setEditingId(model.id);
    setForm({
      name: model.name,
      baseUrl: model.baseUrl,
      modelName: model.modelName,
      apiKey: '',
    });
    setError(null);
    if (typeof window !== 'undefined') {
      window.scrollTo({ top: 0, behavior: 'smooth' });
    }
  };

  const submit = async () => {
    if (!form.name.trim() || !form.baseUrl.trim() || !form.modelName.trim()) {
      setError('Naziv, Base URL i Model name su obavezni');
      return;
    }
    setSaving(true);
    setError(null);
    try {
      const payload: EmbeddingModelInput = {
        name: form.name.trim(),
        baseUrl: form.baseUrl.trim(),
        modelName: form.modelName.trim(),
      };
      if (form.apiKey && form.apiKey.trim()) {
        payload.apiKey = form.apiKey.trim();
      }
      if (editingId == null) {
        await createEmbeddingModel(payload);
      } else {
        await updateEmbeddingModel(editingId, payload);
      }
      resetForm();
      load();
    } catch (e) {
      setError(extractError(e));
    } finally {
      setSaving(false);
    }
  };

  const remove = async (model: EmbeddingModel) => {
    const note = model.active ? ' Aktivan je, pa se vraća na lokalni e5-small.' : '';
    if (!window.confirm(`Obrisati embedding model "${model.name}"?${note}`)) return;
    setError(null);
    try {
      await deleteEmbeddingModel(model.id);
      if (editingId === model.id) resetForm();
      load();
    } catch (e) {
      setError(extractError(e));
    }
  };

  const activate = async (id: number | null) => {
    setError(null);
    try {
      await setActiveEmbeddingModel(id);
      load();
    } catch (e) {
      setError(extractError(e));
    }
  };

  const editingModel = editingId != null ? models.find((m) => m.id === editingId) : undefined;
  const localActive = !models.some((m) => m.active);

  return (
    <div className="mx-auto max-w-3xl space-y-6">
      <div>
        <h1 className="text-2xl font-bold">Embedding modeli</h1>
        <p className="text-muted-foreground">
          Lokalni e5-small ili remote OpenAI-kompatibilni embedding server (<code>/v1/embeddings</code>).
          Aktivni model se koristi za nove uploadove, Search i Chat.
        </p>
        <p className="mt-2 text-sm text-muted-foreground">
          Svaki model ima svoj Redis indeks. Dokumenti embedovani drugim modelom se ne vide u pretrazi dok ih
          ponovo ne uploaduješ.
        </p>
      </div>

      {error && (
        <div className="rounded-md border border-red-200 bg-red-50 px-4 py-2 text-sm text-red-700">
          {error}
        </div>
      )}

      <Card>
        <CardHeader className="py-4">
          <CardTitle className="text-base">
            {editingId == null
              ? 'Dodaj remote embedding model'
              : `Izmeni model: ${editingModel?.name ?? ''}`}
          </CardTitle>
        </CardHeader>
        <CardContent className="space-y-3">
          <div className="grid gap-3 sm:grid-cols-2">
            <label className="space-y-1 text-sm">
              <span className="text-muted-foreground">Naziv (prikazni)</span>
              <Input
                placeholder="npr. OpenAI small"
                value={form.name}
                onChange={(e) => setForm({ ...form, name: e.target.value })}
                disabled={saving}
              />
            </label>
            <label className="space-y-1 text-sm">
              <span className="text-muted-foreground">Model name (šalje se serveru)</span>
              <Input
                placeholder="npr. text-embedding-3-small"
                value={form.modelName}
                onChange={(e) => setForm({ ...form, modelName: e.target.value })}
                disabled={saving}
              />
            </label>
          </div>
          <label className="block space-y-1 text-sm">
            <span className="text-muted-foreground">Base URL (OpenAI putanja, npr. .../v1)</span>
            <Input
              placeholder="https://api.openai.com/v1"
              value={form.baseUrl}
              onChange={(e) => setForm({ ...form, baseUrl: e.target.value })}
              disabled={saving}
            />
          </label>
          <label className="block space-y-1 text-sm">
            <span className="text-muted-foreground">API ključ (opciono)</span>
            <Input
              type="password"
              placeholder={
                editingModel?.hasApiKey
                  ? '•••••••• (ostavi prazno da zadržiš postojeći)'
                  : 'Bearer token, ako server zahteva'
              }
              value={form.apiKey ?? ''}
              onChange={(e) => setForm({ ...form, apiKey: e.target.value })}
              disabled={saving}
            />
          </label>
          <div className="flex gap-2 pt-1">
            <Button onClick={submit} disabled={saving}>
              {editingId == null ? 'Dodaj' : 'Sačuvaj izmene'}
            </Button>
            {editingId != null && (
              <Button variant="outline" onClick={resetForm} disabled={saving}>
                Otkaži
              </Button>
            )}
          </div>
        </CardContent>
      </Card>

      {loading && <p className="text-muted-foreground">Učitavam...</p>}

      <div className="space-y-3">
        <Card>
          <CardHeader className="flex flex-row items-start justify-between gap-3 py-4">
            <div className="flex items-start gap-3">
              <Cpu className="mt-0.5 h-5 w-5 text-primary" />
              <div className="space-y-1">
                <CardTitle className="text-base">e5-small (lokalni)</CardTitle>
                <p className="text-xs text-muted-foreground">
                  <span className="font-mono">intfloat/multilingual-e5-small</span>
                  <br />
                  Indeks: <span className="font-mono">rag_demo</span>
                </p>
                <div className="flex flex-wrap gap-2 pt-1">
                  <Badge variant={localActive ? 'success' : 'secondary'}>
                    {localActive ? 'Aktivan' : 'Neaktivan'}
                  </Badge>
                </div>
              </div>
            </div>
            {!localActive && (
              <Button variant="ghost" size="sm" onClick={() => activate(null)}>
                Koristi
              </Button>
            )}
          </CardHeader>
        </Card>

        {models.map((model) => (
          <Card key={model.id}>
            <CardHeader className="flex flex-row items-start justify-between gap-3 py-4">
              <div className="flex items-start gap-3">
                <Layers className="mt-0.5 h-5 w-5 text-primary" />
                <div className="space-y-1">
                  <CardTitle className="text-base">{model.name}</CardTitle>
                  <p className="text-xs text-muted-foreground">
                    <span className="font-mono">{model.modelName}</span>
                    <br />
                    <span className="font-mono">{model.baseUrl}</span>
                    <br />
                    Indeks: <span className="font-mono">{model.indexName}</span>
                  </p>
                  <div className="flex flex-wrap gap-2 pt-1">
                    <Badge variant={model.active ? 'success' : 'secondary'}>
                      {model.active ? 'Aktivan' : 'Neaktivan'}
                    </Badge>
                    {model.hasApiKey && <Badge variant="outline">API ključ</Badge>}
                  </div>
                </div>
              </div>
              <div className="flex shrink-0 gap-1">
                {!model.active && (
                  <Button variant="ghost" size="sm" onClick={() => activate(model.id)}>
                    Koristi
                  </Button>
                )}
                <Button variant="ghost" size="sm" onClick={() => startEdit(model)}>
                  <Pencil className="h-4 w-4" />
                </Button>
                <Button variant="ghost" size="sm" onClick={() => remove(model)}>
                  <Trash2 className="h-4 w-4 text-red-600" />
                </Button>
              </div>
            </CardHeader>
          </Card>
        ))}
      </div>
    </div>
  );
}
