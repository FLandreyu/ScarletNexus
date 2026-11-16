<script setup lang="ts">
import { onMounted, onBeforeUnmount, ref, watch } from 'vue'
import { init, use, type EChartsCoreOption, type ECharts } from 'echarts/core'
import { BarChart, LineChart } from 'echarts/charts'
import {
  GridComponent,
  TooltipComponent,
  LegendComponent,
  AriaComponent,
} from 'echarts/components'
import { SVGRenderer } from 'echarts/renderers'
import type { AnalyticsReport } from '../types/analytics'
use([
  BarChart,
  LineChart,
  GridComponent,
  TooltipComponent,
  LegendComponent,
  AriaComponent,
  SVGRenderer,
])
const props = defineProps<{ report: AnalyticsReport }>(),
  element = ref<HTMLDivElement | null>(null),
  mode = ref('days')
let chart: ECharts | undefined, observer: ResizeObserver | undefined
function render() {
  if (!chart) return
  const days = mode.value === 'days'
  const option: EChartsCoreOption = {
    backgroundColor: 'transparent',
    textStyle: { color: '#d0c4d8' },
    aria: {
      enabled: true,
      label: { description: '已结束学习时段的分钟统计，详细数值见下方表格' },
    },
    grid: { left: 60, right: 24, top: 45, bottom: 80, containLabel: true },
    tooltip: { trigger: 'axis', renderMode: 'richText' },
    legend: {
      textStyle: { color: '#d0c4d8' },
      data: [days ? '已结束学习分钟' : '技能投入分钟'],
    },
    xAxis: {
      type: 'category',
      data: days
        ? props.report.days.map((d) => d.date)
        : props.report.skills.map((s) => s.name),
      axisLabel: {
        rotate: 25,
        hideOverlap: true,
        width: 100,
        overflow: 'truncate',
      },
    },
    yAxis: { type: 'value', name: '分钟', min: 0 },
    series: [
      {
        name: days ? '已结束学习分钟' : '技能投入分钟',
        type: days ? 'line' : 'bar',
        data: days
          ? props.report.days.map(
              (d) => Math.round(d.studyMilliseconds / 600) / 100,
            )
          : props.report.skills.map(
              (s) => Math.round(s.totals.studyMilliseconds / 600) / 100,
            ),
        itemStyle: { color: '#ec91a8' },
        smooth: false,
      },
    ],
    animation: false,
  }
  chart.setOption(option, true)
}
watch(() => props.report, render)
watch(mode, render)
onMounted(() => {
  if (!element.value) return
  chart = init(element.value, undefined, { renderer: 'svg' })
  observer = new ResizeObserver(() => chart?.resize())
  observer.observe(element.value)
  render()
})
onBeforeUnmount(() => {
  observer?.disconnect()
  chart?.dispose()
  chart = undefined
})
</script>
<template>
  <section class="tree-panel">
    <h2>学习时间趋势</h2>
    <label for="analytics-chart-mode">图表范围</label
    ><select id="analytics-chart-mode" v-model="mode">
      <option value="days">按结束日期</option>
      <option value="skills">按技能</option>
    </select>
    <div
      ref="element"
      class="analytics-chart"
      role="img"
      aria-label="已结束学习时段的分钟统计，详细数值见下方表格"
    ></div>
    <p>图表与下方明细使用同一组真实数据，悬停可查看数值。</p>
  </section>
</template>
<style scoped>
.analytics-chart {
  height: 340px;
  min-width: 0;
  width: 100%;
}
select {
  max-width: 100%;
  margin: 8px 0;
  padding: 8px;
  background: #211e29;
  color: #eeeaf1;
  border: 1px solid #6e5e7a;
}
</style>
