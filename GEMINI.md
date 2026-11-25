# Repository Agent Guidelines

This repository implements the `agent-workflow-kit-cli` standards.

---

## 🚀 AI Agent 5-Step Execution Workflow

### 1. 🔍 Step 1: Research & Context Analysis
- **Selective Config Scan:** Read `AGENTS.md`. Only read rule files in `.agents/rules/` relevant to the files/stack you are modifying (e.g. `react-style.md` when editing JS/TS). Do not scan irrelevant rules or skills.
- **codebase Scan:** Read relevant codebase files, configurations, and `README.md` to understand context and project goals.
- Do NOT modify code or run build commands in this step.

### 2. 📝 Step 2: Formulate Implementation Plan
- Document your proposed approach.
- List all files to be created (`[NEW]`), modified (`[MODIFY]`), or deleted (`[DELETE]`).
- Identify all verification commands to run.

### 3. 💻 Step 3: Implementation
- Follow type safety, project conventions, and architecture.
- Preserve existing comments, docstrings, and license headers.

### 4. 🧪 Step 4: Verification & Conformance Testing
- Run automated verification commands (compile, lint, test, build). Rerun until 100% clean.

### 5. 🔍 Step 5: Self-Review & QA
- Review the diff for syntax, logs, and unused imports.
- Summarize changes and verification results before finishing.

---

## ⚙️ General Engineering Conventions
- Follow existing patterns in naming, architecture, and layouts.
- Ensure type safety and run verification checks before declaring a task done.

<!-- AWK-START: STACK_PACK -->
## React + TypeScript Guidelines

### Architecture & Feature Conventions
Use a feature-first structure. Keep route wiring thin, and place feature UI, hooks, data clients, and types together:

```text
src/
  app/                  # App shell, router, providers
  features/
    billing/
      components/
        BillingPanel.tsx
      hooks/
        useBilling.ts
      services/
        billingClient.ts
      types.ts
      index.ts
  shared/
    components/
    hooks/
    utils/
```

### Component, Hook, and State Rules
- Components render UI and delegate fetching, subscriptions, timers, and other side effects to custom hooks.
- Custom hooks own reusable stateful logic and must start with `use`.
- Prefer local component state for isolated interactions.
- Use Context API for low-frequency cross-tree state such as theme, auth session, or app configuration.
- Use Zustand only for shared state with frequent updates or cross-feature coordination.
- Keep feature state close to the feature before moving it to `shared/`.

### TypeScript Standards
- Use strict TypeScript. Avoid `any`; prefer explicit domain types and discriminated unions.
- Define feature-local interfaces and types near the code that owns them.
- Export from feature `index.ts` files only when another module needs the API.
- Use absolute imports from `@/`; do not add deep relative imports like `../../`.

### Testing & Verification
Before completing a React + TypeScript change, run the project validation pipeline:

```bash
npm run lint
npm run typecheck
npm run test -- --run
npm run build
```
<!-- AWK-END: STACK_PACK -->

