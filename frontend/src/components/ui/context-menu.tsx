import * as Menu from '@radix-ui/react-context-menu'
import { cn } from '@/lib/utils'
export const ContextMenu = Menu.Root
export const ContextMenuTrigger = Menu.Trigger
export function ContextMenuContent({ className, ...props }: React.ComponentProps<typeof Menu.Content>) { return <Menu.Portal><Menu.Content className={cn('z-50 min-w-40 rounded-lg border bg-popover p-1 text-popover-foreground shadow-md outline-none', className)} {...props} /></Menu.Portal> }
export function ContextMenuItem({ className, inset, ...props }: React.ComponentProps<typeof Menu.Item> & { inset?: boolean }) { return <Menu.Item className={cn('flex cursor-default select-none items-center rounded-md px-2 py-2 text-sm outline-none focus:bg-accent', inset && 'pl-8', className)} {...props} /> }
export function ContextMenuSeparator({ className, ...props }: React.ComponentProps<typeof Menu.Separator>) { return <Menu.Separator className={cn('-mx-1 my-1 h-px bg-muted', className)} {...props} /> }
