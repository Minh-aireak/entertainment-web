# Aireak Entertainment (frontend)

React + Vite + MUI. API base URL: `VITE_API_BASE_URL` (see `.env`).

## Chạy local

```bash
npm install
npm run dev
```

## Docker

Build image từ thư mục này (xem `Dockerfile` ở root compose: `frontend`).

## Cấu trúc chính

- `src/App.tsx` — routing (Social `/social/*`, Travel `/travel/*`)
- `src/components/Layout/MainLayout.tsx` — layout + sidebar module
- `src/config/navigation.ts` — menu theo module
