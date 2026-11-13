<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { api, type Category, type CategoryKind, type CategoryRule } from '../lib/api'
import { expenseModal } from '../lib/expenseModal'

const categories = ref<Category[]>([])
const rules = ref<CategoryRule[]>([])
const error = ref('')
const notice = ref('')
const draft = reactive({ name: '', color: '#4f46e5', kind: 'EXPENSE' as CategoryKind })
const editing = reactive({ id: null as number | null, name: '', color: '', kind: 'EXPENSE' as CategoryKind })
const ruleDraft = reactive({ pattern: '', categoryId: null as number | null })
const editingRule = reactive({ id: null as number | null, pattern: '', categoryId: null as number | null })

const groups = computed(() => [
  { kind: 'EXPENSE' as const, title: 'Ausgaben', items: categories.value.filter((c) => c.kind === 'EXPENSE') },
  { kind: 'INCOME' as const, title: 'Einnahmen', items: categories.value.filter((c) => c.kind === 'INCOME') },
])

async function load() {
  try {
    ;[categories.value, rules.value] = await Promise.all([api.categories(), api.rules()])
    if (ruleDraft.categoryId === null) ruleDraft.categoryId = categories.value[0]?.id ?? null
  } catch (e) {
    error.value = (e as Error).message
  }
}

async function run(action: () => Promise<unknown>) {
  error.value = ''
  notice.value = ''
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
    await api.createCategory({ name: draft.name.trim(), color: draft.color, kind: draft.kind })
    draft.name = ''
  })
}

function startEdit(c: Category) {
  Object.assign(editing, { id: c.id, name: c.name, color: c.color, kind: c.kind })
}

function saveEdit() {
  const id = editing.id!
  run(async () => {
    await api.updateCategory(id, { name: editing.name.trim(), color: editing.color, kind: editing.kind })
    editing.id = null
  })
}

function remove(c: Category) {
  run(() => api.deleteCategory(c.id))
}

function createRule() {
  if (!ruleDraft.pattern.trim() || ruleDraft.categoryId === null) return
  run(async () => {
    await api.createRule({ pattern: ruleDraft.pattern.trim(), categoryId: ruleDraft.categoryId! })
    ruleDraft.pattern = ''
  })
}

function startEditRule(r: CategoryRule) {
  Object.assign(editingRule, { id: r.id, pattern: r.pattern, categoryId: r.categoryId })
}

function saveRule() {
  const id = editingRule.id!
  run(async () => {
    await api.updateRule(id, { pattern: editingRule.pattern.trim(), categoryId: editingRule.categoryId! })
    editingRule.id = null
  })
}

function removeRule(r: CategoryRule) {
  run(() => api.deleteRule(r.id))
}

function applyRule(r: CategoryRule) {
  run(async () => {
    const { updated } = await api.applyRule(r.id)
    notice.value = updated === 0
      ? `Keine bestehenden Buchungen mit „${r.pattern}“ zum Umsortieren gefunden.`
      : `${updated} Buchung(en) mit „${r.pattern}“ nach „${r.categoryName}“ verschoben.`
    if (updated > 0) expenseModal.version++
  })
}

onMounted(load)
</script>

<template>
  <div class="stack">
    <div class="page-header"><h1>Kategorien</h1></div>

    <form class="card row add" @submit.prevent="create">
      <input v-model="draft.color" type="color" title="Farbe" />
      <input v-model="draft.name" class="grow" maxlength="50" placeholder="Neue Kategorie…" />
      <select v-model="draft.kind" title="Typ">
        <option value="EXPENSE">Ausgabe</option>
        <option value="INCOME">Einnahme</option>
      </select>
      <button type="submit" class="primary" :disabled="!draft.name.trim()">Hinzufügen</button>
    </form>

    <p v-if="error" class="error">{{ error }}</p>
    <p v-if="notice" class="notice">{{ notice }}</p>

    <section v-for="group in groups" :key="group.kind" class="card">
      <h2>{{ group.title }}</h2>
      <table v-if="group.items.length">
        <thead><tr><th>Kategorie</th><th class="num">Buchungen</th><th /></tr></thead>
        <tbody>
          <tr v-for="c in group.items" :key="c.id">
            <template v-if="editing.id === c.id">
              <td>
                <form class="row" @submit.prevent="saveEdit">
                  <input v-model="editing.color" type="color" />
                  <input v-model="editing.name" class="grow" maxlength="50" autofocus />
                  <select v-model="editing.kind" :disabled="c.expenseCount > 0"
                          :title="c.expenseCount > 0 ? 'Typ nur änderbar, solange die Kategorie leer ist' : 'Typ'">
                    <option value="EXPENSE">Ausgabe</option>
                    <option value="INCOME">Einnahme</option>
                  </select>
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
      <div v-else class="empty">Noch keine {{ group.title }}-Kategorien.</div>
    </section>

    <section class="card">
      <h2>Regeln für die automatische Zuordnung</h2>
      <p class="small muted intro">
        Enthält die Beschreibung einer Buchung den Suchtext (Groß-/Kleinschreibung egal), wird die Kategorie
        beim Erfassen und beim CSV-Import vorgeschlagen. Passen mehrere Regeln, gewinnt der längste Suchtext.
      </p>
      <form class="row add" @submit.prevent="createRule">
        <input v-model="ruleDraft.pattern" class="grow" maxlength="100" placeholder="Beschreibung enthält… z. B. REWE" />
        <span class="muted">→</span>
        <select v-model="ruleDraft.categoryId">
          <optgroup v-for="group in groups" :key="group.kind" :label="group.title">
            <option v-for="c in group.items" :key="c.id" :value="c.id">{{ c.name }}</option>
          </optgroup>
        </select>
        <button type="submit" class="primary" :disabled="!ruleDraft.pattern.trim() || ruleDraft.categoryId === null">
          Regel anlegen
        </button>
      </form>
      <table v-if="rules.length" class="rules">
        <thead><tr><th>Beschreibung enthält</th><th>Kategorie</th><th /></tr></thead>
        <tbody>
          <tr v-for="r in rules" :key="r.id">
            <template v-if="editingRule.id === r.id">
              <td colspan="2">
                <form class="row" @submit.prevent="saveRule">
                  <input v-model="editingRule.pattern" class="grow" maxlength="100" autofocus />
                  <select v-model="editingRule.categoryId">
                    <optgroup v-for="group in groups" :key="group.kind" :label="group.title">
                      <option v-for="c in group.items" :key="c.id" :value="c.id">{{ c.name }}</option>
                    </optgroup>
                  </select>
                </form>
              </td>
              <td class="actions">
                <button class="primary" @click="saveRule">Speichern</button>
                <button class="ghost" @click="editingRule.id = null">Abbrechen</button>
              </td>
            </template>
            <template v-else>
              <td><code>{{ r.pattern }}</code></td>
              <td><span class="chip"><span class="dot" :style="{ background: r.categoryColor }" />{{ r.categoryName }}</span></td>
              <td class="actions">
                <button class="ghost small" title="Auf bestehende Buchungen anwenden" @click="applyRule(r)">↻ Anwenden</button>
                <button class="ghost" title="Bearbeiten" @click="startEditRule(r)">✏️</button>
                <button class="ghost danger" title="Löschen" @click="removeRule(r)">🗑</button>
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
.intro { margin: -6px 0 14px; }
.rules { margin-top: 14px; }
code { font-size: 0.88rem; background: var(--surface-2); padding: 2px 8px; border-radius: 6px; }
@media (max-width: 760px) {
  .add { flex-wrap: wrap; }
}
</style>
