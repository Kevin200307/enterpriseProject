import { ArrowUpRight, ChevronLeft, Heart, Menu, Share2, ShoppingBag, Sofa, Star } from 'lucide-react'
import { useState } from 'react'
import { cn } from '@/lib/cn'
import { BambooChair, BlueSofa, CoralSofa, GreenChair } from './Illustrations'
import { HomeIndicator, StatusBar } from './PhoneFrame'

const glassCircle =
  'flex h-11 w-11 items-center justify-center rounded-full bg-white/55 backdrop-blur-md shadow-sm'

/* ───────────────────────── 1. Onboarding ───────────────────────── */

function RoomScene() {
  return (
    <svg
      viewBox="0 0 320 660"
      className="absolute inset-0 h-full w-full"
      preserveAspectRatio="xMidYMid slice"
      aria-hidden
    >
      <defs>
        <linearGradient id="room" x1="0.1" y1="0" x2="0.4" y2="1">
          <stop offset="0%" stopColor="#F9E2D5" />
          <stop offset="38%" stopColor="#F0E7E1" />
          <stop offset="100%" stopColor="#E4DFDB" />
        </linearGradient>
        <filter id="blurSm" x="-40%" y="-40%" width="180%" height="180%">
          <feGaussianBlur stdDeviation="5" />
        </filter>
        <filter id="blurLg" x="-40%" y="-40%" width="180%" height="180%">
          <feGaussianBlur stdDeviation="18" />
        </filter>
        <linearGradient id="scrim" x1="0" y1="0" x2="0" y2="1">
          <stop offset="0%" stopColor="#1a1a1a" stopOpacity="0" />
          <stop offset="55%" stopColor="#1a1a1a" stopOpacity="0.18" />
          <stop offset="100%" stopColor="#1a1a1a" stopOpacity="0.42" />
        </linearGradient>
      </defs>

      <rect width="320" height="660" fill="url(#room)" />
      <ellipse cx="80" cy="130" rx="160" ry="150" fill="#FCE6D6" opacity="0.85" filter="url(#blurLg)" />

      {/* armchair */}
      <ellipse cx="168" cy="430" rx="142" ry="156" fill="#E9E3DD" filter="url(#blurSm)" />
      <ellipse cx="52" cy="486" rx="58" ry="96" fill="#F1ECE7" filter="url(#blurSm)" />
      <ellipse cx="282" cy="470" rx="54" ry="92" fill="#EDE7E1" filter="url(#blurSm)" />
      <ellipse
        cx="92"
        cy="398"
        rx="56"
        ry="44"
        fill="#F5F0EB"
        filter="url(#blurSm)"
        transform="rotate(-22 92 398)"
      />
      <ellipse cx="176" cy="580" rx="156" ry="86" fill="#F0EBE6" filter="url(#blurSm)" />

      {/* seated figure */}
      <g>
        {/* legs */}
        <path
          d="M150 420 C 176 466, 214 520, 252 566"
          stroke="#C6A57C"
          strokeWidth="46"
          strokeLinecap="round"
          fill="none"
        />
        <path
          d="M140 428 C 158 482, 186 536, 206 586"
          stroke="#BE9B72"
          strokeWidth="42"
          strokeLinecap="round"
          fill="none"
        />
        {/* torso */}
        <path
          d="M150 318 C 126 346, 122 392, 140 430 C 164 444, 196 436, 204 410 C 212 374, 196 330, 174 316 Z"
          fill="#CBAB83"
        />
        {/* arm */}
        <path
          d="M196 348 C 212 378, 206 414, 188 432"
          stroke="#C2A179"
          strokeWidth="26"
          strokeLinecap="round"
          fill="none"
        />
        {/* neck + face */}
        <rect x="150" y="294" width="20" height="26" rx="10" fill="#D7A77F" />
        <ellipse cx="158" cy="272" rx="24" ry="28" fill="#DCAE86" />
        {/* hair */}
        <path
          d="M134 272 C 130 238, 150 226, 164 228 C 184 231, 192 250, 188 274 C 186 258, 176 248, 160 248 C 146 248, 138 258, 134 272 Z"
          fill="#2F2521"
        />
        <path d="M132 268 C 128 288, 132 300, 138 304 C 132 292, 132 278, 134 266 Z" fill="#2F2521" />
        <path d="M186 268 C 192 288, 190 302, 184 306 C 190 292, 190 278, 188 266 Z" fill="#2F2521" />
      </g>

      {/* legibility scrim */}
      <rect x="0" y="330" width="320" height="330" fill="url(#scrim)" />
    </svg>
  )
}

