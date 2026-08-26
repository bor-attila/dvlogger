<script setup lang="ts">
const auth = useAuthStore()
const user = ref(''); const password = ref(''); const error = ref('')
async function submit() {
  error.value = ''
  try { await auth.login(user.value, password.value); navigateTo('/') }
  catch { error.value = 'Hibás felhasználónév vagy jelszó' }
}
</script>
<template>
  <div class="flex min-h-screen items-center justify-center">
    <form class="w-80 space-y-4 rounded-lg border border-zinc-800 bg-zinc-900 p-6" @submit.prevent="submit">
      <h1 class="text-xl font-semibold">dvlogger</h1>
      <input v-model="user" placeholder="Felhasználó" class="w-full rounded bg-zinc-800 px-3 py-2" autofocus />
      <input v-model="password" type="password" placeholder="Jelszó" class="w-full rounded bg-zinc-800 px-3 py-2" />
      <p v-if="error" class="text-sm text-red-400">{{ error }}</p>
      <button class="w-full rounded bg-emerald-600 py-2 font-medium hover:bg-emerald-500">Belépés</button>
    </form>
  </div>
</template>
