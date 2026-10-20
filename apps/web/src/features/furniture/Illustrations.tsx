/** Furniture + chrome illustrations for the mobile UI mockup. All inline SVG so nothing loads over the wire. */

export function CoralSofa({ className }: { className?: string }) {
  return (
    <svg viewBox="0 0 420 270" className={className} aria-hidden>
      <defs>
        <linearGradient id="coralBack" x1="0" y1="0" x2="0.9" y2="1">
          <stop offset="0%" stopColor="#F79A72" />
          <stop offset="45%" stopColor="#EC6F3F" />
          <stop offset="100%" stopColor="#C8471F" />
        </linearGradient>
        <linearGradient id="coralSeat" x1="0" y1="0" x2="0.6" y2="1">
          <stop offset="0%" stopColor="#FBAE8B" />
          <stop offset="60%" stopColor="#F2825A" />
          <stop offset="100%" stopColor="#DB5C31" />
        </linearGradient>
        <filter id="sofaShadow" x="-30%" y="-30%" width="160%" height="170%">
          <feGaussianBlur stdDeviation="14" />
        </filter>
      </defs>

      <ellipse cx="215" cy="238" rx="158" ry="20" fill="#000" opacity="0.16" filter="url(#sofaShadow)" />

      {/* backrest band */}
      <path
        d="M62 176 C54 86, 142 44, 222 66 C302 88, 372 132, 364 196"
        stroke="url(#coralBack)"
        strokeWidth="64"
        strokeLinecap="round"
        fill="none"
      />
      {/* seat band */}
      <path
        d="M84 192 C80 118, 152 88, 220 110 C288 132, 344 166, 338 208"
        stroke="url(#coralSeat)"
        strokeWidth="46"
        strokeLinecap="round"
        fill="none"
      />
      {/* modular seams */}
      <g stroke="#B8441D" strokeWidth="1.6" opacity="0.38" strokeLinecap="round">
        <path d="M150 62 L140 104" />
        <path d="M250 80 L238 120" />
        <path d="M330 136 L306 164" />
      </g>
      {/* highlight */}
      <path
        d="M96 170 C94 116, 156 96, 214 114"
        stroke="#FFC9AE"
        strokeWidth="9"
        strokeLinecap="round"
        fill="none"
        opacity="0.5"
      />
    </svg>
  )
}

export function BlueSofa({ className }: { className?: string }) {
  return (
    <svg viewBox="0 0 160 120" className={className} aria-hidden>
      <defs>
        <linearGradient id="blueG" x1="0" y1="0" x2="1" y2="1">
          <stop offset="0%" stopColor="#4A43F0" />
          <stop offset="100%" stopColor="#1E18B8" />
        </linearGradient>
      </defs>
      <ellipse cx="80" cy="100" rx="56" ry="9" fill="#000" opacity="0.14" />
      <path
        d="M34 78 C28 36, 72 22, 104 34 C130 44, 138 62, 132 82"
        stroke="url(#blueG)"
        strokeWidth="34"
        strokeLinecap="round"
        fill="none"
      />
      <path
        d="M46 86 C44 58, 76 48, 104 58 C122 64, 128 76, 124 88"
        stroke="#5D57F5"
        strokeWidth="22"
        strokeLinecap="round"
        fill="none"
        opacity="0.9"
      />
    </svg>
  )
}

export function GreenChair({ className }: { className?: string }) {
  return (
    <svg viewBox="0 0 160 140" className={className} aria-hidden>
      <ellipse cx="80" cy="124" rx="44" ry="7" fill="#000" opacity="0.12" />
      {/* legs */}
      <g stroke="#E7E2D8" strokeWidth="5" strokeLinecap="round">
        <path d="M58 92 L48 122" />
        <path d="M104 92 L114 122" />
        <path d="M66 94 L70 122" />
      </g>
      {/* seat */}
      <rect x="50" y="78" width="62" height="18" rx="9" fill="#EFEBE1" />
      {/* backrest cushion */}
      <ellipse cx="86" cy="48" rx="30" ry="34" fill="#9DBBA4" />
      <ellipse cx="86" cy="48" rx="23" ry="27" fill="#A9C6B0" opacity="0.7" />
      {/* frame */}
      <path
        d="M58 80 C56 48, 66 26, 86 16"
        stroke="#E7E2D8"
        strokeWidth="5"
        strokeLinecap="round"
        fill="none"
      />
    </svg>
  )
}

export function BambooChair({ className }: { className?: string }) {
  return (
    <svg viewBox="0 0 120 120" className={className} aria-hidden>
      <ellipse cx="60" cy="104" rx="34" ry="6" fill="#000" opacity="0.12" />
      <g stroke="#C9A24B" strokeWidth="6" strokeLinecap="round" fill="none">
        <path d="M36 44 L30 100" />
        <path d="M84 44 L90 100" />
        <path d="M46 46 L44 100" />
        <path d="M74 46 L76 100" />
        <path d="M32 66 L88 66" />
        <path d="M34 82 L86 82" />
      </g>
      <rect x="26" y="32" width="68" height="14" rx="7" fill="#D9B766" />
      <rect x="30" y="24" width="60" height="12" rx="6" fill="#E3C87F" />
    </svg>
  )
}

/* ---------- phone chrome ---------- */

export function SignalIcon({ className }: { className?: string }) {
  return (
    <svg viewBox="0 0 18 12" className={className} aria-hidden fill="currentColor">
      <rect x="0" y="8" width="3" height="4" rx="1" />
      <rect x="5" y="5.5" width="3" height="6.5" rx="1" />
      <rect x="10" y="3" width="3" height="9" rx="1" />
      <rect x="15" y="0" width="3" height="12" rx="1" />
    </svg>
  )
}

export function WifiIcon({ className }: { className?: string }) {
  return (
    <svg viewBox="0 0 16 12" className={className} aria-hidden fill="currentColor">
      <path d="M8 11.2 5.6 8.6a3.4 3.4 0 0 1 4.8 0L8 11.2Z" />
      <path
        d="M3.3 6.3a6.8 6.8 0 0 1 9.4 0"
        stroke="currentColor"
        strokeWidth="1.7"
        fill="none"
        strokeLinecap="round"
      />
      <path
        d="M1 3.8a10.2 10.2 0 0 1 14 0"
        stroke="currentColor"
        strokeWidth="1.7"
        fill="none"
        strokeLinecap="round"
      />
    </svg>
  )
}

export function BatteryIcon({ level = 78, className }: { level?: number; className?: string }) {
  return (
    <div className={className}>
      <div className="relative flex h-[13px] w-[26px] items-center rounded-[4px] bg-current px-[3px]">
        <span className="text-[8px] font-semibold leading-none text-white">{level}</span>
      </div>
    </div>
  )
}
