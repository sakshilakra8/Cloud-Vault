import { Link, useLocation } from 'react-router-dom'
import { CloudUpload, FilePlus2, FolderPlus, ShieldCheck, Sparkles } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Badge } from '@/components/ui/badge'

const pageCopy: Record<string, { title: string; description: string }> = {
  '/': { title: 'My Files', description: 'A quiet place for everything you want to keep.' },
  '/shared': { title: 'Shared with me', description: 'Files and folders shared with your account.' },
  '/recent': { title: 'Recent', description: 'Pick up where you left off.' },
  '/trash': { title: 'Trash', description: 'Deleted files stay here until you remove them forever.' },
  '/storage': { title: 'Storage', description: 'Your encrypted storage, at a glance.' },
  '/activity': { title: 'Activity', description: 'A timeline of what has happened in your vault.' },
  '/settings': { title: 'Settings', description: 'Manage your account and preferences.' },
}

export function WorkspacePage() {
  const path = useLocation().pathname
  const page = pageCopy[path] ?? pageCopy['/']
  return <div>
    <div className="flex flex-wrap items-start justify-between gap-4"><div><div className="mb-2 flex items-center gap-2"><h1 className="text-2xl font-semibold tracking-tight">{page.title}</h1>{path === '/' && <Badge variant="secondary">Encrypted</Badge>}</div><p className="text-sm text-muted-foreground">{page.description}</p></div>{path === '/' && <div className="flex gap-2"><Button variant="outline"><FolderPlus size={16} />New folder</Button><Button><CloudUpload size={16} />Upload files</Button></div>}</div>
    {path === '/' ? <section className="mt-8 flex min-h-[440px] flex-col items-center justify-center rounded-2xl border border-dashed bg-card/50 px-6 py-12 text-center">
      <div className="relative mb-6 grid h-20 w-20 place-items-center rounded-[28px] bg-primary/10 text-primary"><div className="absolute -right-2 -top-2 grid h-8 w-8 place-items-center rounded-xl bg-card text-amber-500 shadow-soft"><Sparkles size={16} /></div><FilePlus2 size={34} strokeWidth={1.5} /></div>
      <h2 className="text-lg font-semibold">Your vault is ready</h2><p className="mt-2 max-w-sm text-sm leading-6 text-muted-foreground">Upload your first file and it will be encrypted before it leaves your device’s connection.</p>
      <div className="mt-6 flex flex-col gap-2 sm:flex-row"><Button><CloudUpload size={16} />Choose files</Button><Button variant="outline"><FolderPlus size={16} />Create a folder</Button></div>
      <div className="mt-9 flex items-center gap-2 rounded-full bg-muted px-3 py-1.5 text-xs text-muted-foreground"><ShieldCheck size={14} className="text-emerald-600" />Encrypted storage is active</div>
    </section> : <section className="mt-8 grid min-h-[380px] place-items-center rounded-2xl border bg-card"><div className="max-w-sm px-6 text-center"><div className="mx-auto mb-4 grid h-14 w-14 place-items-center rounded-2xl bg-muted text-muted-foreground"><FilePlus2 size={23} /></div><h2 className="font-semibold">Nothing here yet</h2><p className="mt-2 text-sm text-muted-foreground">Items will appear here when they’re available.</p><Link to="/" className="mt-4 inline-flex rounded text-sm font-medium text-primary hover:underline focus-visible:ring-2 focus-visible:ring-ring">Back to My Files</Link></div></section>}
  </div>
}

export function NotFoundPage() {
  return <main className="grid min-h-screen place-items-center px-6"><div className="text-center"><p className="text-sm font-semibold text-primary">404</p><h1 className="mt-2 text-3xl font-semibold tracking-tight">Page not found</h1><p className="mt-2 text-muted-foreground">We couldn’t find the page you’re looking for.</p><Link to="/" className="mt-6 inline-flex rounded-md bg-primary px-4 py-2 text-sm font-medium text-primary-foreground focus-visible:ring-2 focus-visible:ring-ring">Go to your files</Link></div></main>
}
