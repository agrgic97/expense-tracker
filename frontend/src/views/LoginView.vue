<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { api } from '../lib/api'
import { setSession } from '../lib/auth'

const route = useRoute()
const router = useRouter()

const mode = ref<'login' | 'register'>('login')
const registrationEnabled = ref(false)
const form = reactive({ username: '', password: '', confirm: '' })
const error = ref('')
const busy = ref(false)

const isRegister = computed(() => mode.value === 'register')
const mismatch = computed(() => isRegister.value && form.confirm !== '' && form.password !== form.confirm)
const valid = computed(() => {
  if (!form.username.trim() || !form.password) return false
  if (!isRegister.value) return true
  return form.password.length >= 8 && form.password === form.confirm
})

onMounted(async () => {
  try {
    registrationEnabled.value = (await api.authConfig()).registrationEnabled
  } catch {
    registrationEnabled.value = false
  }
})

function switchMode() {
  mode.value = isRegister.value ? 'login' : 'register'
  error.value = ''
  form.confirm = ''
}

async function submit() {
  if (!valid.value) return
  busy.value = true
  error.value = ''
  try {
    const credentials = { username: form.username.trim(), password: form.password }
    setSession(isRegister.value ? await api.register(credentials) : await api.login(credentials))
    const redirect = typeof route.query.redirect === 'string' && route.query.redirect.startsWith('/')
      ? route.query.redirect : '/'
    router.replace(redirect)
  } catch (e) {
    error.value = (e as Error).message
  } finally {
    busy.value = false
  }
}
</script>

<template>
  <div class="login-page">
    <form class="card login" @submit.prevent="submit">
      <div class="brand">💶</div>
      <h1>{{ isRegister ? 'Konto erstellen' : 'Anmelden' }}</h1>
      <p class="muted small">Ausgaben-Tracker</p>

      <label class="field">
        Benutzername
        <input v-model="form.username" autocomplete="username" autofocus required maxlength="50" />
      </label>
      <label class="field">
        Passwort
        <input v-model="form.password" type="password" required maxlength="72"
               :autocomplete="isRegister ? 'new-password' : 'current-password'" />
        <span v-if="isRegister" class="small">Mindestens 8 Zeichen</span>
      </label>
      <label v-if="isRegister" class="field">
        Passwort wiederholen
        <input v-model="form.confirm" type="password" autocomplete="new-password" required maxlength="72" />
        <span v-if="mismatch" class="small mismatch">Passwörter stimmen nicht überein</span>
      </label>

      <p v-if="error" class="error">{{ error }}</p>

      <button type="submit" class="primary submit" :disabled="!valid || busy">
        {{ busy ? 'Bitte warten…' : isRegister ? 'Registrieren' : 'Anmelden' }}
      </button>

      <p v-if="registrationEnabled || isRegister" class="small muted switch">
        {{ isRegister ? 'Schon ein Konto?' : 'Noch kein Konto?' }}
        <button type="button" class="link" @click="switchMode">
          {{ isRegister ? 'Anmelden' : 'Registrieren' }}
        </button>
      </p>
    </form>
  </div>
</template>

<style scoped>
.login-page { min-height: 100vh; display: grid; place-items: center; padding: 16px; }
.login { width: min(380px, 100%); display: flex; flex-direction: column; gap: 14px; padding: 28px; }
.brand { font-size: 2.2rem; line-height: 1; }
.login p { margin: -10px 0 4px; }
.login .error { margin: 0; }
.submit { justify-content: center; padding: 10px; margin-top: 4px; }
.switch { margin: 0; text-align: center; }
.mismatch { color: var(--danger); }
.link { border: none; background: none; padding: 0; color: var(--accent); font-weight: 600; }
.link:hover { background: none; text-decoration: underline; }
</style>
