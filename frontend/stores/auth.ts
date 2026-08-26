import { defineStore } from 'pinia'

export const useAuthStore = defineStore('auth', {
  state: () => ({ user: null as string | null, checked: false, archiveEnabled: false, footerText: true }),
  actions: {
    async load() {
      try {
        const me = await $fetch<{ user: string | null }>('/api/me', { credentials: 'include' })
        this.user = me.user ?? null
      } catch { this.user = null }
      try {
        const h = await $fetch<{ archiveEnabled: boolean; footerText?: boolean }>('/api/health')
        this.archiveEnabled = h.archiveEnabled
        this.footerText = h.footerText !== false
      } catch { /* backend down: leave default */ }
      this.checked = true
    },
    async login(user: string, password: string) {
      await $fetch('/api/login', { method: 'POST', body: { user, password }, credentials: 'include' })
      this.user = user
    },
    async logout() {
      await $fetch('/api/logout', { method: 'POST', credentials: 'include' })
      this.user = null
    },
  },
})
