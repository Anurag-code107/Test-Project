# Error Handling Pattern

## Mutation Error Handling

Use `axios.isAxiosError(err)` to narrow error types in mutation `catch` blocks. Casting `err as { response?: ... }` is an `any`-style bypass that bypasses TypeScript's type system.

```tsx
} catch (err: unknown) {
  if (!axios.isAxiosError(err)) {
    toast.error("Something went wrong. Please try again.");
    return;
  }
  const status = err.response?.status;
  const data = err.response?.data as { details?: Record<string, string> } | undefined;

  if (status === 409) { /* handle conflict */ }
  if (status === 400 && data?.details) { /* walk field errors */ }

  toast.error("Something went wrong. Please try again.");
}
```

**When testing**, mock the rejected value with `isAxiosError: true` on the error object so `axios.isAxiosError()` recognises it:

```ts
mockMutateAsync.mockRejectedValueOnce({
  isAxiosError: true,
  response: { status: 409, data: { message: "Conflict" } },
});
```

## Pitfalls

**Never cast `err as { response?: ... }`.** `axios.isAxiosError()` is the correct narrowing approach. The cast silently passes for any thrown value including non-Axios errors.
