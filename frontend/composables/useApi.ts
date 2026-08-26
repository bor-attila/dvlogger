export function useApi() {
  async function call<T>(method: 'GET' | 'POST', path: string, opts: { query?: Record<string, any>; body?: any } = {}): Promise<T> {
    try {
      return await $fetch<T>(path, { method, query: opts.query, body: opts.body, credentials: 'include' })
    } catch (e: any) {
      if (e?.status === 401 || e?.response?.status === 401) {
        const auth = useAuthStore()
        auth.user = null
        if (useRoute().path !== '/login') navigateTo('/login')
      }
      throw e
    }
  }
  return {
    get: <T>(path: string, query?: Record<string, any>) => call<T>('GET', path, { query }),
    post: <T>(path: string, body?: any) => call<T>('POST', path, { body }),
  }
}
