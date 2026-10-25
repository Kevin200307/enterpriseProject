import {
  ArrowRight,
  BellRing,
  CheckCircle2,
  ChevronRight,
  ClipboardList,
  MapPin,
  PackageCheck,
  Route,
  ShieldCheck,
  Store,
  Truck,
  Users,
} from 'lucide-react'
import { Link, Navigate } from 'react-router-dom'
import { useAuth } from '@/auth/authContext'
import { roleLanding } from '@/auth/landing'
import { LogoLockup } from '@/components/brand/Logo'
import { LoadingScreen } from '@/components/common/LoadingScreen'
import { Button } from '@/components/ui/button'

const features = [
  {
    icon: PackageCheck,
    title: 'Book deliveries simply',
    description: 'Create a delivery in a few steps with pickup, drop-off, contact, and parcel details.',
  },
  {
    icon: Route,
    title: 'Track every movement',
    description: 'Follow parcel progress from booking to delivery with clear status updates.',
  },
  {
    icon: BellRing,
    title: 'Stay informed',
    description: 'Receive timely notifications as your delivery moves through each stage.',
  },
]

const roles = [
  { icon: Users, title: 'Customers', description: 'Book, manage, and track deliveries from one place.' },
  { icon: Store, title: 'Merchants', description: 'Manage orders, integrations, and delivery operations.' },
  { icon: Truck, title: 'Couriers', description: 'See assignments, update statuses, and complete routes.' },
  { icon: ShieldCheck, title: 'Operations teams', description: 'Dispatch, monitor, and manage the delivery network.' },
]

