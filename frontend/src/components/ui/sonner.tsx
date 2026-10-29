import { Toaster as SonnerToaster, type ToasterProps } from 'sonner'
export function Toaster(props: ToasterProps) { return <SonnerToaster theme={props.theme ?? 'system'} richColors closeButton position="top-right" {...props} /> }
