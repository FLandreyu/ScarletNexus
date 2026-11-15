<script setup lang="ts">
import { computed, defineAsyncComponent, onMounted, ref } from 'vue'
import { onBeforeRouteLeave, useRoute } from 'vue-router'
import { api, errorMessage } from '../api/client'
import { allPages } from '../api/learning'
import { uploadImage } from '../api/tutor'
import type { Task, Page } from '../types/learning'
const MathText = defineAsyncComponent(
  () => import('../components/MathText.vue'),
)
import TutorPanel from '../components/TutorPanel.vue'
interface Question {
  id: string
  title: string
  prompt: string
  questionType: string
  status: string
}
interface Attempt {
  id: string
  questionId: string
  submittedContent: string
  independent: boolean
  hintLevelAtSubmission: number
  createdAt: string
}
interface Review {
  id: string
  verdict: string
  reviewerType: string
  feedback: string
}
interface Evidence {
  id: string
  reviewId: string
  evidenceType: string
  verification: string
  outcome: string
  independent: boolean
  observedAt: string
}
interface Assessment {
  assessment: {
    id: string
    level: number
    assessorType: string
    confidence: string
    reason: string
    assessedAt: string
  }
  evidenceIds: string[]
}
interface Attachment {
  id: string
  taskId: string
  sizeBytes: number
  width: number
  height: number
  status: string
}
const taskId = String(useRoute().params.id),
  task = ref<Task | null>(null),
  error = ref(''),
  message = ref(''),
  busy = ref(false)
const questions = ref<Question[]>([]),
  selectedQuestion = ref(''),
  question = computed(() =>
    questions.value.find((q) => q.id === selectedQuestion.value),
  )
const attempts = ref<Page<Attempt>>({ items: [], total: 0, page: 0, size: 10 }),
  attemptPage = ref(0),
  selectedAttempt = ref<Attempt | null>(null),
  reviews = ref<Review[]>([]),
  detailAttachments = ref<Attachment[]>([]),
  hintLevel = ref(0)
const content = ref(''),
  declaredIndependent = ref(true),
  images = ref<Attachment[]>([]),
  selectedImages = ref<string[]>([]),
  file = ref<HTMLInputElement | null>(null)
const evidence = ref<Evidence[]>([]),
  assessments = ref<Assessment[]>([]),
  selectedEvidence = ref<string[]>([]),
  hintText = ref(''),
  showAuthor = ref(false)
const author = ref({
    title: '',
    prompt: '',
    questionType: 'PROOF',
    answerKey: '',
    hintText: '',
  }),
  review = ref({
    verdict: 'UNSURE',
    feedback: '',
    confirmed: false,
    tutorTurnId: null as string | null,
  })
