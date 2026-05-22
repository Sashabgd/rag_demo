'use client';

import Link from 'next/link';
import { usePathname } from 'next/navigation';
import { cn } from '@/lib/utils';
import {
  Upload,
  FileText,
  Search,
  MessageSquare,
  GitBranch,
} from 'lucide-react';

const links = [
  { href: '/', label: 'Pipeline', icon: GitBranch },
  { href: '/upload', label: 'Upload', icon: Upload },
  { href: '/documents', label: 'Documents', icon: FileText },
  { href: '/search', label: 'Search', icon: Search },
  { href: '/chat', label: 'Chat', icon: MessageSquare },
];

export function Sidebar() {
  const pathname = usePathname();

  return (
    <aside className="flex w-56 flex-col border-r border-border bg-card p-4">
      <div className="mb-8">
        <h1 className="text-lg font-bold text-primary">RAG Demo</h1>
        <p className="text-xs text-muted-foreground">Prezentacija pipeline-a</p>
      </div>
      <nav className="flex flex-col gap-1">
        {links.map(({ href, label, icon: Icon }) => (
          <Link
            key={href}
            href={href}
            className={cn(
              'flex items-center gap-2 rounded-md px-3 py-2 text-sm transition-colors',
              pathname === href || (href !== '/' && pathname.startsWith(href))
                ? 'bg-accent text-accent-foreground font-medium'
                : 'text-muted-foreground hover:bg-muted hover:text-foreground',
            )}
          >
            <Icon className="h-4 w-4" />
            {label}
          </Link>
        ))}
      </nav>
    </aside>
  );
}
