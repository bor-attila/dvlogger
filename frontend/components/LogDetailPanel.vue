<script setup lang="ts">
const logs = useLogsStore()
const pretty = computed(() => logs.selected ? JSON.stringify(logs.selected.raw, null, 2) : '')
</script>
<template>
  <aside v-if="logs.selected" class="w-[28rem] shrink-0 overflow-auto border-l border-zinc-800 bg-zinc-900 p-3 text-xs">
    <div class="mb-2 flex items-center justify-between"><h2 class="font-semibold">Részletek</h2>
      <button class="text-zinc-400 hover:text-zinc-100" @click="logs.selected = null">✕</button></div>
    <dl class="mb-3 grid grid-cols-[6rem_1fr] gap-y-1">
      <dt class="text-zinc-400">id</dt><dd class="break-all font-mono">{{ logs.selected.id }}</dd>
      <dt class="text-zinc-400">ts</dt><dd>{{ logs.selected.ts }}</dd>
      <dt class="text-zinc-400">source</dt><dd>{{ logs.selected.source }}</dd>
      <dt class="text-zinc-400">host</dt><dd>{{ logs.selected.host }}</dd>
      <dt class="text-zinc-400">level</dt><dd>{{ logs.selected.level ?? '-' }}</dd>
      <dt class="text-zinc-400">tags</dt><dd>{{ logs.selected.tags.join(', ') || '-' }}</dd>
    </dl>
    <p class="mb-2 whitespace-pre-wrap break-words rounded bg-zinc-800 p-2 font-mono">{{ logs.selected.message }}</p>
    <pre class="overflow-auto rounded bg-zinc-950 p-2 font-mono text-[11px]">{{ pretty }}</pre>
  </aside>
</template>
