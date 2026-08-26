<script setup lang="ts">
const auth = useAuthStore()
const GITHUB = 'https://github.com/bor-attila/dvlogger'
</script>
<template>
  <footer v-if="auth.footerText" class="py-6 text-center text-xs text-zinc-500">
    <a :href="GITHUB" target="_blank" rel="noopener noreferrer"
       class="group inline-flex items-center gap-1.5 rounded-md px-2 py-1 transition hover:text-zinc-300">
      <span>Vibed with Love</span>
      <svg class="size-3.5 text-rose-500 transition group-hover:scale-110" viewBox="0 0 20 20" fill="currentColor" aria-label="love">
        <path d="m9.653 16.915-.005-.003-.019-.01a20.759 20.759 0 0 1-1.162-.682 22.045 22.045 0 0 1-2.582-1.9C4.045 12.733 2 10.352 2 7.5a4.5 4.5 0 0 1 8-2.828A4.5 4.5 0 0 1 18 7.5c0 2.852-2.044 5.233-3.885 6.82a22.049 22.049 0 0 1-3.744 2.582l-.019.01-.005.003h-.002a.739.739 0 0 1-.69.001l-.002-.001Z" />
      </svg>
      <span>by Attila B.</span>
    </a>
  </footer>
</template>
