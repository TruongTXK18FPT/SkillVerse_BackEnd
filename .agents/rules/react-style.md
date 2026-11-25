# React + TypeScript Style Rules

## Naming
- Components: PascalCase (`Navbar.tsx`, `InstallSection.tsx`).
- Custom hooks: camelCase starting with `use` (`useAuth.ts`).
- Helpers & variables: camelCase (`formatCurrency`).
- Types & interfaces: PascalCase (`ButtonProps`).

## Imports
- Use `@/` absolute alias paths. Avoid relative parent paths (`../../`).
- Use `import type` for types.
- Keep feature internals private; expose via `index.ts` only when needed.

## React & JSX
- Components must focus on UI/composition.
- Move side effects (fetching, subscriptions, timers) to custom hooks.
- Use shorthand for boolean props: `<Input disabled />`.
- Handle async UI states: loading, error, empty, success.

## State Management
- Prefer local component state.
- Use Context API for stable app-level state (auth, theme).
- Use Zustand for frequent, cross-feature state updates.

## Testing
- Test user behavior via React Testing Library.
- Test hooks with complex branching/cleanup.
- Avoid snapshot testing unless output is small & stable.

