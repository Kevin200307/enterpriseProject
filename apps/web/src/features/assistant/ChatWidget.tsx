import { MessageCircle } from 'lucide-react'
import { useState } from 'react'
import { Button } from '@/components/ui/button'
import { ChatPanel } from './ChatPanel'

const OFF_KEY = 'sd-assistant-off'
const wasDisabled = () => {
  try {
    return sessionStorage.getItem(OFF_KEY) === '1'
  } catch {
    return false
  }
}

/** Floating launcher + panel. Disappears for the rest of the session once the service reports 503. */
export function ChatWidget() {
  const [open, setOpen] = useState(false)
  const [off, setOff] = useState(wasDisabled)
  if (off) return null
  return (
    <>
      {open && (
        <ChatPanel
          onClose={() => setOpen(false)}
          onUnavailable={() => {
            try {
              sessionStorage.setItem(OFF_KEY, '1')
            } catch {
              // ignore
            }
            setOff(true)
          }}
        />
      )}
      {!open && (
        <Button
          size="lg"
          // Sits above the mobile bottom bar so it never covers the last tab. The silhouette is
          // the speech bubble itself — round but for the squared bottom-left corner, where the
          // icon's own tail sits — rather than a plain disc.
          className="fixed bottom-24 right-4 z-40 h-14 w-14 rounded-[28px_28px_28px_8px] shadow-lg md:bottom-4"
          aria-label="Open assistant"
          onClick={() => setOpen(true)}
        >
          <MessageCircle className="h-6 w-6" aria-hidden />
        </Button>
      )}
    </>
  )
}
