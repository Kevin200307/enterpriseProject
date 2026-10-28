const runtime = typeof window !== 'undefined' ? window.__SWIFTDROP__ : undefined

export const env = {
  apiBaseUrl: (
    runtime?.apiBaseUrl ??
    import.meta.env.VITE_API_BASE_URL ??
    'http://localhost:8081'
  ).replace(/\/$/, ''),
  wsUrl: runtime?.wsUrl ?? import.meta.env.VITE_WS_URL ?? 'http://localhost:8081/ws',
  assistantEnabled: runtime?.assistantEnabled ?? import.meta.env.VITE_ASSISTANT_ENABLED !== 'false',
}
