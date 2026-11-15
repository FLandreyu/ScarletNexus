<script setup lang="ts">
import { computed } from 'vue'
import katex from 'katex'
const props = defineProps<{ text: string }>()
const parts = computed(() => {
  const result: { text: string; html?: string }[] = []
  const source = props.text.slice(0, 60000)
  const pattern = /\$\$[\s\S]*?\$\$|\$[^$\n]+\$|\\\([\s\S]*?\\\)/g
  let from = 0,
    count = 0
  for (const match of source.matchAll(pattern)) {
    if (++count > 200) break
    const at = match.index!
    result.push({ text: source.slice(from, at) })
    const token = match[0],
      display = token.startsWith('$$'),
      math = token.slice(
        display || token.startsWith('\\(') ? 2 : 1,
        display || token.endsWith('\\)') ? -2 : -1,
      )
    try {
      if (math.length > 2000) throw new Error('Math fragment too long')
      result.push({
        text: token,
        html: katex.renderToString(math, {
          displayMode: display,
          trust: false,
          strict: 'error',
          throwOnError: true,
          maxExpand: 100,
          maxSize: 20,
          macros: {},
        }),
      })
    } catch {
      result.push({ text: token })
    }
    from = at + token.length
  }
  result.push({
    text:
      source.slice(from) + (props.text.length > 60000 ? '\n[显示已截断]' : ''),
  })
  return result
})
</script>
<template>
  <div class="math-text">
    <template v-for="(part, i) in parts" :key="i"
      ><span v-if="part.html" class="math-fragment" v-html="part.html" /><span
        v-else
        >{{ part.text }}</span
      ></template
    >
  </div>
</template>
