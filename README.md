# Terminal Finance Android

Compose client for Terminal Finance. The visual language is a terminal/TUI skin over a
touch-native Android app: Roboto Mono everywhere, bracketed `[ PANEL ]` headers, a
`~/finance/...` breadcrumb, ASCII bars and a Gruvbox Material Dark palette.

Screens:

- **HOME** — accounts with opening balances and recent transactions.
- **TXNS** — timeline grouped by day, switchable to a dense terminal log; date and category
  filters; tap a transaction to correct its category; add an expense, income, transfer,
  repayment, fee or refund.
- **BILLS** — one obligation occurrence with its status, paid/remaining breakdown and the
  payment history; record a partial payment, edit the confirmed amount, and link a payment to
  the bank transaction that produced it.
- **BUDGET** — placeholder until the budget slice lands.

Every mutation sends an `Idempotency-Key`, so a retried save cannot spend money twice.

## Build

Open this directory in Android Studio, or run:

```bash
JAVA_HOME='C:/Program Files/Android/Android Studio/jbr' ./gradlew assembleDebug
```

The APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

## Connect a physical device to the local backend

1. Start PostgreSQL and the Go backend, then create an obligation and occurrence through the API.
2. With USB debugging enabled, run `adb reverse tcp:8080 tcp:8080`.
3. In the app, open **Server**, enter `http://127.0.0.1:8080`, and enter that occurrence UUID.

Cleartext HTTP is allowed only in debug builds; release builds require HTTPS.

## Layout

- `app/src/main/java/finance/grummans/app/ui/Tui.kt` — palette, typography and the TUI
  building blocks (panel, card, row, input, bar, button, tabs, bottom nav, dialog).
- `app/src/main/java/finance/grummans/app/MainActivity.kt` — app shell and the HOME / BILLS /
  BUDGET screens.
- `app/src/main/java/finance/grummans/app/TxnsScreen.kt` — timeline, terminal log, new transaction.
- `app/src/main/java/finance/grummans/app/FinanceApi.kt` — the HTTP client (`HttpURLConnection`).
