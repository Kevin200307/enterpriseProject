/**
 * Brand marks. The navy of the source artwork is drawn as `currentColor` so the logo can sit on
 * the black rail (text-white) as well as on light chrome (text-sidebar).
 *
 * The lime is the brand accent, but at 64% lightness it all but disappears on white — so on a
 * light surface the marks take a deepened version of the same hue, which keeps the brand colour
 * reading as green while clearing 4:1 against the page. Pass `onLight` wherever the mark is not
 * on a dark backdrop.
 */

const LIME = '#DDF86C'
/** Same hue, dark enough to be legible on white. */
const LIME_ON_LIGHT = '#6F8F12'

interface BrandProps {
  className?: string
  /** The mark is sitting on a light surface, so the lime needs deepening to stay visible. */
  onLight?: boolean
}

export function LogoMark({ className, onLight }: BrandProps) {
  const lime = onLight ? LIME_ON_LIGHT : LIME
  return (
    <svg
      viewBox="-3 14 224 224"
      className={`${className ?? ''} ${onLight ? 'dark:[--logo-lime:#DDF86C]' : ''}`}
      style={{ '--logo-lime': lime } as React.CSSProperties}
      role="img"
      aria-label="SwiftDrop"
    >
      <path
        fill="currentColor"
        d="M29 112h22a5 5 0 0 1 0 10H29a5 5 0 0 1 0-10Zm-12 25h34a5 5 0 0 1 0 10H17a5 5 0 0 1 0-10Z"
      />
      <path fill="var(--logo-lime)" fillRule="evenodd" d="M65 81 130 44 202 83 130 122Z M97 63 163 99 173 93 107 57Z" />
      <path fill="currentColor" d="M65 91 123 125V204L65 169Z" />
      <path
        fill="var(--logo-lime)"
        fillRule="evenodd"
        d="M135 125 202 88V169L135 207Z M146 140H169V129L193 149 169 169V158H146Z"
      />
    </svg>
  )
}

/** Icon plus wordmark. */
export function LogoLockup({ className, onLight }: BrandProps) {
  const lime = onLight ? LIME_ON_LIGHT : LIME
  return (
    <svg
      viewBox="0 0 824 250"
      // Left-anchored slice: narrowing the element crops the wordmark away and leaves the icon,
      // so expand/collapse is one continuous width change instead of swapping two images.
      preserveAspectRatio="xMinYMid slice"
      className={`${className ?? ''} ${onLight ? 'dark:[--logo-lime:#DDF86C]' : ''}`}
      style={{ '--logo-lime': lime } as React.CSSProperties}
      role="img"
      aria-label="SwiftDrop"
    >
      <path
        fill="currentColor"
        d="M29 112h22a5 5 0 0 1 0 10H29a5 5 0 0 1 0-10Zm-12 25h34a5 5 0 0 1 0 10H17a5 5 0 0 1 0-10Z"
      />
      <path fill="var(--logo-lime)" fillRule="evenodd" d="M65 81 130 44 202 83 130 122Z M97 63 163 99 173 93 107 57Z" />
      <path fill="currentColor" d="M65 91 123 125V204L65 169Z" />
      <path
        fill="var(--logo-lime)"
        fillRule="evenodd"
        d="M135 125 202 88V169L135 207Z M146 140H169V129L193 149 169 169V158H146Z"
      />
      <g>
        <path
          fill="currentColor"
          transform="translate(255 166) scale(0.116 -0.116)"
          d="M607 507C606 658 505 741 321 741C146 741 45 659 45 517C45 404 102 349 250 321L352 301C452 282 489 254 489 195C489 134 433 97 342 97C240 97 183 140 178 218H32C41 62 148 -23 334 -23C522 -23 633 64 633 213C633 328 575 388 440 414L326 436C219 457 188 478 188 531C188 586 236 621 314 621C409 621 462 581 467 507Z"
        />
        <path
          fill="currentColor"
          transform="translate(330.872 166) scale(0.116 -0.116)"
          d="M613 0 766 540H621L539 162L458 540H318L236 162L150 540H5L157 0H301L387 381L468 0Z"
        />
        <path
          fill="currentColor"
          transform="translate(419.62 166) scale(0.116 -0.116)"
          d="M207 540H67V0H207ZM207 729H67V604H207Z"
        />
        <path
          fill="currentColor"
          transform="translate(450.368 166) scale(0.116 -0.116)"
          d="M313 529H230V582C230 610 242 624 268 624C281 624 294 623 308 621V726C278 728 249 729 228 729C135 729 90 685 90 594V529H14V436H90V0H230V436H313Z"
        />
        <path
          fill="currentColor"
          transform="translate(487.496 166) scale(0.116 -0.116)"
          d="M301 529H223V674H83V529H14V436H83V104C83 19 128 -23 220 -23C252 -23 275 -20 301 -12V86C287 84 280 83 269 83C232 83 223 94 223 142V436H301Z"
        />
        <path
          fill="var(--logo-lime)"
          transform="translate(524.624 166) scale(0.116 -0.116)"
          d="M77 0H362C473 0 543 26 593 85C650 153 681 251 681 365C681 478 650 576 593 645C543 704 474 729 362 729H77ZM227 125V604H362C475 604 531 525 531 364C531 204 475 125 362 125Z"
        />
        <path
          fill="var(--logo-lime)"
          transform="translate(606.876 166) scale(0.116 -0.116)"
          d="M63 540V0H203V287C203 369 244 410 326 410C341 410 351 409 370 406V548C362 549 359 549 353 549C289 549 234 507 203 434V540Z"
        />
        <path
          fill="var(--logo-lime)"
          transform="translate(650.5 166) scale(0.116 -0.116)"
          d="M301 549C136 549 35 440 35 263C35 85 136 -23 302 -23C467 -23 569 85 569 259C569 442 470 549 301 549ZM302 436C379 436 429 367 429 261C429 160 377 90 302 90C226 90 175 159 175 263C175 367 226 436 302 436Z"
        />
        <path
          fill="var(--logo-lime)"
          transform="translate(719.876 166) scale(0.116 -0.116)"
          d="M198 540H58V-218H198V64C232 4 280 -24 348 -24C478 -24 574 98 574 262C574 338 552 414 516 464C480 515 413 549 348 549C280 549 232 520 198 460ZM316 432C387 432 434 364 434 260C434 161 386 93 316 93C245 93 198 161 198 262C198 364 245 432 316 432Z"
        />
      </g>
    </svg>
  )
}

/** The mark on its own dark tile — for light chrome, where bare lime would wash out. */
export function LogoTile({ className }: { className?: string }) {
  return (
    <span
      className={
        'flex h-8 w-8 shrink-0 items-center justify-center rounded-xl bg-sidebar text-white ' +
        (className ?? '')
      }
    >
      <LogoMark className="h-5 w-5" />
    </span>
  )
}
