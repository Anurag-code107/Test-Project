# Stories Index — redemption-catalog

_One row per user story. Each row maps to a `stories/US-NN-*.md` file that is the self-contained execution unit for that story._
_Every story row must produce at least one Playwright E2E test in its story file._

---

## Stories Table

| US | Title | Layers | Actor | Touches Entities | Depends on | Can Parallel With | Story File |
|---|---|---|---|---|---|---|---|
| US-01 | Manage global catalog items | BE + FE | TENX_ADMIN | RedemptionCatalogItem | Foundation | — | [stories/US-01-manage-global-catalog-items.md](stories/US-01-manage-global-catalog-items.md) |
| US-02 | Configure tenant catalog | BE + FE | CLIENT_ADMIN | ClientCatalogItemConfig, TenantRedemptionSettings | Foundation, US-01 | US-04, US-05 | [stories/US-02-configure-tenant-catalog.md](stories/US-02-configure-tenant-catalog.md) |
| US-03 | Configure regional catalog | BE + FE | CLIENT_ADMIN | ClientCatalogRegionConfig | Foundation, US-02 | — | [stories/US-03-configure-regional-catalog.md](stories/US-03-configure-regional-catalog.md) |
| US-04 | Browse currency-aware catalog | BE + FE | PARTNER_SELLER, PARTNER_ADMIN | RedemptionCatalogItem, ClientCatalogItemConfig, RewardWallet | Foundation, US-01 | US-02, US-05 | [stories/US-04-browse-currency-aware-catalog.md](stories/US-04-browse-currency-aware-catalog.md) |
| US-05 | Xoxoday sync + integration health | BE + FE | TENX_ADMIN | RedemptionCatalogItem | Foundation, US-01 | US-02, US-04 | [stories/US-05-xoxoday-sync-integration-health.md](stories/US-05-xoxoday-sync-integration-health.md) |

_Layers values: `BE + FE` (full stack), `BE` (no user-visible UI), `FE` (no new endpoints)._

---

## Dependency graph

```
Foundation (F1 → F2 → F3, F4)
└── US-01: Manage global catalog items (BE+FE)
    ├── US-02: Configure tenant catalog (BE+FE)     ← items must exist before tenants can configure them
    │   └── US-03: Configure regional catalog (BE+FE)  ← item configs must exist before regional overrides
    ├── US-04: Browse currency-aware catalog (BE+FE) ← items must exist + be enabled to browse
    └── US-05: Xoxoday sync + integration health (BE+FE) ← items must exist before sync can deactivate them
```

---

## Parallelism notes

_Stories that can run concurrently after US-01 BE is done:_
- US-02, US-04, US-05 — all depend only on US-01 and Foundation; disjoint controllers and service classes

_Stories that must run sequentially:_
- US-01 before US-02 — tenant config requires catalog items to exist
- US-02 before US-03 — regional overrides require item configs to exist
- US-01 before US-04 — partner browse requires active + enabled items
- US-01 before US-05 — sync job requires items to deactivate

---

## Story count

| Total stories | BE-only | FE-only | BE + FE |
|---|---|---|---|
| 5 | 0 | 0 | 5 |
