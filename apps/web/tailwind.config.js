/** @type {import('tailwindcss').Config} */
export default {
  // `hover:` compiles to a (hover: hover) media query, so hover colours and transforms apply
  // only where a pointer can actually hover. On a touch screen they would otherwise stick to
  // whatever was tapped last; the desktop UI is unaffected.
  future: { hoverOnlyWhenSupported: true },
  darkMode: 'class',
  content: ['./index.html', './src/**/*.{ts,tsx}'],
  theme: {
    extend: {
      colors: {
        border: 'hsl(var(--border))',
        input: 'hsl(var(--input))',
        ring: 'hsl(var(--ring))',
        background: 'hsl(var(--background))',
        foreground: 'hsl(var(--foreground))',
        primary: {
          DEFAULT: 'hsl(var(--primary))',
          foreground: 'hsl(var(--primary-foreground))',
          hover: 'hsl(var(--primary-hover))',
        },
        secondary: { DEFAULT: 'hsl(var(--secondary))', foreground: 'hsl(var(--secondary-foreground))' },
        muted: { DEFAULT: 'hsl(var(--muted))', foreground: 'hsl(var(--muted-foreground))' },
        accent: { DEFAULT: 'hsl(var(--accent))', foreground: 'hsl(var(--accent-foreground))' },
        destructive: { DEFAULT: 'hsl(var(--destructive))', foreground: 'hsl(var(--destructive-foreground))' },
        card: { DEFAULT: 'hsl(var(--card))', foreground: 'hsl(var(--card-foreground))' },
        surface: 'hsl(var(--surface))',
        sidebar: 'hsl(var(--sidebar))',
      },
      borderRadius: { lg: 'var(--radius)', md: 'calc(var(--radius) - 2px)', sm: 'calc(var(--radius) - 4px)' },
      transitionTimingFunction: {
        // iOS-style decelerate: fast start, long gentle settle. Used for anything that moves or resizes.
        smooth: 'cubic-bezier(0.32, 0.72, 0, 1)',
      },
      keyframes: {
        'fade-in': { from: { opacity: '0' }, to: { opacity: '1' } },
        'fade-in-up': {
          from: { opacity: '0', transform: 'translateY(6px)' },
          to: { opacity: '1', transform: 'translateY(0)' },
        },
        'scale-in': {
          from: { opacity: '0', transform: 'scale(0.97)' },
          to: { opacity: '1', transform: 'scale(1)' },
        },
        'slide-in-from-bottom': {
          from: { opacity: '0', transform: 'translateY(100%)' },
          to: { opacity: '1', transform: 'translateY(0)' },
        },
        'slide-out-to-bottom': {
          from: { opacity: '1', transform: 'translateY(0)' },
          to: { opacity: '0', transform: 'translateY(100%)' },
        },
        'fade-out': { from: { opacity: '1' }, to: { opacity: '0' } },
      },
      animation: {
        'fade-in': 'fade-in 220ms cubic-bezier(0.32, 0.72, 0, 1) both',
        'fade-in-up': 'fade-in-up 260ms cubic-bezier(0.32, 0.72, 0, 1) both',
        'scale-in': 'scale-in 200ms cubic-bezier(0.32, 0.72, 0, 1) both',
        'fade-out': 'fade-out 180ms ease-in both',
        'slide-in-from-bottom': 'slide-in-from-bottom 320ms cubic-bezier(0.32, 0.72, 0, 1) both',
        'slide-out-to-bottom': 'slide-out-to-bottom 240ms cubic-bezier(0.32, 0.72, 0, 1) both',
      },
    },
  },
  plugins: [],
}
