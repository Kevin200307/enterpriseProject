export function LoadingScreen() {
  return (
    <div
      className="flex min-h-[12rem] flex-1 items-center justify-center bg-background px-6 py-12"
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
