/// <reference types="vite/client" />

interface ImportMetaEnv {
  readonly VITE_API_BASE_URL: string
  readonly VITE_ENABLE_BUG_REPORTER: string | undefined
  readonly VITE_BUG_CAPTURE_URL: string | undefined
  readonly VITE_BUG_CAPTURE_API_KEY: string | undefined
  readonly VITE_BUG_REPORTER_NETWORK_WINDOW_MS: string | undefined
  readonly VITE_BUG_REPORTER_NOISE_ENDPOINTS: string | undefined
}

interface ImportMeta {
  readonly env: ImportMetaEnv
}