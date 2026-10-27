/** Demo-only logins for the offline mock backend (VITE_USE_MOCKS=true). They exist nowhere else. */
export const DEMO_PASSWORD = 'Demo1234'

export const DEMO_ACCOUNTS = [
  { email: 'customer@swiftdrop.demo', label: 'Customer', hint: 'Book and track deliveries' },
  { email: 'merchant@swiftdrop.demo', label: 'Merchant', hint: 'Orders, API keys, webhooks' },
  { email: 'courier@swiftdrop.demo', label: 'Courier', hint: "Today's manifest" },
  { email: 'dispatcher@swiftdrop.demo', label: 'Dispatcher', hint: 'Assign couriers' },
  { email: 'admin@swiftdrop.demo', label: 'Admin', hint: 'Everything' },
] as const
