import { useState } from 'react'
import { Button } from '@/components/ui/button'
import { Input } from '@/components/ui/input'
import { Label } from '@/components/ui/label'
import { Checkbox } from '@/components/ui/checkbox'
import { Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle, DialogTrigger } from '@/components/ui/dialog'
import { Sheet, SheetContent, SheetTrigger } from '@/components/ui/sheet'
import { DropdownMenu, DropdownMenuContent, DropdownMenuItem, DropdownMenuTrigger } from '@/components/ui/dropdown-menu'
import { ContextMenu, ContextMenuContent, ContextMenuItem, ContextMenuTrigger } from '@/components/ui/context-menu'
import { Tooltip, TooltipContent, TooltipProvider, TooltipTrigger } from '@/components/ui/tooltip'
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs'
import { Badge } from '@/components/ui/badge'
import { Progress } from '@/components/ui/progress'
import { Skeleton } from '@/components/ui/skeleton'
import { Command as CommandBox, CommandInput, CommandItem, CommandList } from '@/components/ui/command'
import { toast } from 'sonner'

function Block({ title, children }: { title: string; children: React.ReactNode }) { return <section className="rounded-xl border bg-card p-5"><h2 className="mb-4 text-sm font-semibold">{title}</h2>{children}</section> }
export function StyleguidePage() {
  const [checked, setChecked] = useState<boolean | 'indeterminate'>(false)
  return <main className="mx-auto max-w-6xl px-5 py-10"><p className="text-sm font-medium text-primary">INTERNAL</p><h1 className="mt-1 text-3xl font-semibold">Design system</h1><p className="mt-2 text-muted-foreground">Component states, keyboard interactions, and theme tokens.</p>
    <div className="mt-8 grid gap-4 md:grid-cols-2">
      <Block title="Buttons"><div className="flex flex-wrap gap-2">{(['default', 'secondary', 'outline', 'ghost', 'destructive', 'link'] as const).map(v => <Button key={v} variant={v}>{v}</Button>)}<Button disabled>Disabled</Button><Button size="icon" aria-label="icon button">+</Button></div></Block>
      <Block title="Form controls"><div className="grid max-w-sm gap-3"><div><Label htmlFor="guide-input">Email</Label><Input id="guide-input" placeholder="you@example.com" /></div><div className="flex items-center gap-2"><Checkbox id="guide-check" checked={checked} onCheckedChange={setChecked} /><Label htmlFor="guide-check">Remember this device</Label></div><Input aria-invalid="true" value="Invalid value" readOnly /><p className="text-xs text-destructive" role="alert">Inline validation example</p></div></Block>
      <Block title="Dialog and sheet"><div className="flex gap-2"><Dialog><DialogTrigger asChild><Button variant="outline">Open dialog</Button></DialogTrigger><DialogContent><DialogHeader><DialogTitle>Dialog title</DialogTitle><DialogDescription>Dialogs trap focus and close with Escape.</DialogDescription></DialogHeader><Button>Continue</Button></DialogContent></Dialog><Sheet><SheetTrigger asChild><Button variant="outline">Open sheet</Button></SheetTrigger><SheetContent><h2 className="font-semibold">Sheet panel</h2><p className="mt-2 text-sm text-muted-foreground">Responsive side panel content.</p></SheetContent></Sheet></div></Block>
      <Block title="Menus and tooltip"><div className="flex gap-2"><DropdownMenu><DropdownMenuTrigger asChild><Button variant="outline">Dropdown</Button></DropdownMenuTrigger><DropdownMenuContent><DropdownMenuItem>Open</DropdownMenuItem><DropdownMenuItem>Rename</DropdownMenuItem></DropdownMenuContent></DropdownMenu><ContextMenu><ContextMenuTrigger className="rounded-md border px-3 py-2 text-sm">Right click here</ContextMenuTrigger><ContextMenuContent><ContextMenuItem>Open</ContextMenuItem><ContextMenuItem>Details</ContextMenuItem></ContextMenuContent></ContextMenu><TooltipProvider><Tooltip><TooltipTrigger asChild><Button variant="ghost">Hover or focus</Button></TooltipTrigger><TooltipContent>Helpful context</TooltipContent></Tooltip></TooltipProvider></div></Block>
      <Block title="Tabs"><Tabs defaultValue="files"><TabsList><TabsTrigger value="files">Files</TabsTrigger><TabsTrigger value="activity">Activity</TabsTrigger></TabsList><TabsContent value="files">Your files appear here.</TabsContent><TabsContent value="activity">Recent activity appears here.</TabsContent></Tabs></Block>
      <Block title="Badges, progress and skeleton"><div className="flex flex-wrap items-center gap-3"><Badge>Default</Badge><Badge variant="secondary">Secondary</Badge><Badge variant="outline">Outline</Badge><Badge variant="destructive">Error</Badge></div><Progress className="mt-5" value={62} aria-label="62 percent complete" /><Skeleton className="mt-5 h-10 w-3/4" /></Block>
      <Block title="Command menu"><CommandBox className="max-w-sm rounded-lg border"><CommandInput placeholder="Try searching…" /><CommandList><CommandItem>My Files</CommandItem><CommandItem>Settings</CommandItem></CommandList></CommandBox></Block>
      <Block title="Toast"><Button variant="outline" onClick={() => toast.success('Changes saved', { description: 'Your preferences are up to date.' })}>Show success toast</Button></Block>
    </div>
  </main>
}
