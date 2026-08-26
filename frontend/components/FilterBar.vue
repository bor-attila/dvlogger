<script setup lang="ts">
const logs = useLogsStore()
const emit = defineEmits<{ search: [] }>()
const lastOptions = [['', 'Egyedi'], ['5', '5 perc'], ['15', '15 perc'], ['60', '1 óra'], ['360', '6 óra'], ['1440', '24 óra']]
const levels = [['', 'Minden szint'], ['3', 'ERROR'], ['4', 'WARN'], ['6', 'INFO'], ['7', 'DEBUG']]
</script>
<template>
  <form class="flex flex-wrap items-end gap-2 border-b border-zinc-800 bg-zinc-900 p-3 text-sm" @submit.prevent="emit('search')">
    <input v-model="logs.filters.q" placeholder='Szöveg… ("idézőjel" = pontos részlet)' class="min-w-64 flex-1 rounded bg-zinc-800 px-3 py-1.5" />
    <select v-model="logs.filters.last" class="rounded bg-zinc-800 px-2 py-1.5">
      <option v-for="[v, l] in lastOptions" :key="v" :value="v">{{ l }}</option>
    </select>
    <template v-if="!logs.filters.last">
      <input v-model="logs.filters.from" type="datetime-local" class="rounded bg-zinc-800 px-2 py-1.5" />
      <input v-model="logs.filters.to" type="datetime-local" class="rounded bg-zinc-800 px-2 py-1.5" />
    </template>
    <select v-model="logs.filters.source" class="rounded bg-zinc-800 px-2 py-1.5">
      <option value="">Minden forrás</option>
      <option v-for="s in logs.sources" :key="s" :value="s">{{ s }}</option>
    </select>
    <select v-model="logs.filters.tag" class="rounded bg-zinc-800 px-2 py-1.5">
      <option value="">Minden tag</option>
      <option v-for="t in logs.tags" :key="t" :value="t">{{ t }}</option>
    </select>
    <select v-model="logs.filters.level" class="rounded bg-zinc-800 px-2 py-1.5">
      <option v-for="[v, l] in levels" :key="v" :value="v">{{ l }}</option>
    </select>
    <button class="rounded bg-emerald-600 px-4 py-1.5 font-medium hover:bg-emerald-500" :disabled="logs.loading">Keresés</button>
    <AutoRefresh @tick="emit('search')" />
  </form>
</template>
