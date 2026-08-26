<script setup lang="ts">
import type { LogItem } from '~/stores/logs'
const logs = useLogsStore()
const emit = defineEmits<{ more: [] }>()
const levelClass = (l: number | null) => l == null ? 'text-zinc-500' : l <= 3 ? 'text-red-400' : l === 4 ? 'text-amber-400' : l <= 6 ? 'text-sky-400' : 'text-zinc-400'
const levelName = (l: number | null) => l == null ? '-' : ({ 0: 'EMERG', 1: 'ALERT', 2: 'CRIT', 3: 'ERROR', 4: 'WARN', 5: 'NOTICE', 6: 'INFO', 7: 'DEBUG' } as any)[l] ?? String(l)
const fmt = (ts: string) => new Date(ts).toLocaleString()
const select = (i: LogItem) => { logs.selected = logs.selected?.id === i.id ? null : i }
</script>
<template>
  <div class="flex-1 overflow-auto font-mono text-xs">
    <p v-if="logs.error" class="p-3 text-red-400">{{ logs.error }}</p>
    <table class="w-full">
      <thead class="sticky top-0 bg-zinc-900 text-left text-zinc-400"><tr>
        <th class="px-2 py-1">Time</th><th class="px-2 py-1">Level</th><th class="px-2 py-1">Source</th><th class="px-2 py-1">Tags</th><th class="px-2 py-1">Message</th>
      </tr></thead>
      <tbody>
        <tr v-for="i in logs.items" :key="i.id" class="cursor-pointer border-t border-zinc-800/60 hover:bg-zinc-900"
            :class="logs.selected?.id === i.id && 'bg-zinc-800'" @click="select(i)">
          <td class="whitespace-nowrap px-2 py-1 text-zinc-400">{{ fmt(i.ts) }}</td>
          <td class="px-2 py-1" :class="levelClass(i.level)">{{ levelName(i.level) }}</td>
          <td class="px-2 py-1 text-emerald-300">{{ i.source }}</td>
          <td class="px-2 py-1 text-zinc-400">{{ i.tags.join(', ') }}</td>
          <td class="max-w-xl truncate px-2 py-1">{{ i.message }}</td>
        </tr>
      </tbody>
    </table>
    <p v-if="!logs.loading && !logs.items.length" class="p-4 text-zinc-500">No results.</p>
    <div class="p-3 text-center">
      <button v-if="logs.next" class="rounded bg-zinc-800 px-3 py-1 hover:bg-zinc-700" :disabled="logs.loading" @click="emit('more')">Load more</button>
    </div>
  </div>
</template>
