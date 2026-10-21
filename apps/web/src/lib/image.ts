export const PROOF_MAX_BYTES = 5 * 1024 * 1024
const ALLOWED = ['image/jpeg', 'image/png']

/** Client-side pre-check for quick feedback. The server re-validates by magic bytes. */
export function validateProof(file: File): string | null {
  if (!ALLOWED.includes(file.type)) return 'Please choose a JPEG or PNG photo.'
  if (file.size > PROOF_MAX_BYTES) return 'That photo is larger than 5 MB.'
  return null
}

/** Downscales big phone photos to save mobile data. Falls back to the original if anything is unavailable. */
export async function prepareImage(file: File, maxDim = 1600): Promise<File> {
  try {
    if (typeof createImageBitmap === 'undefined') return file
    const bmp = await createImageBitmap(file)
    const scale = Math.min(1, maxDim / Math.max(bmp.width, bmp.height))
    if (scale === 1) return file
    const canvas = document.createElement('canvas')
    canvas.width = Math.round(bmp.width * scale)
    canvas.height = Math.round(bmp.height * scale)
    canvas.getContext('2d')?.drawImage(bmp, 0, 0, canvas.width, canvas.height)
    const blob = await new Promise<Blob | null>((res) => canvas.toBlob(res, 'image/jpeg', 0.85))
    return blob
      ? new File([blob], file.name.replace(/\.\w+$/, '') + '.jpg', { type: 'image/jpeg' })
      : file
  } catch {
    return file
  }
}
