# Form Handling Pattern

## Zod + react-hook-form

All forms use `useForm<FormValues>({ resolver: zodResolver(schema) })`.

---

## Optional Numeric Override Fields

### The `z.coerce.number()` Pitfall

`z.coerce.number()` coerces an **empty string to `0`**, not `undefined`. This silently overwrites
inherited/default values with zero when the user leaves an override field blank.

**Wrong:**
```ts
returnWindowDaysOverride: z.coerce.number().min(0).optional(),
// "" → 0  ← wrong: 0 is sent to the server as an explicit override
```

**Correct — use `z.preprocess` to intercept before coercion:**
```ts
returnWindowDaysOverride: z.preprocess(
  (v) => (v === "" || v == null ? undefined : v),
  z.coerce.number().min(0).optional(),
),
```

**Also omit the field entirely from the request payload when undefined:**
```ts
const request = {
  enabled: config?.enabled ?? false,
  processingModeOverride: values.processingModeOverride || undefined,
  // spread-omit pattern — field is absent from payload when not set
  ...(values.returnWindowDaysOverride !== undefined && {
    returnWindowDaysOverride: values.returnWindowDaysOverride,
  }),
};
```

Apply this pattern to **any numeric field that represents an "override" of an upstream default**
(return-window days, min-amount overrides, wallet-balance overrides, etc.).

---

## Syncing Server Data into react-hook-form

When a form receives async server data (from TanStack Query), use `reset()` inside a `useEffect`
that depends on the config object. This is the accepted approach for edit forms that load existing
data, with one caveat: the effect fires an extra render cycle each time the dependency changes.

```tsx
const { data } = useQuery(/* ... */);
const config = data?.data[0];

const { reset, handleSubmit } = useForm<FormValues>({
  resolver: zodResolver(schema),
  defaultValues: { /* stable initial values */ },
});

useEffect(() => {
  if (config) {
    reset({
      processingModeOverride: config.processingModeOverride ?? undefined,
      returnWindowDaysOverride: config.returnWindowDaysOverride ?? undefined,
      // ...
    });
  }
}, [config, reset]);
```

**Avoid calling `setValue` field-by-field** in effects — `reset()` atomically updates all fields
and clears dirty/touched state in one pass.

**Do not use `values` and `defaultValues` simultaneously** — pick one. For async edit forms, use
`defaultValues` for the initial skeleton and `reset()` via effect for the loaded data.

---

## Form Submit: Cleaning Optional String Overrides

Convert empty strings to `undefined` before sending to the API so inherited fields are omitted:

```ts
const request = {
  minTransactionAmountOverride: values.minTransactionAmountOverride || undefined,
  minWalletBalanceOverride: values.minWalletBalanceOverride || undefined,
};
```

`|| undefined` collapses `""` and any other falsy value to `undefined`.
