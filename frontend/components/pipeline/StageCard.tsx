'use client';

import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { Badge } from '@/components/ui/badge';
import { Progress } from '@/components/ui/progress';
import { cn } from '@/lib/utils';
import { CheckCircle2, Circle, Loader2 } from 'lucide-react';

export type StageState = 'pending' | 'active' | 'done';

interface StageCardProps {
  step: number;
  title: string;
  description: string;
  state: StageState;
  children?: React.ReactNode;
}

export function StageCard({ step, title, description, state, children }: StageCardProps) {
  const Icon =
    state === 'done' ? CheckCircle2 : state === 'active' ? Loader2 : Circle;

  return (
    <Card
      className={cn(
        'transition-all duration-500',
        state === 'active' && 'ring-2 ring-primary shadow-md',
        state === 'done' && 'border-green-200 bg-green-50/30',
        state === 'pending' && 'opacity-60',
      )}
    >
      <CardHeader className="pb-2">
        <div className="flex items-center justify-between">
          <div className="flex items-center gap-3">
            <div
              className={cn(
                'flex h-8 w-8 items-center justify-center rounded-full text-sm font-bold',
                state === 'done' && 'bg-green-100 text-green-700',
                state === 'active' && 'bg-primary text-primary-foreground',
                state === 'pending' && 'bg-muted text-muted-foreground',
              )}
            >
              {step}
            </div>
            <div>
              <CardTitle className="text-base">{title}</CardTitle>
              <CardDescription>{description}</CardDescription>
            </div>
          </div>
          <Icon
            className={cn(
              'h-5 w-5',
              state === 'done' && 'text-green-600',
              state === 'active' && 'animate-spin text-primary',
              state === 'pending' && 'text-muted-foreground',
            )}
          />
        </div>
        {state === 'active' && <Progress value={66} className="mt-3 h-1" />}
      </CardHeader>
      {children && <CardContent>{children}</CardContent>}
    </Card>
  );
}

export function statusBadge(status: string) {
  const map: Record<string, 'default' | 'success' | 'pending' | 'secondary'> = {
    UPLOADED: 'pending',
    PARSED: 'pending',
    CHUNKED: 'pending',
    EMBEDDED: 'success',
    FAILED: 'secondary',
    EMBEDDED_CHUNK: 'success',
    PENDING: 'pending',
  };
  return <Badge variant={map[status] || 'secondary'}>{status}</Badge>;
}
