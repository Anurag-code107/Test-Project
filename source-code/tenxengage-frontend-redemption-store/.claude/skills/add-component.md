# Skill: Add Component

TRIGGER when: user asks to create a component, add a component, or new component
DO NOT TRIGGER when: user is asking about backend Java classes

## Steps

1. **Define the Props interface**: Create a TypeScript interface named `<ComponentName>Props`. No `any` types.

2. **Create the component file**: In `src/components/`, create `<Name>.tsx` as a functional component:
   ```tsx
   interface ComponentNameProps {
     // typed props
   }

   export function ComponentName({ ...props }: ComponentNameProps) {
     return (...)
   }
   ```

3. **Use Tailwind for styling**: Apply utility classes directly. Use `cn()` from `@/lib/utils` for conditional classes.

4. **Use shadcn/ui primitives**: Build on top of existing `src/components/ui/` components where appropriate (Button, Card, Input, etc.).

5. **Export**: Use named exports. One component per file.

## Conventions
- Functional components only
- Props interface named `<ComponentName>Props`
- Use early returns for conditional rendering
- Responsive: mobile-first (sm: → md: → lg:)
