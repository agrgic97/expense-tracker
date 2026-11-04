<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue'
import { api, type Category } from '../lib/api'

const categories = ref<Category[]>([])
const error = ref('')
const draft = reactive({ name: '', color: '#4f46e5' })
const editing = reactive({ id: null as number | null, name: '', color: '' })

async function load() {
  try {
    categories.value = await api.categories()
  } catch (e) {
    error.value = (e as Error).message
  }
}

async function run(action: () => Promise<unknown>) {
  error.value = ''
  try {
    await action()
    await load()
  } catch (e) {
    error.value = (e as Error).message
  }
}

function create() {
  if (!draft.name.trim()) return
  run(async () => {
    await api.createCategory({ name: draft.name.trim(), color: draft.color })
    draft.name = ''
  })
}

function startEdit(c: Category) {
  Object.assign(editing, { id: c.id, name: c.name, color: c.color })
}

function saveEdit() {
  const id = editing.id!
  run(async () => {
    await api.updateCategory(id, { name: editing.name.trim(), color: editing.color })
    editing.id = null
  })
}

function remove(c: Category) {
  run(() => api.deleteCategory(c.id))
}

onMounted(load)
</script>

<template>
  <div class="stack">
    <div class="page-header"><h1>Kategorien</h1></div>

    <form class="card row add" @submit.prevent="create">
      <input v-model="draft.color" type="color" title="Farbe" />
      <input v-model="draft.name" class="grow" maxlength="50" placeholder="Neue Kategorie…" />
      <button type="submit" class="primary" :disabled="!draft.name.trim()">Hinzufügen</button>
    </form>

    <p v-if="error" class="error">{{ error }}</p>

    <section class="card">
      <table>
        <thead><tr><th>Kategorie</th><th class="num">Ausgaben</th><th /></tr></thead>
        <tbody>
          <tr v-for="c in categories" :key="c.id">
            <template v-if="editing.id === c.id">
              <td>
                <form class="row" @submit.prevent="saveEdit">
                  <input v-model="editing.color" type="color" />
                  <input v-model="editing.name" class="grow" maxlength="50" autofocus />
                </form>
              </td>
              <td class="num muted">{{ c.expenseCount }}</td>
              <td class="actions">
                <button class="primary" @click="saveEdit">Speichern</button>
                <button class="ghost" @click="editing.id = null">Abbrechen</button>
              </td>
            </template>
            <template v-else>
              <td><span class="chip"><span class="dot" :style="{ background: c.color }" />{{ c.name }}</span></td>
              <td class="num muted">{{ c.expenseCount }}</td>
              <td class="actions">
                <button class="ghost" title="Bearbeiten" @click="startEdit(c)">✏️</button>
                <button class="ghost danger" :disabled="c.expenseCount > 0"
                        :title="c.expenseCount > 0 ? 'Wird noch verwendet' : 'Löschen'" @click="remove(c)">🗑</button>
              </td>
            </template>
          </tr>
        </tbody>
      </table>
    </section>
  </div>
</template>

<style scoped>
.add { flex-wrap: nowrap; }
.grow { flex: 1; }
.actions { white-space: nowrap; text-align: right; width: 1%; }
</style>
