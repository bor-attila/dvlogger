import tailwindcss from '@tailwindcss/vite'

export default defineNuxtConfig({
  ssr: false,
  compatibilityDate: '2026-08-25',
  modules: ['@pinia/nuxt'],
  css: ['~/assets/css/main.css'],
  vite: { plugins: [tailwindcss()] },
  devServer: { port: 3000 },
  nitro: { devProxy: { '/api': { target: 'http://localhost:8080/api', changeOrigin: true } } },
  app: { head: { title: 'dvlogger' } },
})
