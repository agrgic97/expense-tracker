import {
  ArcElement,
  BarController,
  BarElement,
  CategoryScale,
  Chart,
  DoughnutController,
  Filler,
  Legend,
  LinearScale,
  LineController,
  LineElement,
  PointElement,
  Tooltip,
} from 'chart.js'
import { money } from './format'

Chart.register(
  ArcElement, BarController, BarElement, CategoryScale, DoughnutController, Filler, Legend,
  LinearScale, LineController, LineElement, PointElement, Tooltip,
)

Chart.defaults.font.family = getComputedStyle(document.documentElement).getPropertyValue('--font').trim() || 'system-ui'
Chart.defaults.color = '#8a94a6'
Chart.defaults.borderColor = 'rgba(138, 148, 166, 0.18)'
Chart.defaults.plugins.tooltip.callbacks.label = (ctx) => {
  const value = typeof ctx.raw === 'number' ? ctx.raw : 0
  return ` ${ctx.dataset.label ? ctx.dataset.label + ': ' : ''}${money(value)}`
}

export const moneyAxis = {
  beginAtZero: true,
  ticks: { callback: (value: string | number) => money(Number(value)).replace(/,00\s/, ' ') },
}
