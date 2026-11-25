---
name: react-feature
description: Generates or extends a React + TypeScript feature with component, hook, types, styles, and tests
---

Follow this workflow to create a new React + TypeScript feature or route.

Inputs:
- featureName: Name of the feature (e.g., `billing`)
- targetPath: Feature or route folder to extend
- userFlow: Brief description of the user-visible behavior

Steps:
1. Inspect neighboring features, routes, shared components, and import aliases before editing.
2. Define feature-local types first, including loading, error, empty, and success state shapes.
3. Create or update the custom hook that owns fetching, side effects, derived state, and cleanup.
4. Create or update PascalCase components that render UI and delegate logic to hooks.
5. Add styles using the project's existing styling approach; do not introduce a new styling system.
6. Wire the feature into the route or parent component through the smallest public API needed.
7. Add tests for component behavior and hook logic when branching, async work, or cleanup is present.
8. Run validations:
   - `npm run lint`
   - `npm run typecheck`
   - `npm run test -- --run`
   - `npm run build`
