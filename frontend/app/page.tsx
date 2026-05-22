import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { ArrowRight, Database, FileText, Layers, Search, Sparkles } from 'lucide-react';
import Link from 'next/link';
import { Button } from '@/components/ui/button';

const steps = [
  {
    num: 1,
    title: 'Upload dokumenta',
    desc: 'Korisnik uploaduje PDF, DOCX ili TXT fajl.',
    icon: FileText,
  },
  {
    num: 2,
    title: 'Parsiranje',
    desc: 'Java API ekstrahuje čist tekst (PDFBox / Apache POI).',
    icon: Sparkles,
  },
  {
    num: 3,
    title: 'Snimanje u bazu',
    desc: 'Tekst + metapodaci se čuvaju u PostgreSQL.',
    icon: Database,
  },
  {
    num: 4,
    title: 'Chunkovanje',
    desc: 'StaticSplitter deli tekst u preklapajuće delove (1200/200).',
    icon: Layers,
  },
  {
    num: 5,
    title: 'Embedovanje u Redis',
    desc: 'Python servis embeduje chunkove (e5-small) i čuva u Redis Stack.',
    icon: Database,
  },
];

export default function HomePage() {
  return (
    <div className="mx-auto max-w-3xl space-y-8">
      <div>
        <h1 className="text-3xl font-bold tracking-tight">RAG Pipeline Demo</h1>
        <p className="mt-2 text-muted-foreground">
          Demonstracija kompletnog RAG pipeline-a: ingest, vektor pretraga i agentic RAG sa
          Gemini 2.5 Flash.
        </p>
      </div>

      <div className="space-y-3">
        {steps.map((step, i) => (
          <div key={step.num} className="flex items-start gap-4">
            <div className="flex flex-col items-center">
              <div className="flex h-10 w-10 items-center justify-center rounded-full bg-primary text-sm font-bold text-primary-foreground">
                {step.num}
              </div>
              {i < steps.length - 1 && (
                <div className="my-1 h-8 w-0.5 bg-border" />
              )}
            </div>
            <Card className="flex-1">
              <CardHeader className="py-4">
                <div className="flex items-center gap-2">
                  <step.icon className="h-4 w-4 text-primary" />
                  <CardTitle className="text-base">{step.title}</CardTitle>
                </div>
                <CardDescription>{step.desc}</CardDescription>
              </CardHeader>
            </Card>
          </div>
        ))}
      </div>

      <Card>
        <CardHeader>
          <CardTitle className="text-base">Demo moduli</CardTitle>
        </CardHeader>
        <CardContent className="flex flex-wrap gap-3">
          <Link href="/upload">
            <Button>
              Upload dokument
              <ArrowRight className="h-4 w-4" />
            </Button>
          </Link>
          <Link href="/search">
            <Button variant="outline">
              <Search className="h-4 w-4" />
              Vektor pretraga
            </Button>
          </Link>
          <Link href="/chat">
            <Button variant="outline">Agentic RAG chat</Button>
          </Link>
        </CardContent>
      </Card>
    </div>
  );
}
