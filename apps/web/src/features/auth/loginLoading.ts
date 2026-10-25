export const LOGIN_LOADING_MIN_MS = 800

export async function waitForLoginMinimum(startedAt: number) {
  const remaining = LOGIN_LOADING_MIN_MS - (Date.now() - startedAt)
  if (remaining > 0) await new Promise((resolve) => setTimeout(resolve, remaining))
}
