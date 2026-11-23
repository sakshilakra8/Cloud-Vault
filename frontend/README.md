# ShardVault frontend

React, TypeScript, Vite, Tailwind, and Radix-based shadcn-style primitives for the ShardVault web client.

## Setup

```sh
cp .env.example .env.local
npm ci
npm run dev
```

The backend defaults to `http://localhost:8080`. Set `VITE_API_BASE_URL` when it is hosted elsewhere. In production, serve the frontend and API on the same site or configure the API CORS allow-list and secure refresh cookie for the deployed origin.

## Commands

```sh
npm test
npm run lint
npm run format:check
npm run build
```

The API client keeps access tokens in module memory, sends refresh cookies with credentialed requests, coalesces simultaneous refreshes, and retries one failed request after a 401. The styleguide lives at `/styleguide` and is lazy-loaded.

The frontend auth forms use `/api/v1/auth/register`, `/login`, `/refresh`, `/logout`, and `/me` from the backend. A successful registration returns an authenticated session, so the user lands in the empty workspace immediately.
