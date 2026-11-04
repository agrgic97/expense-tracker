<script setup lang="ts">
defineProps<{ label: string; value: string; hint?: string }>()
</script>

<template>
  <div class="card kpi">
    <div class="muted small">{{ label }}</div>
    <div class="value">{{ value }}</div>
    <div class="small muted hint"><slot>{{ hint }}</slot></div>
  </div>
</template>

<style scoped>
.kpi { display: flex; flex-direction: column; gap: 4px; }
.value { font-size: 1.6rem; font-weight: 700; font-variant-numeric: tabular-nums; letter-spacing: -0.01em; }
.hint { min-height: 1.4em; display: flex; align-items: center; gap: 6px; flex-wrap: wrap; }
</style>
