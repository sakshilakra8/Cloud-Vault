import * as Menu from '@radix-ui/react-dropdown-menu'
import { Check } from 'lucide-react'
import { cn } from '@/lib/utils'
export const DropdownMenu = Menu.Root
export const DropdownMenuTrigger = Menu.Trigger
export const DropdownMenuGroup = Menu.Group
export function DropdownMenuContent({ className, sideOffset = 6, ...props }: React.ComponentProps<typeof Menu.Content>) { return <Menu.Portal><Menu.Content sideOffset={sideOffset} className={cn('z-50 min-w-40 overflow-hidden rounded-lg border bg-popover p-1 text-popover-foreground shadow-md outline-none', className)} {...props} /></Menu.Portal> }
export function DropdownMenuItem({ className, inset, ...props }: React.ComponentProps<typeof Menu.Item> & { inset?: boolean }) { return <Menu.Item className={cn('relative flex cursor-default select-none items-center rounded-md px-2 py-2 text-sm outline-none transition-colors focus:bg-accent focus:text-accent-foreground data-[disabled]:pointer-events-none data-[disabled]:opacity-50', inset && 'pl-8', className)} {...props} /> }
export function DropdownMenuCheckboxItem({ className, children, checked, ...props }: React.ComponentProps<typeof Menu.CheckboxItem>) { return <Menu.CheckboxItem checked={checked} className={cn('relative flex cursor-default select-none items-center rounded-md py-2 pl-8 pr-2 text-sm outline-none focus:bg-accent', className)} {...props}><span className="absolute left-2"><Menu.ItemIndicator><Check className="h-4 w-4" /></Menu.ItemIndicator></span>{children}</Menu.CheckboxItem> }
export function DropdownMenuSeparator({ className, ...props }: React.ComponentProps<typeof Menu.Separator>) { return <Menu.Separator className={cn('-mx-1 my-1 h-px bg-muted', className)} {...props} /> }
