<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { api } from '../lib/api'

const route = useRoute()

const token = computed(() => (typeof route.query.token === 'string' ? route.query.token : ''))
// /forgot-password asks for the address, /reset-password?token=… sets the new password
const isConfirm = computed(() => route.name === 'reset-password')
const form = reactive({ email: '', password: '', confirm: '' })
const error = ref('')
const busy = ref(false)
const done = ref(false)

const mismatch = computed(() => form.confirm !== '' && form.password !== form.confirm)
const valid = computed(() => isConfirm.value
  ? token.value !== '' && form.password.length >= 8 && form.password === form.confirm
  : form.email.trim() !== '')

watch(isConfirm, () => {
  error.value = ''
  done.value = false
})

async function submit() {
  if (!valid.value) return
  busy.value = true
  error.value = ''
  try {
    if (isConfirm.value) await api.confirmPasswordReset(token.value, form.password)
    else await api.requestPasswordReset(form.email.trim())
    done.value = true
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
      <div class="brand">🔑</div>
      <h1>{{ isConfirm ? 'Neues Passwort' : 'Passwort vergessen' }}</h1>

      <template v-if="done && isConfirm">
        <p class="notice">Dein Passwort wurde geändert. Alle bisherigen Anmeldungen wurden beendet.</p>
        <RouterLink to="/login" class="btn primary submit">Zur Anmeldung</RouterLink>
      </template>
      <template v-else-if="done">
        <p class="notice">
          Falls ein Konto mit dieser Adresse existiert, ist eine Mail mit einem Link unterwegs.
          Prüfe auch den Spam-Ordner.
        </p>
        <RouterLink to="/login" class="link small back">Zurück zur Anmeldung</RouterLink>
      </template>
      <template v-else-if="isConfirm && !token">
        <p class="error">Der Link ist unvollständig. Bitte öffne ihn direkt aus der Mail oder fordere einen neuen an.</p>
        <RouterLink to="/forgot-password" class="link small back">Neuen Link anfordern</RouterLink>
      </template>
      <template v-else>
        <p v-if="!isConfirm" class="muted small">
          Gib die E-Mail-Adresse deines Kontos ein. Du bekommst einen Link, mit dem du ein neues Passwort festlegst.
        </p>
        <label v-if="!isConfirm" class="field">
          E-Mail
          <input v-model="form.email" type="email" autocomplete="email" autofocus required maxlength="254" />
        </label>
        <template v-else>
          <label class="field">
            Neues Passwort
            <input v-model="form.password" type="password" autocomplete="new-password" autofocus required maxlength="72" />
            <span class="small">Mindestens 8 Zeichen</span>
          </label>
          <label class="field">
            Passwort wiederholen
            <input v-model="form.confirm" type="password" autocomplete="new-password" required maxlength="72" />
            <span v-if="mismatch" class="small mismatch">Passwörter stimmen nicht überein</span>
          </label>
        </template>

        <p v-if="error" class="error">{{ error }}</p>

        <button type="submit" class="primary submit" :disabled="!valid || busy">
          {{ busy ? 'Bitte warten…' : isConfirm ? 'Passwort speichern' : 'Link senden' }}
        </button>
        <RouterLink to="/login" class="link small back">Zurück zur Anmeldung</RouterLink>
      </template>
    </form>
  </div>
</template>

<style scoped>
.login-page { min-height: 100vh; display: grid; place-items: center; padding: 16px; }
.login { width: min(380px, 100%); display: flex; flex-direction: column; gap: 14px; padding: 28px; }
.brand { font-size: 2.2rem; line-height: 1; }
.login p { margin: 0; }
.submit { justify-content: center; padding: 10px; margin-top: 4px; }
.back { text-align: center; font-weight: 500; }
.mismatch { color: var(--danger); }
</style>
