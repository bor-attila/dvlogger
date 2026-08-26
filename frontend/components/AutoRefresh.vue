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
    Refresh
    <select v-model.number="logs.refreshSec" class="rounded bg-zinc-800 px-2 py-1.5 text-zinc-100">
      <option :value="0">off</option><option :value="5">5 s</option><option :value="10">10 s</option>
      <option :value="30">30 s</option><option :value="60">60 s</option>
    </select>
  </label>
</template>
