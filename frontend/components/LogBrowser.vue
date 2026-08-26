<script setup lang="ts">
const props = defineProps<{ base: string; title: string }>()
const logs = useLogsStore(); const auth = useAuthStore()
const search = () => logs.search(props.base)
onMounted(async () => { logs.reset(); await logs.loadFacets(props.base).catch(() => {}); await search() })
watch(() => props.base, async () => { logs.reset(); await logs.loadFacets(props.base).catch(() => {}); await search() })
async function onLogout() { await auth.logout(); await navigateTo('/login') }
</script>
<template>
  <div class="flex h-screen flex-col">
    <header class="flex items-center gap-4 border-b border-zinc-800 px-3 py-2 text-sm">
      <span class="font-semibold">dvLogger</span>
      <NuxtLink to="/" class="hover:text-emerald-300" active-class="text-emerald-300">Live</NuxtLink>
      <NuxtLink v-if="auth.archiveEnabled" to="/archive" class="hover:text-emerald-300" active-class="text-emerald-300">Archive</NuxtLink>
      <span class="ml-auto text-zinc-400">{{ title }} · {{ auth.user }}</span>
      <button class="text-zinc-400 hover:text-zinc-100" @click="onLogout">Log out</button>
    </header>
    <FilterBar @search="search" />
    <div class="flex min-h-0 flex-1">
      <LogTable @more="logs.loadMore(base)" />
      <LogDetailPanel />
    </div>
  </div>
</template>
