import { defineConfig } from 'vite'
import react, { reactCompilerPreset } from '@vitejs/plugin-react'
import babel from '@rolldown/plugin-babel'

// https://vite.dev/config/
export default defineConfig({
  server: {
    port: 5173, // Specify the port explicitly
    strictPort: false // If false (default), Vite will try next available port if 5173 is occupied
  },
  plugins: [
    react(),
    babel({ presets: [reactCompilerPreset()] })
  ],
})
