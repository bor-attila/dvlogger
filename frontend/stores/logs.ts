import { defineStore } from 'pinia'

export interface LogItem { id: string; ts: string; source: string; tags: string[]; level: number | null; message: string; host: string; raw: any }
export interface Filters { q: string; from: string; to: string; last: string; source: string; tag: string; level: string }

const emptyFilters = (): Filters => ({ q: '', from: '', to: '', last: '15', source: '', tag: '', level: '' })

export const useLogsStore = defineStore('logs', {
  state: () => ({
    filters: emptyFilters(), items: [] as LogItem[], next: null as string | null,
    loading: false, error: '', selected: null as LogItem | null, refreshSec: 0,
    sources: [] as string[], tags: [] as string[],
  }),
  getters: {
    query: (s) => {
      const f = s.filters; const q: Record<string, string> = { limit: '100' }
      if (f.q) q.q = f.q
      if (f.last) q.last = f.last
      else { if (f.from) q.from = new Date(f.from).toISOString(); if (f.to) q.to = new Date(f.to).toISOString() }
      if (f.source) q.source = f.source
      if (f.tag) q.tag = f.tag
      if (f.level) q.level = f.level
      return q
    },
  },
  actions: {
    async search(base: string) {
      this.loading = true; this.error = ''
      try {
        const r = await useApi().get<{ items: LogItem[]; next: string | null }>(`${base}/logs`, this.query)
        this.items = r.items; this.next = r.next
      } catch (e: any) { this.error = e?.data?.error ?? 'Hiba a lekérdezésben' }
      finally { this.loading = false }
    },
    async loadMore(base: string) {
      if (!this.next || this.loading) return
      this.loading = true
      try {
        const r = await useApi().get<{ items: LogItem[]; next: string | null }>(`${base}/logs`, { ...this.query, before: this.next })
        this.items.push(...r.items); this.next = r.next
      } finally { this.loading = false }
    },
    async loadFacets(base: string) {
      const api = useApi()
      ;[this.sources, this.tags] = await Promise.all([api.get<string[]>(`${base}/sources`), api.get<string[]>(`${base}/tags`)])
    },
    reset() { this.filters = emptyFilters(); this.items = []; this.next = null; this.selected = null },
  },
})
