'use client';

import { useEffect, useState } from 'react';
import axios from 'axios';
import {
  listModels,
  createModel,
  updateModel,
  deleteModel,
  type CustomModel,
  type CustomModelInput,
} from '@/lib/api';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Card, CardContent, CardHeader, CardTitle } from '@/components/ui/card';
import { Badge } from '@/components/ui/badge';
import { Server, Pencil, Trash2 } from 'lucide-react';

const EMPTY_FORM: CustomModelInput = {
  name: '',
  baseUrl: '',
  modelName: '',
  apiKey: '',
  enabled: true,
};

function extractError(e: unknown): string {
  if (axios.isAxiosError(e)) {
    return (e.response?.data as { error?: string } | undefined)?.error || e.message;
  }
  return e instanceof Error ? e.message : 'Nepoznata greška';
}

export default function ModelsPage() {
  const [models, setModels] = useState<CustomModel[]>([]);
  const [loading, setLoading] = useState(true);
  const [form, setForm] = useState<CustomModelInput>(EMPTY_FORM);
  const [editingId, setEditingId] = useState<number | null>(null);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const load = () => {
    setLoading(true);
    listModels()
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

  const startEdit = (model: CustomModel) => {
    setEditingId(model.id);
    setForm({
      name: model.name,
      baseUrl: model.baseUrl,
      modelName: model.modelName,
      apiKey: '',
      enabled: model.enabled,
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
      const payload: CustomModelInput = {
        name: form.name.trim(),
        baseUrl: form.baseUrl.trim(),
        modelName: form.modelName.trim(),
        enabled: form.enabled,
      };
      if (form.apiKey && form.apiKey.trim()) {
        payload.apiKey = form.apiKey.trim();
      }
      if (editingId == null) {
        await createModel(payload);
      } else {
        await updateModel(editingId, payload);
      }
      resetForm();
      load();
    } catch (e) {
      setError(extractError(e));
    } finally {
      setSaving(false);
    }
  };

  const remove = async (model: CustomModel) => {
    if (!window.confirm(`Obrisati model "${model.name}"?`)) return;
    setError(null);
    try {
      await deleteModel(model.id);
      if (editingId === model.id) resetForm();
      load();
    } catch (e) {
      setError(extractError(e));
    }
  };

  const toggleEnabled = async (model: CustomModel) => {
    setError(null);
    try {
      await updateModel(model.id, {
        name: model.name,
        baseUrl: model.baseUrl,
        modelName: model.modelName,
        enabled: !model.enabled,
      });
      load();
    } catch (e) {
      setError(extractError(e));
    }
  };

  const editingModel = editingId != null ? models.find((m) => m.id === editingId) : undefined;

  return (
    <div className="mx-auto max-w-3xl space-y-6">
      <div>
        <h1 className="text-2xl font-bold">Modeli</h1>
        <p className="text-muted-foreground">
          Custom OpenAI-kompatibilni modeli (npr. vLLM server). Koriste se na Chat strani uz Gemini.
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
            {editingId == null ? 'Dodaj model' : `Izmeni model: ${editingModel?.name ?? ''}`}
          </CardTitle>
        </CardHeader>
        <CardContent className="space-y-3">
          <div className="grid gap-3 sm:grid-cols-2">
            <label className="space-y-1 text-sm">
              <span className="text-muted-foreground">Naziv (prikazni)</span>
              <Input
                placeholder="npr. Llama 3.1 (vLLM)"
                value={form.name}
                onChange={(e) => setForm({ ...form, name: e.target.value })}
                disabled={saving}
              />
            </label>
            <label className="space-y-1 text-sm">
              <span className="text-muted-foreground">Model name (šalje se serveru)</span>
              <Input
                placeholder="npr. meta-llama/Llama-3.1-8B-Instruct"
                value={form.modelName}
                onChange={(e) => setForm({ ...form, modelName: e.target.value })}
                disabled={saving}
              />
            </label>
          </div>
          <label className="block space-y-1 text-sm">
            <span className="text-muted-foreground">Base URL (OpenAI putanja, npr. .../v1)</span>
            <Input
              placeholder="http://localhost:8000/v1"
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
          <label className="flex items-center gap-2 text-sm">
            <input
              type="checkbox"
              checked={form.enabled ?? true}
              onChange={(e) => setForm({ ...form, enabled: e.target.checked })}
              disabled={saving}
              className="rounded"
            />
            Aktivan (dostupan u Chat-u)
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
      {!loading && models.length === 0 && (
        <p className="text-muted-foreground">Još nema custom modela. Dodaj prvi iznad.</p>
      )}

      <div className="space-y-3">
        {models.map((model) => (
          <Card key={model.id}>
            <CardHeader className="flex flex-row items-start justify-between gap-3 py-4">
              <div className="flex items-start gap-3">
                <Server className="mt-0.5 h-5 w-5 text-primary" />
                <div className="space-y-1">
                  <CardTitle className="text-base">{model.name}</CardTitle>
                  <p className="text-xs text-muted-foreground">
                    <span className="font-mono">{model.modelName}</span>
                    <br />
                    <span className="font-mono">{model.baseUrl}</span>
                  </p>
                  <div className="flex flex-wrap gap-2 pt-1">
                    <Badge variant={model.enabled ? 'success' : 'secondary'}>
                      {model.enabled ? 'Aktivan' : 'Neaktivan'}
                    </Badge>
                    {model.hasApiKey && <Badge variant="outline">API ključ</Badge>}
                  </div>
                </div>
              </div>
              <div className="flex shrink-0 gap-1">
                <Button variant="ghost" size="sm" onClick={() => toggleEnabled(model)}>
                  {model.enabled ? 'Isključi' : 'Uključi'}
                </Button>
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
