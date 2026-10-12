export function LoadingScreen() {
  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center bg-background px-6 py-12"
      role="status"
      aria-label="Loading SwiftDrop"
    >
      <img
        src="/delivery-lorry-loader.svg"
        alt=""
        aria-hidden
        className="h-auto w-full max-w-[20rem]"
        width={640}
        height={320}
      />
      <span className="sr-only">Loading SwiftDrop…</span>
    </div>
  )
}
