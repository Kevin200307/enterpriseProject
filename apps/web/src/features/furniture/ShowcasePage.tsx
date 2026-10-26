import { PhoneFrame } from './PhoneFrame'
import { HomeScreen, OnboardingScreen, ProductScreen } from './Screens'

export function ShowcasePage() {
  return (
    <div
      className="flex min-h-screen items-center justify-center overflow-x-auto p-8"
      style={{
        background:
          'radial-gradient(120% 110% at 30% 10%, #b9b6b2 0%, #9b9895 45%, #8d8a87 100%)',
      }}
    >
      <div className="flex flex-col items-center gap-14 lg:flex-row lg:items-center lg:gap-10">
        <PhoneFrame>
          <OnboardingScreen />
        </PhoneFrame>
        <PhoneFrame className="lg:-translate-y-10">
          <HomeScreen />
        </PhoneFrame>
        <PhoneFrame>
          <ProductScreen />
        </PhoneFrame>
      </div>
    </div>
  )
}
