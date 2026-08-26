<script setup lang="ts">
const props = defineProps<{ base: string; title: string }>()
const logs = useLogsStore(); const auth = useAuthStore()
const search = () => logs.search(props.base)
onMounted(async () => { logs.reset(); await logs.loadFacets(props.base).catch(() => {}); await search() })
watch(() => props.base, async () => { logs.reset(); await logs.loadFacets(props.base).catch(() => {}); await search() })
async function onLogout() { await auth.logout(); await navigateTo('/login') }
</script>
<template>
  <div class="mx-auto flex min-h-screen w-full max-w-7xl flex-col gap-5 px-6 py-8 lg:px-10">
    <header class="flex items-center gap-6 rounded-xl border border-zinc-800 bg-zinc-900/60 px-6 py-4">
      <span class="text-lg font-semibold tracking-tight">dv<span class="text-emerald-400">Logger</span></span>
      <nav class="flex items-center gap-1 text-sm">
        <NuxtLink to="/" class="rounded-lg px-3 py-1.5 text-zinc-400 transition hover:bg-zinc-800 hover:text-zinc-100"
                  active-class="!bg-zinc-800 !text-emerald-300">Live</NuxtLink>
        <NuxtLink v-if="auth.archiveEnabled" to="/archive" class="rounded-lg px-3 py-1.5 text-zinc-400 transition hover:bg-zinc-800 hover:text-zinc-100"
                  active-class="!bg-zinc-800 !text-emerald-300">Archive</NuxtLink>
      </nav>
      <div class="ml-auto flex items-center gap-4 text-sm">
        <span class="text-zinc-500">{{ title }} · <span class="text-zinc-300">{{ auth.user }}</span></span>
        <button class="rounded-lg border border-zinc-800 px-3 py-1.5 text-zinc-400 transition hover:border-zinc-700 hover:bg-zinc-800 hover:text-zinc-100" @click="onLogout">Log out</button>
      </div>
    </header>
    <FilterBar @search="search" />
    <LogTable @more="logs.loadMore(base)" />
    <LogDetailModal />
  </div>
</template>