export function OnboardingScreen() {
  return (
    <div className="relative flex h-full flex-col">
      <RoomScene />
      <StatusBar />
      <div className="relative z-10 mt-auto px-7 pb-1">
        <h1 className="text-[46px] font-bold leading-[0.95] tracking-[-0.035em] text-white">
          Live in
          <br />
          Comfort
        </h1>
        <p className="mt-4 max-w-[230px] text-[12.5px] leading-[1.55] text-white/75">
          Our furniture rental blends modern design with premium comfort for effortless living.
        </p>
        <div className="mt-7 flex items-center gap-3">
          <button
            type="button"
            aria-label="Browse furniture"
            className="flex h-[52px] w-[52px] items-center justify-center rounded-full bg-white/25 backdrop-blur-md ring-1 ring-white/30"
          >
            <Sofa className="h-5 w-5 text-white" aria-hidden />
          </button>
          <button
            type="button"
            className="flex h-[52px] flex-1 items-center justify-between rounded-full bg-white/25 px-6 backdrop-blur-md ring-1 ring-white/30"
          >
            <span className="text-[15px] font-medium text-white">Get Started</span>
            <ArrowUpRight className="h-[18px] w-[18px] text-white" aria-hidden />
          </button>
        </div>
      </div>
      <HomeIndicator className="relative z-10 [&>div]:bg-white" />
    </div>
  )
}

/* ───────────────────────── 2. Home ───────────────────────── */

const collections = [
  {
    name: 'Velvet Sofa',
    desc: 'Luxurious comfort to any living space with its soft texture',
    art: <BlueSofa className="h-full w-full" />,
    bg: '#DCD9D4',
  },
  {
    name: 'Desert Chiar',
    desc: 'Offering a calm, grounded seating experience by arid',
    art: <GreenChair className="h-full w-full" />,
    bg: '#E5EAE2',
  },
]