export function LandingPage() {
  const { status, claims } = useAuth()

  if (status === 'loading') return <LoadingScreen />
  if (claims) return <Navigate to={roleLanding(claims.roles)} replace />

  return (
    <div className="min-h-screen overflow-hidden bg-background text-foreground">
      <header className="mx-auto flex w-full max-w-7xl items-center justify-between px-5 py-5 sm:px-8 lg:px-10">
        <Link to="/" aria-label="SwiftDrop home">
          <LogoLockup className="h-9 w-[130px] text-sidebar dark:text-white" onLight />
        </Link>
        <div className="flex items-center gap-2">
          <Link
            to="/track"
            className="hidden rounded-xl px-3 py-2 text-sm font-medium text-muted-foreground transition-colors hover:bg-secondary hover:text-foreground sm:inline-flex"
          >
            Track a parcel
          </Link>
          <Button asChild size="sm" className="rounded-xl">
            <Link to="/login">Sign in</Link>
          </Button>
        </div>
      </header>

      <main>
        <section className="mx-auto grid w-full max-w-7xl gap-10 px-5 pb-16 pt-10 sm:px-8 sm:pt-16 lg:grid-cols-[1.05fr_.95fr] lg:items-center lg:px-10 lg:pb-24 lg:pt-20">
          <div className="max-w-2xl">
            <p className="mb-5 inline-flex items-center gap-2 rounded-full border border-primary/30 bg-primary/10 px-3 py-1.5 text-xs font-semibold text-primary">
              <span className="h-1.5 w-1.5 rounded-full bg-primary" />
              Smarter delivery management
            </p>
            <h1 className="max-w-xl text-4xl font-bold tracking-tight sm:text-5xl lg:text-6xl">
              Every delivery,{' '}
              <span className="text-primary">moving forward.</span>
            </h1>
            <p className="mt-5 max-w-xl text-base leading-7 text-muted-foreground sm:text-lg">
              SwiftDrop connects customers, merchants, couriers, and operations teams in one
              reliable delivery platform. Book, manage, dispatch, and track with confidence.
            </p>
            <div className="mt-8 flex flex-col gap-3 sm:flex-row">
              <Button asChild size="lg" className="h-12 rounded-xl px-6">
                <Link to="/login">
                  Get started
                  <ArrowRight className="ml-2 h-4 w-4" />
                </Link>
              </Button>
              <Button asChild variant="outline" size="lg" className="h-12 rounded-xl px-6">
                <Link to="/track">
                  Track a parcel
                  <MapPin className="ml-2 h-4 w-4" />
                </Link>
              </Button>
            </div>
            <div className="mt-7 flex flex-wrap gap-x-5 gap-y-2 text-xs text-muted-foreground">
              {['Simple booking', 'Clear tracking', 'Built for every team'].map((item) => (
                <span key={item} className="inline-flex items-center gap-1.5">
                  <CheckCircle2 className="h-3.5 w-3.5 text-primary" />
                  {item}
                </span>
              ))}
            </div>
          </div>

          <div className="relative min-h-[300px] overflow-hidden rounded-3xl bg-sidebar p-4 shadow-2xl shadow-sidebar/15 sm:min-h-[390px] sm:p-6">
            <img
              src="/active-deliveries-card.webp"
              alt="Delivery route through a city"
              className="absolute inset-0 h-full w-full object-cover opacity-80"
            />
            <div className="absolute inset-0 bg-gradient-to-br from-sidebar/40 via-sidebar/20 to-sidebar/90" />
            <div className="relative flex h-full min-h-[268px] flex-col justify-between sm:min-h-[342px]">
              <div className="flex items-center justify-between">
                <span className="rounded-full bg-white/15 px-3 py-1.5 text-xs font-medium text-white backdrop-blur">
                  SwiftDrop network
                </span>
                <span className="flex h-10 w-10 items-center justify-center rounded-2xl bg-primary text-sidebar">
                  <Truck className="h-5 w-5" />
                </span>
              </div>
              <div className="max-w-xs rounded-2xl border border-white/15 bg-black/35 p-4 text-white backdrop-blur-md">
                <p className="text-xs text-white/65">Your delivery at a glance</p>
                <p className="mt-1 text-lg font-semibold">From booking to doorstep</p>
                <div className="mt-4 flex items-center gap-2 text-xs text-white/75">
                  <span className="h-2 w-2 rounded-full bg-primary" />
                  Real-time status updates
                  <ChevronRight className="ml-auto h-3.5 w-3.5" />
                </div>
              </div>
            </div>
          </div>
        </section>

        <section className="border-y bg-secondary/30">
          <div className="mx-auto grid w-full max-w-7xl gap-5 px-5 py-14 sm:grid-cols-3 sm:px-8 lg:px-10">
            {features.map(({ icon: Icon, title, description }) => (
              <div key={title} className="rounded-2xl border bg-card p-5">
                <span className="mb-5 flex h-10 w-10 items-center justify-center rounded-xl bg-primary/15 text-primary">
                  <Icon className="h-5 w-5" />
                </span>
                <h2 className="font-semibold">{title}</h2>
                <p className="mt-2 text-sm leading-6 text-muted-foreground">{description}</p>
              </div>
            ))}
          </div>
        </section>

        <section className="mx-auto w-full max-w-7xl px-5 py-16 sm:px-8 lg:px-10 lg:py-24">
          <div className="max-w-xl">
            <p className="text-sm font-semibold text-primary">One connected system</p>
            <h2 className="mt-2 text-3xl font-bold tracking-tight sm:text-4xl">
              One platform for every delivery role.
            </h2>
            <p className="mt-4 leading-7 text-muted-foreground">
              Everyone sees the information and actions they need, without the noise. SwiftDrop
              keeps the entire delivery journey visible and accountable.
            </p>
          </div>
          <div className="mt-10 grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
            {roles.map(({ icon: Icon, title, description }) => (
              <div key={title} className="rounded-2xl border p-5">
                <Icon className="h-5 w-5 text-primary" />
                <h3 className="mt-5 font-semibold">{title}</h3>
                <p className="mt-2 text-sm leading-6 text-muted-foreground">{description}</p>
              </div>
            ))}
          </div>
        </section>

        <section className="mx-5 mb-12 overflow-hidden rounded-3xl bg-sidebar px-6 py-12 text-center text-white sm:mx-8 lg:mx-auto lg:max-w-7xl lg:px-10">
          <ClipboardList className="mx-auto h-7 w-7 text-primary" />
          <h2 className="mt-4 text-3xl font-bold tracking-tight">Ready to move smarter?</h2>
          <p className="mx-auto mt-3 max-w-lg text-sm leading-6 text-white/65">
            Sign in to your SwiftDrop workspace and take control of your delivery journey.
          </p>
          <Button asChild size="lg" className="mt-7 h-12 rounded-xl bg-primary px-6 text-sidebar hover:bg-primary/90">
            <Link to="/login">
              Get started
              <ArrowRight className="ml-2 h-4 w-4" />
            </Link>
          </Button>
        </section>
      </main>

      <footer className="mx-auto flex w-full max-w-7xl flex-col gap-2 border-t px-5 py-6 text-xs text-muted-foreground sm:flex-row sm:items-center sm:justify-between sm:px-8 lg:px-10">
        <span>© {new Date().getFullYear()} SwiftDrop</span>
        <span>University of Moratuwa · Enterprise Application Development</span>
      </footer>
    </div>
  )
}
