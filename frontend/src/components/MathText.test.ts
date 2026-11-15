import { mount } from '@vue/test-utils'
import { expect, test } from 'vitest'
import MathText from './MathText.vue'
test('renders math while leaving untrusted HTML and external math commands inert', () => {
  const wrapper = mount(MathText, {
    props: {
      text: '<img src=x onerror=alert(1)> $a^2$ $\\href{javascript:alert(1)}{click}$',
    },
  })
  expect(wrapper.find('.katex').exists()).toBe(true)
  expect(wrapper.find('img').exists()).toBe(false)
  expect(wrapper.find('a').exists()).toBe(false)
  expect(wrapper.text()).toContain('<img src=x onerror=alert(1)>')
})
test('does not turn math parsing failures into executable error HTML', () => {
  const wrapper = mount(MathText, {
    props: { text: '$\\badcommand{<script>alert(1)</script>}$' },
  })
  expect(wrapper.find('script').exists()).toBe(false)
  expect(wrapper.text()).toContain('<script>alert(1)</script>')
})