export function HomeScreen() {
  return (
    <div className="flex h-full flex-col bg-[#f3f1ef]">
      <StatusBar />

      {/* hero */}
      <div className="relative mt-1">
        <div className="relative mx-auto h-[268px] w-full">
          <CoralSofa className="absolute left-1/2 top-2 h-[228px] w-[332px] -translate-x-1/2" />

          {/* header buttons float over the hero */}
          <div className="absolute inset-x-5 top-1 z-20 flex items-center justify-between">
            <button type="button" aria-label="Open menu" className={glassCircle}>
              <Menu className="h-[18px] w-[18px] text-[#1b1b1b]" aria-hidden />
            </button>
            <button
              type="button"
              aria-label="Cart, 2 items"
              className="relative flex h-11 w-11 items-center justify-center rounded-full bg-[#141414] shadow-md"
            >
              <ShoppingBag className="h-[17px] w-[17px] text-white" aria-hidden />
              <span className="absolute -right-0.5 -top-0.5 flex h-[17px] w-[17px] items-center justify-center rounded-full bg-[#E4482E] text-[9px] font-bold text-white ring-2 ring-[#f3f1ef]">
                2
              </span>
            </button>
          </div>

          {/* promo glass card — overlaps the sofa so the white type has a mid-tone backdrop */}
          <div className="absolute inset-x-4 bottom-3 z-10 overflow-hidden rounded-[26px] bg-white/15 px-5 py-4 backdrop-blur-2xl ring-1 ring-white/25 [&_*]:drop-shadow-[0_1px_8px_rgba(0,0,0,0.35)]">
            <div className="flex items-center gap-2">
              <span className="flex h-[18px] w-[18px] items-center justify-center rounded-full bg-white/35 text-[9px] font-bold text-white">
                %
              </span>
              <span className="text-[12px] font-medium text-white/90">New Customer Discount</span>
            </div>
            <p className="mt-1 text-[38px] font-bold leading-none tracking-[-0.03em] text-white">
              20% Off
            </p>
            <p className="mt-1.5 text-[13px] text-white/85">Your First Order</p>
          </div>
        </div>

        {/* carousel dots */}
        <div className="mt-2.5 flex justify-center gap-1.5">
          {[0, 1, 2].map((i) => (
            <span
              key={i}
              className={cn(
                'h-[5px] rounded-full transition-all',
                i === 1 ? 'w-[5px] bg-[#1b1b1b]' : 'w-[5px] bg-[#1b1b1b]/20',
              )}
            />
          ))}
        </div>
      </div>

      {/* New Collections */}
      <SectionHeader title="New Collections" />
      <div className="mt-3 flex gap-3 overflow-hidden pl-5 pr-0">
        {collections.map((c) => (
          <div
            key={c.name}
            className="relative h-[158px] w-[118px] shrink-0 overflow-hidden rounded-[20px] p-3"
            style={{ background: c.bg }}
          >
            <div className="absolute inset-x-2 top-3 h-[72px]">{c.art}</div>
            <div className="absolute inset-x-3 bottom-3">
              <p className="text-[13.5px] font-semibold leading-tight text-white drop-shadow-sm">
                {c.name}
              </p>
              <p className="mt-1 text-[8.5px] leading-[1.35] text-white/85 drop-shadow-sm">
                {c.desc}
              </p>
            </div>
          </div>
        ))}
        <div className="h-[158px] w-[118px] shrink-0 rounded-[20px] bg-[#EFE4DD]">
          <p className="px-3 pb-3 pt-[128px] text-[13.5px] font-semibold text-white/70">Sofa</p>
        </div>
      </div>

      {/* Top Trends */}
      <SectionHeader title="Top Trends" className="mt-5" />
      <div className="mt-3 flex gap-3 overflow-hidden pl-5">
        <div className="flex h-[92px] w-[238px] shrink-0 items-center gap-3 rounded-[20px] bg-white p-3">
          <div className="flex h-[68px] w-[62px] shrink-0 items-center justify-center rounded-[14px] bg-[#EFEDE9]">
            <BambooChair className="h-[52px] w-[52px]" />
          </div>
          <div className="min-w-0 flex-1">
            <p className="text-[13.5px] font-bold leading-tight text-[#1b1b1b]">Bamboo chair</p>
            <p className="mt-1 text-[8.5px] leading-[1.35] text-[#8c8882]">
              A bamboo chair offers a lightweight, eco-friendly everyday comfort.
            </p>
            <div className="mt-1.5 flex items-center gap-1">
              <Star className="h-3 w-3 fill-[#F5A623] text-[#F5A623]" aria-hidden />
              <span className="text-[10px] font-bold text-[#1b1b1b]">4.8</span>
              <span className="text-[9px] text-[#8c8882]">240 Reviews</span>
            </div>
          </div>
        </div>
        <div className="flex h-[92px] w-[110px] shrink-0 items-center justify-center rounded-[20px] bg-[#EFEDE9]">
          <CoralSofa className="h-[64px] w-[92px]" />
        </div>
      </div>

      <div className="mt-auto">
        <HomeIndicator />
      </div>
    </div>
  )
}

function SectionHeader({ title, className }: { title: string; className?: string }) {
  return (
    <div className={cn('flex items-baseline justify-between px-5', className)}>
      <h2 className="text-[19px] font-bold tracking-[-0.02em] text-[#1b1b1b]">{title}</h2>
      <button type="button" className="text-[12px] font-medium text-[#8c8882]">
        Show all
      </button>
    </div>
  )
}

/* ───────────────────────── 3. Product detail ───────────────────────── */

const swatches = ['#A855C8', '#E4573A', '#2B29C9', '#FFFFFF']
const sizes = ['3x3', '3x5', '5x5']

