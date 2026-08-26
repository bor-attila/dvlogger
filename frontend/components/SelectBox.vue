<script setup lang="ts">
import { Listbox, ListboxButton, ListboxOption, ListboxOptions } from '@headlessui/vue'

export interface SelectOption { value: string | number; label: string }
const props = defineProps<{ modelValue: string | number; options: SelectOption[]; label?: string }>()
const emit = defineEmits<{ 'update:modelValue': [value: string | number] }>()
const current = computed(() => props.options.find(o => o.value === props.modelValue)?.label ?? String(props.modelValue))
</script>
<template>
  <Listbox :model-value="modelValue" @update:model-value="v => emit('update:modelValue', v)">
    <div class="relative">
      <ListboxButton
        class="flex min-w-36 items-center justify-between gap-3 rounded-lg border border-zinc-800 bg-zinc-900 px-3 py-2 text-left text-sm text-zinc-100 transition hover:border-zinc-700 focus:outline-none focus-visible:ring-2 focus-visible:ring-emerald-500/60">
        <span class="truncate"><span v-if="label" class="mr-1.5 text-zinc-500">{{ label }}</span>{{ current }}</span>
        <svg class="size-4 shrink-0 text-zinc-500" viewBox="0 0 20 20" fill="currentColor" aria-hidden="true">
          <path fill-rule="evenodd" d="M5.23 7.21a.75.75 0 0 1 1.06.02L10 11.17l3.71-3.94a.75.75 0 1 1 1.08 1.04l-4.25 4.5a.75.75 0 0 1-1.08 0l-4.25-4.5a.75.75 0 0 1 .02-1.06Z" clip-rule="evenodd" />
        </svg>
      </ListboxButton>
      <transition leave-active-class="transition duration-100 ease-in" leave-from-class="opacity-100" leave-to-class="opacity-0">
        <ListboxOptions
          class="absolute z-20 mt-1.5 max-h-64 w-full min-w-40 overflow-auto rounded-lg border border-zinc-800 bg-zinc-900 py-1 text-sm shadow-xl shadow-black/40 focus:outline-none">
          <ListboxOption v-for="o in options" :key="String(o.value)" v-slot="{ active, selected }" :value="o.value" as="template">
            <li class="flex cursor-pointer items-center justify-between px-3 py-1.5 transition"
                :class="[active ? 'bg-zinc-800 text-zinc-50' : 'text-zinc-300', selected && 'text-emerald-300']">
              <span class="truncate">{{ o.label }}</span>
              <svg v-if="selected" class="size-4 shrink-0" viewBox="0 0 20 20" fill="currentColor" aria-hidden="true">
                <path fill-rule="evenodd" d="M16.7 5.3a1 1 0 0 1 0 1.4l-8 8a1 1 0 0 1-1.4 0l-4-4a1 1 0 1 1 1.4-1.4L8 12.6l7.3-7.3a1 1 0 0 1 1.4 0Z" clip-rule="evenodd" />
              </svg>
            </li>
          </ListboxOption>
        </ListboxOptions>
      </transition>
    </div>
  </Listbox>
</template>
