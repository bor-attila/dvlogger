<script setup lang="ts">
const logs = useLogsStore()
const emit = defineEmits<{ tick: [] }>()
const options = [
  { value: 0, label: 'off' }, { value: 5, label: '5 s' }, { value: 10, label: '10 s' },
  { value: 30, label: '30 s' }, { value: 60, label: '60 s' },
]
let timer: ReturnType<typeof setInterval> | null = null
watch(() => logs.refreshSec, (sec) => {
  if (timer) clearInterval(timer); timer = null
  if (sec > 0) timer = setInterval(() => emit('tick'), sec * 1000)
}, { immediate: true })
onUnmounted(() => { if (timer) clearInterval(timer) })
</script>
<template>
  <SelectBox v-model="logs.refreshSec" :options="options" label="Refresh" />
</template>
