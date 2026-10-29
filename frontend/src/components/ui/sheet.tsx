import * as DialogPrimitive from '@radix-ui/react-dialog'
import { cn } from '@/lib/utils'
export const Sheet = DialogPrimitive.Root
export const SheetTrigger = DialogPrimitive.Trigger
export const SheetClose = DialogPrimitive.Close
export function SheetContent({ side = 'right', className, children, ...props }: React.ComponentProps<typeof DialogPrimitive.Content> & { side?: 'left' | 'right' }) { return <DialogPrimitive.Portal><DialogPrimitive.Overlay className="fixed inset-0 z-50 bg-black/50" /><DialogPrimitive.Content className={cn('fixed z-50 h-full w-[min(19rem,85vw)] bg-background p-5 shadow-xl focus:outline-none', side === 'left' ? 'left-0 top-0 border-r' : 'right-0 top-0 border-l', className)} {...props}>{children}</DialogPrimitive.Content></DialogPrimitive.Portal> }
