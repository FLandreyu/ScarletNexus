import { expect, it } from 'vitest'
import { percentage, studyTime } from './analytics'
it('does not invent a percentage when there is no comparable evidence', () => {
  expect(percentage(0, 0)).toBe('暂无可比结果')
  expect(percentage(1, 2)).toBe('50.0%')
  expect(studyTime(5400000)).toBe('1.50 小时')
})
