# Skill: Add Page

TRIGGER when: user asks to create a page, add a page, or new page
DO NOT TRIGGER when: user is asking about backend controllers or API endpoints

## Steps

1. **Check contracts**: Read `contracts/endpoints/` for the API spec and `contracts/models/` for data shapes. If no contract exists, STOP and tell the developer to add it to the contracts repo first.

2. **Create or update types**: In `src/types/`, add TypeScript interfaces matching the contract models exactly. No `any` types.

3. **Create or update service**: In `src/services/`, add API functions using the axios instance from `@/lib/axios`. Each function should be typed with request and response types.

4. **Create TanStack Query hooks**: In `src/hooks/useApi.ts` (or a new domain-specific hook file), add:
   - `useQuery` hooks for reads (with proper query keys)
   - `useMutation` hooks for writes (with query invalidation on success)

5. **Create the page component**: In `src/pages/`, create `<Name>Page.tsx`:
   - Handle loading, error, and empty states
   - Use shadcn/ui components + Tailwind for styling
   - Use react-hook-form + zod for any forms
   - Use DataTable for list views

6. **Add the route**: In `src/App.tsx`, add a `<Route>` inside the protected layout group

7. **Add sidebar navigation**: In `src/components/layout/Sidebar.tsx`, add a nav item with a lucide-react icon
