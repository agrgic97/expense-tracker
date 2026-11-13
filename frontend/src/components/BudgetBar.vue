<script setup lang="ts">
import { computed } from 'vue'

const props = defineProps<{
  percent: number
  color: string
  /** Share of the month that has passed; drawn as a marker so overspending early in the month stands out */
  elapsedPercent?: number
}>()

/** ok below 80 %, warn up to 100 %, over beyond */
const level = computed(() => (props.percent > 100 ? 'over' : props.percent >= 80 ? 'warn' : 'ok'))
const showMarker = computed(() => props.elapsedPercent !== undefined && props.elapsedPercent > 0 && props.elapsedPercent < 100)
</script>

<template>
  <div class="bar" :class="level" role="progressbar" :aria-valuenow="percent" aria-valuemin="0" aria-valuemax="100">
    <div class="fill" :style="{ width: `${Math.min(percent, 100)}%`, background: level === 'ok' ? color : undefined }" />
    <div v-if="showMarker" class="marker" :style="{ left: `${elapsedPercent}%` }" title="Heute" />
  </div>
</template>

<style scoped>
.bar { position: relative; height: 10px; background: var(--surface-2); border-radius: 99px; overflow: hidden; }
.fill { height: 100%; border-radius: 99px; transition: width 0.3s; }
.warn .fill { background: var(--warning); }
.over .fill { background: var(--danger); }
.marker { position: absolute; top: 0; bottom: 0; width: 2px; background: var(--text); opacity: 0.45; }
</style>
