import { useEffect, useState } from 'react'
import { NavLink, Outlet, useNavigate } from 'react-router-dom'
import { Activity, ChevronLeft, ChevronRight, Cloud, FileClock, Files, HardDrive, Menu, Moon, Search, Settings, Share2, Sun, Trash2, UserRound } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Sheet, SheetContent, SheetTrigger } from '@/components/ui/sheet'
import { DropdownMenu, DropdownMenuContent, DropdownMenuItem, DropdownMenuSeparator, DropdownMenuTrigger } from '@/components/ui/dropdown-menu'
import { Dialog, DialogContent } from '@/components/ui/dialog'
import { Command as CommandBox, CommandEmpty, CommandGroup, CommandInput, CommandItem, CommandList } from '@/components/ui/command'
import { useAuth } from '@/components/auth-provider'
import { useTheme } from '@/components/theme-provider'
import { cn } from '@/lib/utils'

const links = [
  { label: 'My Files', to: '/', icon: Files }, { label: 'Shared', to: '/shared', icon: Share2 },
  { label: 'Recent', to: '/recent', icon: FileClock }, { label: 'Trash', to: '/trash', icon: Trash2 },
]
const lowerLinks = [{ label: 'Storage', to: '/storage', icon: HardDrive }, { label: 'Activity', to: '/activity', icon: Activity }, { label: 'Settings', to: '/settings', icon: Settings }]

function SidebarContent({ collapsed = false, onNavigate }: { collapsed?: boolean; onNavigate?: () => void }) {
  const navigate = useNavigate()
  return <div className="flex h-full flex-col">
    <button onClick={() => { navigate('/'); onNavigate?.() }} className={cn('mb-8 flex items-center gap-3 rounded-lg text-left focus-visible:ring-2 focus-visible:ring-ring', collapsed ? 'justify-center' : 'px-2')} aria-label="ShardVault home">
      <span className="grid h-9 w-9 shrink-0 place-items-center rounded-xl bg-primary text-primary-foreground"><Cloud size={19} strokeWidth={2.3} /></span>
      {!collapsed && <span className="text-base font-semibold tracking-tight">Shard<span className="text-primary">Vault</span></span>}
    </button>
    {!collapsed && <p className="mb-2 px-3 text-[11px] font-semibold uppercase tracking-[.12em] text-muted-foreground">Workspace</p>}
    <nav aria-label="Main navigation" className="space-y-1">
      {links.map(({ label, to, icon: Icon }) => <NavLink key={to} to={to} end={to === '/'} onClick={onNavigate} title={collapsed ? label : undefined} className={({ isActive }) => cn('group flex h-10 items-center gap-3 rounded-lg px-3 text-sm transition-colors hover:bg-accent hover:text-accent-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring', collapsed && 'justify-center px-0', isActive && 'bg-accent font-medium text-accent-foreground')}><Icon size={18} strokeWidth={1.8} /><span className={collapsed ? 'sr-only' : ''}>{label}</span></NavLink>)}
    </nav>
    <div className="my-6 border-t" />
    {!collapsed && <p className="mb-2 px-3 text-[11px] font-semibold uppercase tracking-[.12em] text-muted-foreground">Manage</p>}
    <nav aria-label="Account navigation" className="space-y-1">
      {lowerLinks.map(({ label, to, icon: Icon }) => <NavLink key={to} to={to} onClick={onNavigate} title={collapsed ? label : undefined} className={({ isActive }) => cn('group flex h-10 items-center gap-3 rounded-lg px-3 text-sm transition-colors hover:bg-accent hover:text-accent-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring', collapsed && 'justify-center px-0', isActive && 'bg-accent font-medium text-accent-foreground')}><Icon size={18} strokeWidth={1.8} /><span className={collapsed ? 'sr-only' : ''}>{label}</span></NavLink>)}
    </nav>
    <div className="mt-auto rounded-xl border bg-card p-3">
      {!collapsed && <><div className="mb-2 flex items-center justify-between text-xs"><span className="font-medium">Storage</span><span className="text-muted-foreground">0% used</span></div><div className="h-1.5 rounded-full bg-muted"><div className="h-full w-0 rounded-full bg-primary" /></div><p className="mt-2 text-[11px] text-muted-foreground">Your encrypted space</p></>}
      {collapsed && <HardDrive className="mx-auto h-4 w-4 text-muted-foreground" />}
    </div>
  </div>
}

