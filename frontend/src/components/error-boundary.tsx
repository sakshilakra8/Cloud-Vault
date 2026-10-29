import { Component, type ErrorInfo, type ReactNode } from 'react'
import { Button } from '@/components/ui/button'

type Props = { children: ReactNode }
type State = { hasError: boolean }
export class ErrorBoundary extends Component<Props, State> {
  state: State = { hasError: false }
  static getDerivedStateFromError(): State { return { hasError: true } }
  componentDidCatch(error: Error, info: ErrorInfo) { console.error('UI rendering error', error.name, info.componentStack) }
  render() {
    if (this.state.hasError) return <main className="grid min-h-screen place-items-center px-6"><div className="max-w-sm text-center"><h1 className="text-xl font-semibold">Something went wrong</h1><p className="mt-2 text-sm text-muted-foreground">The page hit an unexpected problem.</p><Button className="mt-5" onClick={() => window.location.assign('/')}>Return to your files</Button></div></main>
    return this.props.children
  }
}
