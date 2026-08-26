<script setup lang="ts">
const logs = useLogsStore()
const emit = defineEmits<{ tick: [] }>()
let timer: ReturnType<typeof setInterval> | null = null
watch(() => logs.refreshSec, (sec) => {
  if (timer) clearInterval(timer); timer = null
  if (sec > 0) timer = setInterval(() => emit('tick'), sec * 1000)
}, { immediate: true })
onUnmounted(() => { if (timer) clearInterval(timer) })
</script>
<template>
  <label class="flex items-center gap-1 text-zinc-400">
    Frissítés
    <select v-model.number="logs.refreshSec" class="rounded bg-zinc-800 px-2 py-1.5 text-zinc-100">
      <option :value="0">ki</option><option :value="5">5 mp</option><option :value="10">10 mp</option>
      <option :value="30">30 mp</option><option :value="60">60 mp</option>
    </select>
  </label>
</template>
