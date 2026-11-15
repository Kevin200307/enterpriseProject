import baseUserEvent from '@testing-library/user-event'

/**
 * Shared user-event instance with no pause between keystrokes. The default awaits a timer after every character,
 * which made the form tests (12+ fields each) time out whenever CPU was busy. Behaviour is otherwise identical.
 */
const instance = baseUserEvent.setup({ delay: null })

const userEvent = Object.assign(instance, {
  setup: (options: Parameters<typeof baseUserEvent.setup>[0] = {}) =>
    baseUserEvent.setup({ delay: null, ...options }),
})

export default userEvent
