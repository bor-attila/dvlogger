<script setup lang="ts">
const logs = useLogsStore()
const emit = defineEmits<{ search: [] }>()
const lastOptions = [
  { value: '', label: 'Custom range' }, { value: '5', label: 'Last 5 min' }, { value: '15', label: 'Last 15 min' },
  { value: '60', label: 'Last hour' }, { value: '360', label: 'Last 6 hours' }, { value: '1440', label: 'Last 24 hours' },
]
const levels = [
  { value: '', label: 'All levels' }, { value: '3', label: 'ERROR' }, { value: '4', label: 'WARN' },
  { value: '6', label: 'INFO' }, { value: '7', label: 'DEBUG' },
]
const sourceOptions = computed(() => [{ value: '', label: 'All sources' }, ...logs.sources.map(s => ({ value: s, label: s }))])
const tagOptions = computed(() => [{ value: '', label: 'All tags' }, ...logs.tags.map(t => ({ value: t, label: t }))])
</script>
<template>
  <form class="rounded-xl border border-zinc-800 bg-zinc-900/60 p-4" @submit.prevent="emit('search')">
    <div class="flex flex-wrap items-center gap-3">
      <div class="relative min-w-72 flex-1">
        <svg class="pointer-events-none absolute left-3 top-1/2 size-4 -translate-y-1/2 text-zinc-500" viewBox="0 0 20 20" fill="currentColor" aria-hidden="true">
          <path fill-rule="evenodd" d="M9 3.5a5.5 5.5 0 1 0 0 11 5.5 5.5 0 0 0 0-11ZM2 9a7 7 0 1 1 12.45 4.39l3.33 3.33a1 1 0 0 1-1.42 1.42l-3.33-3.33A7 7 0 0 1 2 9Z" clip-rule="evenodd" />
        </svg>
        <input v-model="logs.filters.q" placeholder='Search text… ("quotes" = exact phrase)'
               class="w-full rounded-lg border border-zinc-800 bg-zinc-900 py-2 pl-9 pr-3 text-sm transition placeholder:text-zinc-500 hover:border-zinc-700 focus:border-emerald-500/60 focus:outline-none" />
      </div>
      <SelectBox v-model="logs.filters.last" :options="lastOptions" />
      <template v-if="!logs.filters.last">
        <input v-model="logs.filters.from" type="datetime-local" class="rounded-lg border border-zinc-800 bg-zinc-900 px-3 py-2 text-sm text-zinc-200 transition hover:border-zinc-700 focus:border-emerald-500/60 focus:outline-none" />
        <span class="text-zinc-500">→</span>
        <input v-model="logs.filters.to" type="datetime-local" class="rounded-lg border border-zinc-800 bg-zinc-900 px-3 py-2 text-sm text-zinc-200 transition hover:border-zinc-700 focus:border-emerald-500/60 focus:outline-none" />
      </template>
      <SelectBox v-model="logs.filters.source" :options="sourceOptions" />
      <SelectBox v-model="logs.filters.tag" :options="tagOptions" />
      <SelectBox v-model="logs.filters.level" :options="levels" />
      <button class="rounded-lg bg-emerald-600 px-5 py-2 text-sm font-medium text-white transition hover:bg-emerald-500 disabled:opacity-50" :disabled="logs.loading">
        {{ logs.loading ? 'Searching…' : 'Search' }}
      </button>
      <AutoRefresh class="ml-auto" @tick="emit('search')" />
    </div>
  </form>
</template>
