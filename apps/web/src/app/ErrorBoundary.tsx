import { Component, type ErrorInfo, type ReactNode } from 'react'
import { ErrorState } from '@/components/common/ErrorState'

export class ErrorBoundary extends Component<{ children: ReactNode }, { error: unknown }> {
  state = { error: null as unknown }
  static getDerivedStateFromError(error: unknown) {
    return { error }
  }
  componentDidCatch(error: unknown, info: ErrorInfo) {
    console.error('UI error', error, info.componentStack)
  }
  render() {
    if (this.state.error) {
      return (
        <div className="mx-auto max-w-lg p-8">
          <ErrorState error={this.state.error} onRetry={() => location.reload()} />
        </div>
      )
    }
    return this.props.children
  }
}
