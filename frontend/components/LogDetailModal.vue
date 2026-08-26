<script setup lang="ts">
import { Dialog, DialogPanel, DialogTitle, TransitionChild, TransitionRoot } from '@headlessui/vue'
const logs = useLogsStore()
const open = computed(() => logs.selected !== null)
const pretty = computed(() => logs.selected ? JSON.stringify(logs.selected.raw, null, 2) : '')
const levelName = (l: number | null) => l == null ? '-' : ({ 0: 'EMERG', 1: 'ALERT', 2: 'CRIT', 3: 'ERROR', 4: 'WARN', 5: 'NOTICE', 6: 'INFO', 7: 'DEBUG' } as any)[l] ?? String(l)
const close = () => { logs.selected = null }
</script>
<template>
  <TransitionRoot :show="open" as="template">
    <Dialog class="relative z-50" @close="close">
      <TransitionChild as="template" enter="duration-150 ease-out" enter-from="opacity-0" enter-to="opacity-100"
                       leave="duration-100 ease-in" leave-from="opacity-100" leave-to="opacity-0">
        <div class="fixed inset-0 bg-black/70 backdrop-blur-sm" />
      </TransitionChild>
      <div class="fixed inset-0 overflow-y-auto">
        <div class="flex min-h-full items-center justify-center p-6">
          <TransitionChild as="template" enter="duration-150 ease-out" enter-from="opacity-0 scale-95" enter-to="opacity-100 scale-100"
                           leave="duration-100 ease-in" leave-from="opacity-100 scale-100" leave-to="opacity-0 scale-95">
            <DialogPanel v-if="logs.selected"
                         class="w-full max-w-3xl rounded-2xl border border-zinc-800 bg-zinc-900 p-6 text-zinc-100 shadow-2xl shadow-black/50">
              <div class="mb-5 flex items-start justify-between gap-4">
                <div>
                  <DialogTitle class="text-base font-semibold">Log entry</DialogTitle>
                  <p class="mt-0.5 font-mono text-xs text-zinc-500">{{ logs.selected.id }}</p>
                </div>
                <button type="button" class="rounded-md p-1.5 text-zinc-400 transition hover:bg-zinc-800 hover:text-zinc-100" title="Close" @click="close">
                  <svg class="size-5" viewBox="0 0 20 20" fill="currentColor" aria-hidden="true">
                    <path d="M6.28 5.22a.75.75 0 0 0-1.06 1.06L8.94 10l-3.72 3.72a.75.75 0 1 0 1.06 1.06L10 11.06l3.72 3.72a.75.75 0 1 0 1.06-1.06L11.06 10l3.72-3.72a.75.75 0 0 0-1.06-1.06L10 8.94 6.28 5.22Z" />
                  </svg>
                </button>
              </div>

              <dl class="mb-5 grid grid-cols-2 gap-x-6 gap-y-3 text-sm sm:grid-cols-3">
                <div><dt class="text-xs uppercase tracking-wide text-zinc-500">Time</dt><dd class="mt-0.5">{{ new Date(logs.selected.ts).toLocaleString() }}</dd></div>
                <div><dt class="text-xs uppercase tracking-wide text-zinc-500">Level</dt><dd class="mt-0.5">{{ levelName(logs.selected.level) }}</dd></div>
                <div><dt class="text-xs uppercase tracking-wide text-zinc-500">Source</dt><dd class="mt-0.5 text-emerald-300">{{ logs.selected.source }}</dd></div>
                <div><dt class="text-xs uppercase tracking-wide text-zinc-500">Host</dt><dd class="mt-0.5">{{ logs.selected.host }}</dd></div>
                <div class="sm:col-span-2"><dt class="text-xs uppercase tracking-wide text-zinc-500">Tags</dt>
                  <dd class="mt-1 flex flex-wrap gap-1.5">
                    <span v-for="t in logs.selected.tags" :key="t" class="rounded-md bg-zinc-800 px-2 py-0.5 text-xs text-zinc-300">{{ t }}</span>
                    <span v-if="!logs.selected.tags.length" class="text-zinc-500">-</span>
                  </dd></div>
              </dl>

              <section class="mb-4">
                <div class="mb-1.5 flex items-center justify-between">
                  <h3 class="text-xs uppercase tracking-wide text-zinc-500">Message</h3>
                  <CopyButton :text="logs.selected.message" title="Copy message" />
                </div>
                <p class="max-h-60 overflow-auto whitespace-pre-wrap break-words rounded-lg bg-zinc-950 p-3 font-mono text-xs leading-relaxed">{{ logs.selected.message }}</p>
              </section>

              <section>
                <div class="mb-1.5 flex items-center justify-between">
                  <h3 class="text-xs uppercase tracking-wide text-zinc-500">Raw</h3>
                  <CopyButton :text="pretty" title="Copy raw JSON" />
                </div>
                <pre class="max-h-72 overflow-auto rounded-lg bg-zinc-950 p-3 font-mono text-[11px] leading-relaxed text-zinc-300">{{ pretty }}</pre>
              </section>
            </DialogPanel>
          </TransitionChild>
        </div>
      </div>
    </Dialog>
  </TransitionRoot>
</template>
