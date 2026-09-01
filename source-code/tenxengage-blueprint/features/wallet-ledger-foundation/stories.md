# Stories Index — wallet-ledger-foundation

_One row per user story. Each row maps to a `stories/US-NN-*.md` file that is the self-contained execution unit for that story._

---

## Stories Table

| US | Title | Layers | Actor | Touches Entities | Depends on | Can Parallel With | Story File |
|---|---|---|---|---|---|---|---|
| US-01 | Wallet read endpoints | BE | PARTNER_SELLER, PARTNER_ADMIN, CLIENT_ADMIN | RewardWallet | Foundation | — | [stories/US-01-wallet-read-endpoints.md](stories/US-01-wallet-read-endpoints.md) |
| US-02 | Nav balance widget | FE | PARTNER_SELLER, PARTNER_ADMIN | RewardWallet (reads) | Foundation, US-01 | — | [stories/US-02-nav-balance-widget.md](stories/US-02-nav-balance-widget.md) |
| US-03 | Wallet mutation service + grant integration | BE | Internal (RewardGrantService, F-03, F-06) | RewardWallet, LedgerEntry | Foundation, US-01 | — | [stories/US-03-wallet-mutations.md](stories/US-03-wallet-mutations.md) |

---

## Dependency graph

```
Foundation (F1 → F2 → F3, F4)
├── US-01 (wallet-read-endpoints) [BE]
│   ├── US-02 (nav-balance-widget) [FE]   ← reads from US-01 endpoints
│   └── US-03 (wallet-mutations)  [BE]    ← adds mutations to same WalletService.java
└── T1 Cross-story integration tests
```

---

## Parallelism notes

_US-01 and US-03 both modify `WalletService.java` — must run sequentially to avoid merge conflicts. US-01 establishes the class and read methods; US-03 adds mutation methods._

_US-02 can start as soon as US-01 BE is done — it only reads from the wallet endpoints._

---

## Story count

| Total stories | BE-only | FE-only | BE + FE |
|---|---|---|---|
| 3 | 2 | 1 | 0 |
