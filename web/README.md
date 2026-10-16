# GPay Expense Tracker - Next.js Web Dashboard

> **Authentication note:** This dashboard does not yet sign requests with the
> Android Keystore key. It is suitable for local development while
> `DEVICE_AUTH_ENABLED=false`, but protected hosted endpoints will return 401
> after phone-only authentication is enabled.

Modern, responsive web dashboard built with Next.js App Router, Tailwind CSS, and Lucide Icons.

## Features

- **Overview Dashboard**: Key financial metrics (Total Monthly Spending, Transaction Count, Top Category).
- **Category Breakdown**: Spending visual distribution cards with progress indicators.
- **Transactions Management Data Grid**: Search, filter by category, manual transaction creation, and delete actions.
- **Analytics View**: Spending proportions and average ticket size calculations.
- **Live FastAPI Integration**: Connects to the FastAPI backend API at `http://localhost:8000/api/v1`.

---

## Local Development Setup

1. **Install dependencies**:
   ```bash
   npm install
   ```

2. **Run dev server**:
   ```bash
   npm run dev
   ```

3. Open `http://localhost:3000` in your browser.
