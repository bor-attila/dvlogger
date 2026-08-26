<script setup lang="ts">
import type { LogItem } from '~/stores/logs'
const logs = useLogsStore()
const emit = defineEmits<{ more: [] }>()
const levelClass = (l: number | null) => l == null ? 'text-zinc-500' : l <= 3 ? 'text-red-400' : l === 4 ? 'text-amber-400' : l <= 6 ? 'text-sky-400' : 'text-zinc-400'
const levelName = (l: number | null) => l == null ? '-' : ({ 0: 'EMERG', 1: 'ALERT', 2: 'CRIT', 3: 'ERROR', 4: 'WARN', 5: 'NOTICE', 6: 'INFO', 7: 'DEBUG' } as any)[l] ?? String(l)
const fmt = (ts: string) => new Date(ts).toLocaleString()
const select = (i: LogItem) => { logs.selected = i }
</script>
<template>
  <section class="overflow-hidden rounded-xl border border-zinc-800 bg-zinc-900/60">
    <p v-if="logs.error" class="border-b border-zinc-800 px-5 py-3 text-sm text-red-400">{{ logs.error }}</p>
    <div class="overflow-x-auto">
      <table class="w-full font-mono text-xs">
        <thead class="bg-zinc-900 text-left text-[11px] uppercase tracking-wide text-zinc-500">
          <tr>
            <th class="px-5 py-3 font-medium">Time</th>
            <th class="px-4 py-3 font-medium">Level</th>
            <th class="px-4 py-3 font-medium">Source</th>
            <th class="px-4 py-3 font-medium">Tags</th>
            <th class="px-4 py-3 font-medium">Message</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="i in logs.items" :key="i.id"
              class="group cursor-pointer border-t border-zinc-800/70 transition-colors duration-150 hover:bg-emerald-500/10"
              @click="select(i)">
            <td class="whitespace-nowrap px-5 py-2.5 text-zinc-400 group-hover:text-zinc-200">{{ fmt(i.ts) }}</td>
            <td class="px-4 py-2.5 font-semibold" :class="levelClass(i.level)">{{ levelName(i.level) }}</td>
            <td class="px-4 py-2.5 text-emerald-300">{{ i.source }}</td>
            <td class="px-4 py-2.5 text-zinc-400">
              <span v-for="t in i.tags" :key="t" class="mr-1 inline-block rounded bg-zinc-800 px-1.5 py-0.5 text-[10px] text-zinc-300 group-hover:bg-zinc-700">{{ t }}</span>
            </td>
            <td class="max-w-xl truncate px-4 py-2.5 text-zinc-200">{{ i.message }}</td>
          </tr>
        </tbody>
      </table>
    </div>
    <p v-if="!logs.loading && !logs.items.length" class="px-5 py-10 text-center text-sm text-zinc-500">No results.</p>
    <div v-if="logs.next" class="border-t border-zinc-800 px-5 py-3 text-center">
      <button class="rounded-lg border border-zinc-700 bg-zinc-800 px-4 py-1.5 text-sm transition hover:border-zinc-600 hover:bg-zinc-700 disabled:opacity-50"
              :disabled="logs.loading" @click="emit('more')">Load more</button>
    </div>
  </section>
</template>