export function ProductScreen() {
  const [color, setColor] = useState(1)
  const [size, setSize] = useState(0)
  const [thumb, setThumb] = useState(1)

  return (
    <div className="relative flex h-full flex-col bg-[#f3f1ef]">
      <StatusBar />

      {/* top actions */}
      <div className="relative z-10 flex items-center justify-between px-5 pt-2">
        <button type="button" aria-label="Go back" className={glassCircle}>
          <ChevronLeft className="h-[19px] w-[19px] text-[#1b1b1b]" aria-hidden />
        </button>
        <div className="flex gap-2.5">
          <button type="button" aria-label="Share" className={glassCircle}>
            <Share2 className="h-[16px] w-[16px] text-[#1b1b1b]" aria-hidden />
          </button>
          <button type="button" aria-label="Save to favourites" className={glassCircle}>
            <Heart className="h-[16px] w-[16px] fill-[#1b1b1b] text-[#1b1b1b]" aria-hidden />
          </button>
        </div>
      </div>

      {/* product image */}
      <div className="relative -mt-4">
        <CoralSofa className="h-[300px] w-full" />
        <span className="absolute left-[46%] top-[58%] h-[11px] w-[11px] rounded-full bg-white shadow" />
      </div>

      {/* colour rail */}
      <div className="absolute right-4 top-[200px] z-20 flex flex-col gap-2.5 rounded-full bg-white/45 p-2 backdrop-blur-xl ring-1 ring-white/40">
        {swatches.map((c, i) => (
          <button
            key={c}
            type="button"
            aria-label={`Colour ${i + 1}`}
            aria-pressed={color === i}
            onClick={() => setColor(i)}
            className={cn(
              'h-[30px] w-[30px] rounded-full transition-all',
              color === i && 'ring-[3px] ring-white ring-offset-0 scale-110',
              c === '#FFFFFF' && 'shadow-[inset_0_0_0_1px_rgba(0,0,0,0.08)]',
            )}
            style={{ background: c }}
          />
        ))}
      </div>

      {/* bottom sheet */}
      <div className="relative z-10 mt-auto rounded-t-[32px] bg-[#eceae7] px-5 pb-6 pt-2.5">
        <div className="mx-auto h-[4px] w-9 rounded-full bg-[#1b1b1b]/15" />

        {/* thumbnails */}
        <div className="mt-3 flex justify-center gap-2.5">
          {[0, 1, 2].map((i) => (
            <button
              key={i}
              type="button"
              aria-label={`View ${i + 1}`}
              aria-pressed={thumb === i}
              onClick={() => setThumb(i)}
              className={cn(
                'flex h-[52px] w-[64px] items-center justify-center rounded-[14px] bg-[#e0ddd9] transition-all',
                thumb === i && 'bg-[#f6f4f2] ring-2 ring-white',
              )}
            >
              <CoralSofa className="h-[34px] w-[48px]" />
            </button>
          ))}
        </div>

        <p className="mt-4 text-[11px] text-[#8c8882]">Sofa lounge system</p>
        <div className="mt-0.5 flex items-baseline justify-between">
          <h2 className="text-[23px] font-bold tracking-[-0.025em] text-[#1b1b1b]">Velvet Sofa</h2>
          <span className="text-[23px] font-bold tracking-[-0.025em] text-[#1b1b1b]">$839.00</span>
        </div>

        {/* sizes */}
        <div className="mt-3.5 flex gap-2.5">
          {sizes.map((s, i) => (
            <button
              key={s}
              type="button"
              aria-pressed={size === i}
              onClick={() => setSize(i)}
              className={cn(
                'h-[38px] w-[72px] rounded-full text-[13px] font-semibold transition-colors',
                size === i ? 'bg-white text-[#1b1b1b]' : 'bg-[#e0ddd9] text-[#8c8882]',
              )}
            >
              {s}
            </button>
          ))}
        </div>

        <p className="mt-3.5 max-h-[34px] overflow-hidden pr-24 text-[11.5px] leading-[1.5] text-[#8c8882]">
          Comprising three modular sections, the blue velvet sofa features a subtle yet intriguing
          design that balances versatility with elegance, creating a refined centrepiece.{' '}
          <span className="font-semibold text-[#1b1b1b]">More</span>
        </p>

        <HomeIndicator className="pb-0 pt-3" />
      </div>

      {/* add to cart */}
      <button
        type="button"
        className="absolute bottom-[26px] left-1/2 z-30 flex h-[58px] -translate-x-1/2 items-center gap-3 whitespace-nowrap rounded-full bg-white pl-[5px] pr-8 shadow-[0_14px_34px_-8px_rgba(0,0,0,0.3)]"
      >
        <span className="flex h-12 w-12 shrink-0 items-center justify-center rounded-full bg-[#141414]">
          <ShoppingBag className="h-[18px] w-[18px] text-white" aria-hidden />
        </span>
        <span className="text-[15px] font-semibold text-[#1b1b1b]">Add to cart</span>
      </button>
    </div>
  )
}
