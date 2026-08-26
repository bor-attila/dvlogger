<script setup lang="ts">
const props = defineProps<{ text: string; title?: string }>()
const copied = ref(false)
let timer: ReturnType<typeof setTimeout> | null = null
async function copy() {
  try { await navigator.clipboard.writeText(props.text) }
  catch {
    const ta = document.createElement('textarea'); ta.value = props.text; document.body.appendChild(ta); ta.select()
    document.execCommand('copy'); ta.remove()
  }
  copied.value = true
  if (timer) clearTimeout(timer)
  timer = setTimeout(() => { copied.value = false }, 1500)
}
onUnmounted(() => { if (timer) clearTimeout(timer) })
</script>
<template>
  <button type="button" :title="title ?? 'Copy to clipboard'" @click.stop="copy"
          class="inline-flex items-center gap-1 rounded-md px-2 py-1 text-xs text-zinc-400 transition hover:bg-zinc-800 hover:text-zinc-100 focus:outline-none focus-visible:ring-2 focus-visible:ring-emerald-500/60">
    <svg v-if="!copied" class="size-4" viewBox="0 0 20 20" fill="currentColor" aria-hidden="true">
      <path d="M7 3.5A1.5 1.5 0 0 1 8.5 2h3.879a1.5 1.5 0 0 1 1.06.44l3.122 3.12A1.5 1.5 0 0 1 17 6.622V12.5a1.5 1.5 0 0 1-1.5 1.5h-1v-3.379a3 3 0 0 0-.879-2.121L10.5 5.379A3 3 0 0 0 8.379 4.5H7v-1Z" />
      <path d="M4.5 6A1.5 1.5 0 0 0 3 7.5v9A1.5 1.5 0 0 0 4.5 18h7a1.5 1.5 0 0 0 1.5-1.5v-5.879a1.5 1.5 0 0 0-.44-1.06L9.44 6.44A1.5 1.5 0 0 0 8.378 6H4.5Z" />
    </svg>
    <svg v-else class="size-4 text-emerald-400" viewBox="0 0 20 20" fill="currentColor" aria-hidden="true">
      <path fill-rule="evenodd" d="M16.7 5.3a1 1 0 0 1 0 1.4l-8 8a1 1 0 0 1-1.4 0l-4-4a1 1 0 1 1 1.4-1.4L8 12.6l7.3-7.3a1 1 0 0 1 1.4 0Z" clip-rule="evenodd" />
    </svg>
    <span>{{ copied ? 'Copied' : 'Copy' }}</span>
  </button>
</template>