const assessment = ref({ assessorType: 'RULE', level: 0, reason: '' })
let submitKey = crypto.randomUUID()
let previousQuestion = ''
async function run(action: () => Promise<void>) {
  if (busy.value) return
  busy.value = true
  error.value = ''
  message.value = ''
  try {
    await action()
  } catch (e) {
    error.value = errorMessage(e)
  } finally {
    busy.value = false
  }
}
async function load() {
  task.value = await api(`/learning/tasks/${taskId}`)
  ;[
    questions.value,
    attempts.value,
    images.value,
    evidence.value,
    assessments.value,
  ] = await Promise.all([
    allPages<Question>(`/practice/tasks/${taskId}/questions`),
    api<Page<Attempt>>(
      `/practice/tasks/${taskId}/attempts?page=${attemptPage.value}&size=10`,
    ),
    allPages<Attachment>(`/learning/tasks/${taskId}/attachments`),
    allPages<Evidence>(`/practice/tasks/${taskId}/evidence`),
    allPages<Assessment>(`/practice/tasks/${taskId}/assessments`),
  ])
  if (!selectedQuestion.value)
    selectedQuestion.value = questions.value[0]?.id ?? ''
  previousQuestion = selectedQuestion.value
  selectedImages.value = selectedImages.value.filter((id) =>
    images.value.some((image) => image.id === id && image.status === 'READY'),
  )
  if (selectedAttempt.value) await loadAttempt(selectedAttempt.value.id)
}
async function loadAttempt(id: string) {
  const value = await api<{
    attempt: Attempt
    attachments: Attachment[]
    currentHintLevel: number
  }>(`/practice/attempts/${id}`)
  selectedAttempt.value = value.attempt
  detailAttachments.value = value.attachments
  hintLevel.value = value.currentHintLevel
  reviews.value = await allPages<Review>(`/practice/attempts/${id}/reviews`)
}
async function chooseAttempt(id: string) {
  await run(async () => {
    hintText.value = ''
    review.value = {
      verdict: 'UNSURE',
      feedback: '',
      confirmed: false,
      tutorTurnId: null,
    }
    await loadAttempt(id)
  })
}
async function createQuestion() {
  await run(async () => {
    await api(`/practice/tasks/${taskId}/questions`, 'POST', author.value)
    author.value = {
      title: '',
      prompt: '',
      questionType: 'PROOF',
      answerKey: '',
      hintText: '',
    }
    showAuthor.value = false
    await load()
    message.value = '题目已保存。答案不会随练习读取返回。'
  })
}
async function submit() {
  await run(async () => {
    const value = await api<Attempt>(
      `/practice/tasks/${taskId}/attempts`,
      'POST',
      {
        questionId: selectedQuestion.value,
        content: content.value,
        declaredIndependent: declaredIndependent.value,
        attachmentIds: selectedImages.value,
      },
      { 'Idempotency-Key': submitKey },
    )
    submitKey = crypto.randomUUID()
    selectedAttempt.value = value
    content.value = ''
    selectedImages.value = []
    hintText.value = ''
    attemptPage.value = 0
    await load()
    message.value = '作答已保存。之后可以选择提示或请求复核。'
  })
}
async function hint(full: boolean) {
  await run(async () => {
    if (!selectedAttempt.value) return
    if (
      full &&
      !window.confirm(
        '查看完整解析会记录答案曝光，之后作答不再标记为未获帮助。确定查看？',
      )
    )
      return
    const value = await api<{ text: string }>(
      `/practice/attempts/${selectedAttempt.value.id}/hint`,
      'POST',
      { level: full ? 'FULL_SOLUTION' : 'ONE_HINT', confirmed: full },
    )
    hintText.value = value.text
    await loadAttempt(selectedAttempt.value.id)
  })
}
async function check() {
  await run(async () => {
    if (!selectedAttempt.value) return
    await api(
      `/practice/attempts/${selectedAttempt.value.id}/check`,
      'POST',
      undefined,
      { 'Idempotency-Key': crypto.randomUUID() },
    )
    await loadAttempt(selectedAttempt.value.id)
  })
}
async function saveReview() {
  await run(async () => {
    if (!selectedAttempt.value) return
    await api(
      `/practice/attempts/${selectedAttempt.value.id}/reviews`,
      'POST',
      review.value,
      { 'Idempotency-Key': crypto.randomUUID() },
    )
    await loadAttempt(selectedAttempt.value.id)
    message.value = '复核意见已追加，尚未自动生成证据。'
  })
}
async function confirmEvidence(id: string) {
  await run(async () => {
    await api(`/practice/reviews/${id}/evidence`, 'POST', { confirmed: true })
    await load()
    message.value = '证据已确认，能力评估仍需单独保存。'
  })
}
async function assess() {
  await run(async () => {
    await api(`/practice/tasks/${taskId}/assessments`, 'POST', {
      ...assessment.value,
      evidenceIds: selectedEvidence.value,
    })
    await load()
    message.value = '能力评估已追加保存。'
  })
}
async function upload() {
  await run(async () => {
    const image = file.value?.files?.[0]
    if (!image) return
    await uploadImage(taskId, image)
    if (file.value) file.value.value = ''
    await load()
    message.value = '图片已安全处理并保存，未发送给模型。'
  })
}
async function archiveImage(image: Attachment) {
  await run(async () => {
    await api(`/attachments/${image.id}`, 'PATCH', {
      status: image.status === 'READY' ? 'ARCHIVED' : 'READY',
    })
    await load()
  })
}
async function nextPage(delta: number) {
  await run(async () => {
    attemptPage.value += delta
    await load()
  })
}
function changeQuestion() {
  if (
    content.value &&
    !window.confirm('切换题目将清空尚未提交的作答，继续？')
  ) {
    selectedQuestion.value = previousQuestion
    return
  }
  previousQuestion = selectedQuestion.value
  content.value = ''
  selectedImages.value = []
  submitKey = crypto.randomUUID()
}
onBeforeRouteLeave(
  () => !content.value || window.confirm('作答尚未提交，确定离开？'),
)
onMounted(() => void run(load))
</script>
<template>
  <section class="workspace-heading">
    <p class="eyebrow">PRACTICE AND EVIDENCE</p>
    <h1>练习与能力证据</h1>
    <p>
      {{ task?.title }} ·
      独立提交与提示后的作答分别保留，不用学习完成率代替能力。
    </p>
    <div class="row-actions">
      <RouterLink :to="`/study/${taskId}`">返回学习工作台</RouterLink
      ><button :disabled="busy" @click="run(load)">刷新练习记录</button>
    </div>
  </section>
  <p v-if="error" role="alert" class="error-box">{{ error }}</p>
  <p v-if="message" role="status">{{ message }}</p>
  <fieldset class="learning-fieldset" :disabled="busy">
    <section class="tree-panel learning-section">
      <h2>练习题目</h2>
      <label for="practice-question">选择题目</label
      ><select
        id="practice-question"
        v-model="selectedQuestion"
        @change="changeQuestion"
      >
        <option value="">选择一道题</option>
        <option v-for="q in questions" :key="q.id" :value="q.id">
          {{ q.title }}{{ q.status === 'ARCHIVED' ? '（已归档）' : '' }}
        </option></select
      ><template v-if="question"
        ><h3>{{ question.title }}</h3>
        <MathText :text="question.prompt" />
        <form class="editor inline-editor" @submit.prevent="submit">
          <label for="attempt-content">独立作答内容</label
          ><textarea
            id="attempt-content"
            v-model="content"
            maxlength="20000"
            rows="8"
          /><label class="check-label"
            ><input
              v-model="declaredIndependent"
              type="checkbox"
            />自报独立作答（已查看提示时不会标记为未获帮助）</label
          >
          <p>可附上已上传图片；图片不会自动发给模型。</p>
          <label
            v-for="image in images.filter((i) => i.status === 'READY')"
            :key="image.id"
            class="check-label"
            ><input
              v-model="selectedImages"
              :value="image.id"
              type="checkbox"
            />附图 {{ image.id.slice(0, 8) }}</label
          ><button
            :disabled="
              question.status !== 'ACTIVE' ||
              (!content.trim() && !selectedImages.length)
            "
          >
            提交本次作答
          </button>
        </form></template
      ><button @click="showAuthor = !showAuthor">
        {{ showAuthor ? '收起题目编辑' : '手动添加题目' }}
      </button>
      <form
        v-if="showAuthor"
        class="editor learning-section"
        @submit.prevent="createQuestion"
      >
        <h3>手动添加题目</h3>
        <label for="question-title">题目名称</label
        ><input
          id="question-title"
          v-model="author.title"
          maxlength="160"
          required
        /><label for="question-prompt">题目内容（支持数学公式）</label
        ><textarea
          id="question-prompt"
          v-model="author.prompt"
          maxlength="20000"
          required
        /><label for="question-type">题目类型</label
        ><select id="question-type" v-model="author.questionType">
          <option value="PROOF">证明题（需复核）</option>
          <option value="PROJECT">代码或作品（需复核）</option>
          <option value="EXACT_TEXT">精确文本比对</option></select
        ><label for="question-answer">服务端参考答案或解析</label
        ><textarea
          id="question-answer"
          v-model="author.answerKey"
          maxlength="20000"
          :required="author.questionType === 'EXACT_TEXT'"
        /><label for="question-hint">预先准备的一步提示</label
        ><textarea
          id="question-hint"
          v-model="author.hintText"
          maxlength="8000"
        /><button>保存题目</button>
      </form>
    </section>
    <section class="tree-panel learning-section">
      <h2>图片附件</h2>
      <p>
        PNG/JPEG，原件最多5MiB；安全处理后私有保存。当前未启用模型读图，可手动转录。
      </p>
      <form @submit.prevent="upload">
        <label for="practice-image">上传作答图片</label
        ><input
          id="practice-image"
          ref="file"
          type="file"
          accept="image/png,image/jpeg"
          required
        /><button>上传图片</button>
      </form>
      <ul class="skill-list">
        <li v-for="image in images" :key="image.id" class="skill-card">
          <a
            v-if="image.status === 'READY'"
            :href="`/api/v1/attachments/${image.id}/content`"
            target="_blank"
            rel="noopener noreferrer"
            ><img
              class="practice-image"
              :src="`/api/v1/attachments/${image.id}/content`"
              :alt="`作答图片 ${image.id.slice(0, 8)}`"
          /></a>
          <p>
            {{ image.width }}×{{ image.height }} ·
            {{ image.status === 'READY' ? '可用' : '已归档' }}
          </p>
          <button @click="archiveImage(image)">
            {{ image.status === 'READY' ? '归档' : '恢复' }}图片
            {{ image.id.slice(0, 8) }}
          </button>
        </li>
      </ul>
    </section>
    <section class="tree-panel learning-section">
      <h2>作答历史</h2>
      <ul class="skill-list">
        <li v-for="a in attempts.items" :key="a.id" class="skill-card">
          <p>
            {{ new Date(a.createdAt).toLocaleString() }} ·
            {{ a.independent ? '自报独立提交' : '曾获帮助或未声明独立' }} ·
            提交时提示等级 {{ a.hintLevelAtSubmission }}
          </p>
          <button @click="chooseAttempt(a.id)">
            查看作答 {{ a.id.slice(0, 8) }}
          </button>
        </li>
      </ul>
      <div class="pagination">
        <button :disabled="attemptPage === 0" @click="nextPage(-1)">
          上一页作答</button
        ><button
          :disabled="(attemptPage + 1) * 10 >= attempts.total"
          @click="nextPage(1)"
        >
          下一页作答
        </button>
      </div>
    </section>
    <section v-if="selectedAttempt" class="tree-panel learning-section">
      <h2>已提交作答</h2>
      <MathText
        :text="selectedAttempt.submittedContent || '本次为图片作答'"
      /><img
        v-for="image in detailAttachments.filter((i) => i.status === 'READY')"
        :key="image.id"
        class="practice-image"
        :src="`/api/v1/attachments/${image.id}/content`"
        alt="已提交的作答图片"
      />
      <p>当前题目提示曝光等级 {{ hintLevel }}；历史提交内容不会覆盖。</p>
      <div class="row-actions">
        <button @click="hint(false)">查看一步提示</button
        ><button @click="hint(true)">确认后查看完整解析</button
        ><button @click="check">请求规则比对</button>
      </div>
      <MathText v-if="hintText" :text="hintText" />
      <form class="editor inline-editor" @submit.prevent="saveReview">
        <h3>人工复核</h3>
        <p v-if="review.tutorTurnId">
          已引用本人AI建议，确认后仍标记为人工确认AI意见。
        </p>
        <label for="review-verdict">复核结论</label
        ><select id="review-verdict" v-model="review.verdict">
          <option value="UNSURE">不确定</option>
          <option value="CORRECT">正确</option>
          <option value="INCORRECT">错误</option></select
        ><label for="review-feedback">复核依据</label
        ><textarea
          id="review-feedback"
          v-model="review.feedback"
          minlength="5"
          maxlength="8000"
          required
        /><label class="check-label"
          ><input
            v-model="review.confirmed"
            type="checkbox"
          />确认保存本人的复核意见，不视为权威验证</label
        ><button :disabled="!review.confirmed">保存复核意见</button>
      </form>
      <article
        v-for="r in reviews"
        :key="r.id"
        class="skill-card learning-section"
      >
        <h3>
          {{
            r.verdict === 'UNSURE'
              ? '不确定'
              : r.verdict === 'CORRECT'
                ? '正确'
                : '错误'
          }}
          ·
          {{
            r.reviewerType === 'RULE'
              ? '规则比对'
              : r.reviewerType === 'SELF_REVIEW'
                ? '人工自评'
                : '人工确认AI建议'
          }}
        </h3>
        <MathText :text="r.feedback" /><button
          :disabled="evidence.some((e) => e.reviewId === r.id)"
          @click="confirmEvidence(r.id)"
        >
          {{
            evidence.some((e) => e.reviewId === r.id)
              ? '已记录为证据'
              : '确认记录为证据'
          }}
        </button>
      </article>
    </section>
    <section class="tree-panel learning-section">
      <h2>节点证据与能力历史</h2>
      <p>自评与规则结果分开。规则比对不是作品验收，最多给出L2的保守建议。</p>
      <label v-for="e in evidence" :key="e.id" class="check-label"
        ><input v-model="selectedEvidence" :value="e.id" type="checkbox" />{{
          e.outcome
        }}
        ·
        {{ e.verification === 'RULE_CHECKED' ? '规则比对记录' : '自评记录' }} ·
        {{ e.independent ? '自报独立' : '曾获帮助' }} ·
        {{ e.id.slice(0, 8) }}</label
      >
      <form class="editor inline-editor" @submit.prevent="assess">
        <label for="assessment-type">评估方式</label
        ><select id="assessment-type" v-model="assessment.assessorType">
          <option value="RULE">按所选证据执行规则</option>
          <option value="SELF">人工自评</option></select
        ><label v-if="assessment.assessorType === 'SELF'" for="assessment-level"
          >自评等级（L0–L5）</label
        ><input
          v-if="assessment.assessorType === 'SELF'"
          id="assessment-level"
          v-model.number="assessment.level"
          type="number"
          min="0"
          max="5"
        /><label for="assessment-reason">评估说明</label
        ><textarea
          id="assessment-reason"
          v-model="assessment.reason"
          maxlength="8000"
          required
        /><button :disabled="!selectedEvidence.length">保存能力评估</button>
      </form>
      <article
        v-for="a in assessments"
        :key="a.assessment.id"
        class="skill-card learning-section"
      >
        <h3>
          L{{ a.assessment.level }} ·
          {{ a.assessment.assessorType === 'SELF' ? '自评' : '保守规则' }} ·
          {{ a.assessment.confidence === 'LOW' ? '低可信度' : '中可信度' }}
        </h3>
        <MathText :text="a.assessment.reason" />
        <p>
          {{ new Date(a.assessment.assessedAt).toLocaleString() }} · 关联
          {{ a.evidenceIds.length }} 条证据
        </p>
      </article>
    </section>
  </fieldset>
  <TutorPanel
    v-if="selectedAttempt"
    :key="selectedAttempt.id"
    :task-id="taskId"
    :question-id="selectedAttempt.questionId"
    :attempt-id="selectedAttempt.id"
    @review="
      (id) => {
        review.tutorTurnId = id
        review.confirmed = false
      }
    "
  />
</template>
