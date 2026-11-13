<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { api, type Account } from '../lib/api'
import { formatDate } from '../lib/format'

const account = ref<Account | null>(null)
const error = ref('')
const emailForm = reactive({ email: '', busy: false, message: '', error: '' })
const passwordForm = reactive({ current: '', next: '', confirm: '', busy: false, message: '', error: '' })

const emailChanged = computed(() => emailForm.email.trim() !== (account.value?.email ?? ''))
const passwordMismatch = computed(() => passwordForm.confirm !== '' && passwordForm.next !== passwordForm.confirm)
const passwordValid = computed(() =>
  passwordForm.current !== '' && passwordForm.next.length >= 8 && passwordForm.next === passwordForm.confirm)

onMounted(async () => {
  try {
    account.value = await api.account()
    emailForm.email = account.value.email ?? ''
  } catch (e) {
    error.value = (e as Error).message
  }
})

async function saveEmail() {
  Object.assign(emailForm, { busy: true, message: '', error: '' })
  try {
    account.value = await api.changeEmail(emailForm.email.trim() || null)
    emailForm.message = account.value.email ? 'E-Mail-Adresse gespeichert.' : 'E-Mail-Adresse entfernt.'
  } catch (e) {
    emailForm.error = (e as Error).message
  } finally {
    emailForm.busy = false
  }
}

async function savePassword() {
  if (!passwordValid.value) return
  Object.assign(passwordForm, { busy: true, message: '', error: '' })
  try {
    await api.changePassword(passwordForm.current, passwordForm.next)
    Object.assign(passwordForm, {
      current: '', next: '', confirm: '',
      message: 'Passwort geändert. Auf allen anderen Geräten wurdest du abgemeldet.',
    })
  } catch (e) {
    passwordForm.error = (e as Error).message
  } finally {
    passwordForm.busy = false
  }
}
</script>

<template>
  <div class="stack narrow">
    <div class="page-header">
      <div>
        <h1>Konto</h1>
        <div v-if="account" class="muted">👤 {{ account.username }} · dabei seit {{ formatDate(account.createdAt.slice(0, 10)) }}</div>
      </div>
    </div>

    <p v-if="error" class="error">{{ error }}</p>

    <form class="card form" @submit.prevent="saveEmail">
      <h2>E-Mail-Adresse</h2>
      <p class="small muted">Wird nur gebraucht, um ein vergessenes Passwort per Mail zurückzusetzen.</p>
      <label class="field">
        E-Mail
        <input v-model="emailForm.email" type="email" autocomplete="email" maxlength="254" placeholder="name@beispiel.de" />
      </label>
      <p v-if="emailForm.error" class="error">{{ emailForm.error }}</p>
      <p v-if="emailForm.message" class="notice">{{ emailForm.message }}</p>
      <div class="actions">
        <button type="submit" class="primary" :disabled="!emailChanged || emailForm.busy">Speichern</button>
      </div>
    </form>

    <form class="card form" @submit.prevent="savePassword">
      <h2>Passwort ändern</h2>
      <input type="text" autocomplete="username" :value="account?.username" hidden />
      <label class="field">
        Aktuelles Passwort
        <input v-model="passwordForm.current" type="password" autocomplete="current-password" required maxlength="72" />
      </label>
      <label class="field">
        Neues Passwort
        <input v-model="passwordForm.next" type="password" autocomplete="new-password" required maxlength="72" />
        <span class="small">Mindestens 8 Zeichen</span>
      </label>
      <label class="field">
        Neues Passwort wiederholen
        <input v-model="passwordForm.confirm" type="password" autocomplete="new-password" required maxlength="72" />
        <span v-if="passwordMismatch" class="small mismatch">Passwörter stimmen nicht überein</span>
      </label>
      <p v-if="passwordForm.error" class="error">{{ passwordForm.error }}</p>
      <p v-if="passwordForm.message" class="notice">{{ passwordForm.message }}</p>
      <div class="actions">
        <button type="submit" class="primary" :disabled="!passwordValid || passwordForm.busy">Passwort ändern</button>
      </div>
    </form>
  </div>
</template>

<style scoped>
.narrow { max-width: 560px; }
.form { display: flex; flex-direction: column; gap: 14px; }
.form h2, .form p { margin: 0; }
.actions { display: flex; justify-content: flex-end; }
.mismatch { color: var(--danger); }
</style>
