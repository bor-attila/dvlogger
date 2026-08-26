export default defineNuxtRouteMiddleware(async (to) => {
  const auth = useAuthStore()
  if (!auth.checked) await auth.load()
  if (!auth.user && to.path !== '/login') return navigateTo('/login')
  if (auth.user && to.path === '/login') return navigateTo('/')
})
