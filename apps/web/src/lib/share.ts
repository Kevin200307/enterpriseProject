export const trackingUrl = (tn: string) => `${window.location.origin}/track/${tn}`

/** Native share sheet where available (phones), clipboard otherwise. Returns how it was shared. */
export async function shareLink(
  url: string,
  title = 'Track my SwiftDrop parcel',
): Promise<'shared' | 'copied' | 'failed'> {
  try {
    if (navigator.share) {
      await navigator.share({ title, url })
      return 'shared'
    }
    await navigator.clipboard.writeText(url)
    return 'copied'
  } catch {
    // user dismissed the share sheet, or clipboard access was denied
    return 'failed'
  }
}

export async function copyText(text: string): Promise<boolean> {
  try {
    await navigator.clipboard.writeText(text)
    return true
  } catch {
    return false
  }
}