export function AppShell() {
  const [collapsed, setCollapsed] = useState(false)
  const [searchOpen, setSearchOpen] = useState(false)
  const { user, signOut } = useAuth()
  const { resolvedTheme, setTheme } = useTheme()
  const navigate = useNavigate()
  useEffect(() => {
    const onKey = (event: KeyboardEvent) => { if ((event.metaKey || event.ctrlKey) && event.key.toLowerCase() === 'k') { event.preventDefault(); setSearchOpen(true) } }
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [])
  const SearchContent = <CommandBox><CommandInput placeholder="Search your vault…" aria-label="Search your vault" /><CommandList><CommandEmpty>No matching files.</CommandEmpty><CommandGroup heading="Navigate">{[...links, ...lowerLinks].map(({ label, to, icon: Icon }) => <CommandItem key={to} onSelect={() => { setSearchOpen(false); navigate(to) }}><Icon className="mr-2 h-4 w-4" />{label}</CommandItem>)}</CommandGroup></CommandList></CommandBox>
  return <div className="min-h-screen bg-background">
    <aside className={cn('fixed inset-y-0 left-0 z-20 hidden border-r bg-card px-4 py-5 transition-[width] duration-200 lg:flex lg:flex-col', collapsed ? 'w-[76px]' : 'w-[252px]')}><SidebarContent collapsed={collapsed} /><Button variant="outline" size="icon" aria-label={collapsed ? 'Expand sidebar' : 'Collapse sidebar'} onClick={() => setCollapsed(!collapsed)} className="absolute -right-3 top-8 z-30 h-7 w-7 rounded-full bg-card shadow-sm">{collapsed ? <ChevronRight size={14} /> : <ChevronLeft size={14} />}</Button></aside>
    <div className={cn('min-h-screen transition-[padding] duration-200', collapsed ? 'lg:pl-[76px]' : 'lg:pl-[252px]')}>
      <header className="sticky top-0 z-10 flex h-[68px] items-center justify-between gap-4 border-b bg-background/90 px-4 backdrop-blur-md sm:px-6 lg:px-8">
        <div className="flex items-center gap-3">
          <Sheet><SheetTrigger asChild><Button variant="ghost" size="icon" className="lg:hidden" aria-label="Open navigation"><Menu size={20} /></Button></SheetTrigger><SheetContent side="left" className="p-5"><SidebarContent onNavigate={() => document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }))} /></SheetContent></Sheet>
          <Button variant="outline" onClick={() => setSearchOpen(true)} className="h-10 w-[min(42vw,360px)] justify-start gap-2 text-muted-foreground shadow-none sm:w-80"><Search size={16} /><span className="truncate">Search files and folders</span><kbd className="ml-auto hidden rounded border bg-muted px-1.5 py-0.5 text-[10px] sm:inline">⌘ K</kbd></Button>
        </div>
        <div className="flex items-center gap-1.5">
          <Button variant="ghost" size="icon" aria-label={`Switch to ${resolvedTheme === 'dark' ? 'light' : 'dark'} theme`} onClick={() => setTheme(resolvedTheme === 'dark' ? 'light' : 'dark')}>{resolvedTheme === 'dark' ? <Sun size={18} /> : <Moon size={18} />}</Button>
          <DropdownMenu><DropdownMenuTrigger asChild><Button variant="ghost" className="h-10 gap-2 px-2 sm:px-3" aria-label="Open user menu"><span className="grid h-8 w-8 place-items-center rounded-full bg-primary/10 text-primary"><UserRound size={16} /></span><span className="hidden max-w-36 truncate text-sm sm:block">{user?.email}</span></Button></DropdownMenuTrigger><DropdownMenuContent align="end"><DropdownMenuItem onSelect={() => navigate('/settings')}><Settings className="mr-2 h-4 w-4" />Settings</DropdownMenuItem><DropdownMenuSeparator /><DropdownMenuItem onSelect={async () => { await signOut(); navigate('/login', { replace: true }) }}>Sign out</DropdownMenuItem></DropdownMenuContent></DropdownMenu>
        </div>
      </header>
      <main id="main-content" className="mx-auto max-w-[1440px] px-4 py-8 sm:px-6 lg:px-10"><Outlet /></main>
    </div>
    <Dialog open={searchOpen} onOpenChange={setSearchOpen}><DialogContent className="top-[25%] translate-y-0 p-0 sm:max-w-xl">{SearchContent}</DialogContent></Dialog>
    <span className="sr-only" aria-live="polite">Press Control or Command and K to open search</span>
  </div>
}
