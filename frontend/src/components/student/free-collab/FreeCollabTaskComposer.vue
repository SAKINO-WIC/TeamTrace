<script setup>
import FileUploadZone from '../../common/FileUploadZone.vue'

defineProps({
  selectedSpace: { type: Object, default: null },
  selectedProjectId: { type: [Number, String, null], default: null },
  canOrganize: { type: Boolean, default: false },
  activeCreatePanel: { type: String, default: '' },
  isSubmitting: { type: Boolean, default: false },
  selectedMembers: { type: Array, default: () => [] },
  selectedTasks: { type: Array, default: () => [] },
  projectForm: { type: Object, required: true },
  projectWizardForm: { type: Object, required: true },
  taskForm: { type: Object, required: true },
  setActiveCreatePanel: { type: Function, required: true },
  submitCreateProject: { type: Function, required: true },
  submitProjectWizard: { type: Function, required: true },
  resetProjectWizard: { type: Function, required: true },
  addWizardTask: { type: Function, required: true },
  removeWizardTask: { type: Function, required: true },
  setWizardStep: { type: Function, required: true },
  submitCreateTask: { type: Function, required: true },
  addTaskFormFlowNode: { type: Function, required: true },
  removeTaskFormFlowNode: { type: Function, required: true },
})
</script>

<template>
  <section v-if="selectedSpace && canOrganize" class="composer project-wizard">
    <div class="composer-brief">
      <div>
        <span>发起人工作台</span>
        <h2>把协作先拆清楚，再交给看板流转</h2>
        <p>项目负责说明目标和规则，任务负责明确负责人、接收人、截止时间和前置关系。</p>
      </div>
      <div class="composer-actions">
        <button
          type="button"
          :class="{ active: activeCreatePanel === 'wizard' }"
          @click="setActiveCreatePanel(activeCreatePanel === 'wizard' ? '' : 'wizard')"
        >
          发布协作项目
        </button>
        <button
          type="button"
          :class="{ active: activeCreatePanel === 'task' }"
          :disabled="!selectedProjectId"
          @click="setActiveCreatePanel(activeCreatePanel === 'task' ? '' : 'task')"
        >
          追加任务
        </button>
      </div>
    </div>

    <div v-if="!activeCreatePanel" class="creator-empty-panel">
      <article>
        <strong>1</strong>
        <div>
          <span>先发布项目</span>
          <p>定义目标、规则和总交付物，团队先对齐方向。</p>
        </div>
      </article>
      <article>
        <strong>2</strong>
        <div>
          <span>再拆任务</span>
          <p>支持指定负责人，也支持成员自行认领。</p>
        </div>
      </article>
      <article>
        <strong>3</strong>
        <div>
          <span>最后看流转</span>
          <p>提交、接收、打回、完成都会进入可视化面板。</p>
        </div>
      </article>
    </div>

    <form v-if="activeCreatePanel === 'wizard'" class="wizard-shell" @submit.prevent="submitProjectWizard">
      <nav class="wizard-steps" aria-label="发布步骤">
        <button type="button" :class="{ active: projectWizardForm.step === 'project' }" @click="setWizardStep('project')">
          <strong>1</strong>
          <span>项目信息</span>
        </button>
        <button type="button" :class="{ active: projectWizardForm.step === 'tasks' }" @click="setWizardStep('tasks')">
          <strong>2</strong>
          <span>任务拆解</span>
        </button>
        <button type="button" :class="{ active: projectWizardForm.step === 'review' }" @click="setWizardStep('review')">
          <strong>3</strong>
          <span>发布确认</span>
        </button>
      </nav>

      <section v-if="projectWizardForm.step === 'project'" class="wizard-panel">
        <div class="section-head compact">
          <div>
            <span>项目基础信息</span>
            <h2>先让团队知道要一起完成什么</h2>
          </div>
          <button type="button" class="secondary-btn" @click="setWizardStep('tasks')">下一步</button>
        </div>
        <div class="wizard-project-grid">
          <input v-model="projectWizardForm.project.title" class="free-input" placeholder="项目名称" />
          <label class="free-field">
            <span>开始时间</span>
            <input v-model="projectWizardForm.project.startAt" class="free-input" type="datetime-local" />
          </label>
          <label class="free-field">
            <span>截止时间</span>
            <input v-model="projectWizardForm.project.dueAt" class="free-input" type="datetime-local" />
          </label>
          <textarea v-model="projectWizardForm.project.description" class="free-textarea" placeholder="项目目标、团队规则、最终交付物说明" />
          <div class="wizard-attachment-field">
            <span>项目附件</span>
            <FileUploadZone v-model="projectWizardForm.project.attachments" :disabled="isSubmitting" :max-files="10" />
          </div>
        </div>
      </section>

      <section v-else-if="projectWizardForm.step === 'tasks'" class="wizard-panel">
        <div class="section-head compact">
          <div>
            <span>任务拆解</span>
            <h2>{{ projectWizardForm.tasks.length }} 个任务</h2>
          </div>
          <div class="button-row">
            <button type="button" class="secondary-btn" @click="addWizardTask">添加任务</button>
            <button type="button" class="primary-btn" @click="setWizardStep('review')">去确认</button>
          </div>
        </div>

        <div class="wizard-task-list">
          <article v-for="(task, index) in projectWizardForm.tasks" :key="index" class="wizard-task-card">
            <header>
              <span>任务 {{ index + 1 }}</span>
              <button type="button" class="text-btn" :disabled="projectWizardForm.tasks.length <= 1" @click="removeWizardTask(index)">删除</button>
            </header>
            <div class="wizard-task-grid">
              <input v-model="task.title" class="free-input" placeholder="任务标题" />
              <select v-model="task.assigneeId" class="free-input">
                <option value="">成员自愿认领</option>
                <option v-for="member in selectedMembers" :key="member.studentId" :value="member.studentId">{{ member.name }}</option>
              </select>
              <select v-model="task.receiverId" class="free-input">
                <option value="">任一成员接收</option>
                <option v-for="member in selectedMembers" :key="member.studentId" :value="member.studentId">{{ member.name }}</option>
              </select>
              <label class="free-field">
                <span>开始时间</span>
                <input v-model="task.startAt" class="free-input" type="datetime-local" />
              </label>
              <label class="free-field">
                <span>截止时间</span>
                <input v-model="task.dueAt" class="free-input" type="datetime-local" />
              </label>
              <textarea v-model="task.description" class="free-textarea" placeholder="任务说明" />
              <textarea v-model="task.deliverableRequirements" class="free-textarea" placeholder="交付要求" />
              <div class="wizard-attachment-field">
                <span>任务附件</span>
                <FileUploadZone v-model="task.attachments" :disabled="isSubmitting" :max-files="10" />
              </div>
              <div class="flow-node-editor">
                <div class="flow-node-editor-head">
                  <span>交接流程</span>
                  <button type="button" class="text-btn" @click="addTaskFormFlowNode(task)">添加环节</button>
                </div>
                <div v-for="(node, nodeIndex) in task.flowNodes" :key="nodeIndex" class="flow-node-row">
                  <strong>{{ nodeIndex + 1 }}</strong>
                  <input v-model="node.title" class="free-input" placeholder="环节名称，如收集资料" />
                  <select v-model="node.assigneeId" class="free-input" @change="node.claimable = !node.assigneeId">
                    <option value="">开放认领</option>
                    <option v-for="member in selectedMembers" :key="member.studentId" :value="member.studentId">{{ member.name }}</option>
                  </select>
                  <input v-model="node.description" class="free-input" placeholder="交接要求，可选" />
                  <button type="button" class="text-btn" @click="removeTaskFormFlowNode(task, nodeIndex)">删除</button>
                </div>
              </div>
              <div v-if="index > 0" class="dependency-picker">
                <label v-for="(_, prevIndex) in projectWizardForm.tasks.slice(0, index)" :key="prevIndex">
                  <input v-model="task.dependsOnLocalIds" type="checkbox" :value="prevIndex + 1" />
                  <span>等待任务 {{ prevIndex + 1 }}：{{ projectWizardForm.tasks[prevIndex].title || '未命名任务' }}</span>
                </label>
              </div>
            </div>
          </article>
        </div>
      </section>

      <section v-else class="wizard-panel">
        <div class="section-head compact">
          <div>
            <span>发布确认</span>
            <h2>{{ projectWizardForm.project.title || '未命名项目' }}</h2>
          </div>
          <div class="button-row">
            <button type="button" class="secondary-btn" @click="setWizardStep('tasks')">返回修改</button>
            <button type="submit" class="primary-btn" :disabled="isSubmitting">
              {{ isSubmitting ? '发布中' : '发布项目和任务' }}
            </button>
          </div>
        </div>
        <div class="wizard-review-grid">
          <article>
            <span>任务数量</span>
            <strong>{{ projectWizardForm.tasks.filter((task) => task.title.trim()).length }}</strong>
          </article>
          <article>
            <span>指定负责人</span>
            <strong>{{ projectWizardForm.tasks.filter((task) => task.assigneeId).length }}</strong>
          </article>
          <article>
            <span>存在前置关系</span>
            <strong>{{ projectWizardForm.tasks.filter((task) => task.dependsOnLocalIds.length).length }}</strong>
          </article>
        </div>
        <div class="wizard-review-list">
          <article v-for="(task, index) in projectWizardForm.tasks.filter((item) => item.title.trim())" :key="index">
            <strong>{{ index + 1 }}. {{ task.title }}</strong>
            <span>
              {{ task.assigneeId ? '已指定负责人' : '成员自愿认领' }}
              · {{ task.receiverId ? '已指定接收人' : '任一成员接收' }}
              · 前置 {{ task.dependsOnLocalIds.length }} 个
            </span>
          </article>
        </div>
        <button type="button" class="text-btn" @click="resetProjectWizard">清空向导</button>
      </section>
    </form>

    <Teleport to="body">
      <div v-if="activeCreatePanel === 'task'" class="free-modal-layer" role="presentation">
        <button
          type="button"
          class="free-modal-backdrop"
          aria-label="关闭追加任务"
          @click="setActiveCreatePanel('')"
        ></button>
        <form class="free-modal-panel task-modal-form" role="dialog" aria-modal="true" aria-label="追加任务" @submit.prevent="submitCreateTask">
          <header class="free-modal-head">
            <div>
              <span>追加任务</span>
              <h2>给当前项目补充一个可流转任务</h2>
            </div>
            <button type="button" class="text-btn" @click="setActiveCreatePanel('')">关闭</button>
          </header>
          <div class="task-modal-grid">
            <input v-model="taskForm.title" class="free-input" placeholder="任务标题" />
            <select v-model="taskForm.assigneeId" class="free-input">
              <option value="">成员自己认领</option>
              <option v-for="member in selectedMembers" :key="member.studentId" :value="member.studentId">{{ member.name }}</option>
            </select>
            <select v-model="taskForm.receiverId" class="free-input">
              <option value="">任一成员接收</option>
              <option v-for="member in selectedMembers" :key="member.studentId" :value="member.studentId">{{ member.name }}</option>
            </select>
            <label class="free-field">
              <span>开始时间</span>
              <input v-model="taskForm.startAt" class="free-input" type="datetime-local" />
            </label>
            <label class="free-field">
              <span>截止时间</span>
              <input v-model="taskForm.dueAt" class="free-input" type="datetime-local" />
            </label>
            <textarea v-model="taskForm.description" class="free-textarea" placeholder="任务说明" />
            <textarea v-model="taskForm.deliverableRequirements" class="free-textarea" placeholder="交付要求" />
            <div class="wizard-attachment-field">
              <span>任务附件</span>
              <FileUploadZone v-model="taskForm.attachments" :disabled="isSubmitting" :max-files="10" />
            </div>
            <div class="flow-node-editor">
              <div class="flow-node-editor-head">
                <span>交接流程</span>
                <button type="button" class="text-btn" @click="addTaskFormFlowNode(taskForm)">添加环节</button>
              </div>
              <div v-for="(node, nodeIndex) in taskForm.flowNodes" :key="nodeIndex" class="flow-node-row">
                <strong>{{ nodeIndex + 1 }}</strong>
                <input v-model="node.title" class="free-input" placeholder="环节名称，如制作 PPT" />
                <select v-model="node.assigneeId" class="free-input" @change="node.claimable = !node.assigneeId">
                  <option value="">开放认领</option>
                  <option v-for="member in selectedMembers" :key="member.studentId" :value="member.studentId">{{ member.name }}</option>
                </select>
                <input v-model="node.description" class="free-input" placeholder="交接要求，可选" />
                <button type="button" class="text-btn" @click="removeTaskFormFlowNode(taskForm, nodeIndex)">删除</button>
              </div>
            </div>
            <div v-if="selectedTasks.length" class="dependency-picker">
              <label v-for="task in selectedTasks" :key="task.id">
                <input v-model="taskForm.dependsOnTaskIds" type="checkbox" :value="task.id" />
                <span>{{ task.title }}</span>
              </label>
            </div>
          </div>
          <footer class="free-modal-actions">
            <button class="secondary-btn" type="button" @click="setActiveCreatePanel('')">取消</button>
            <button class="primary-btn" type="submit" :disabled="isSubmitting || !selectedProjectId">创建任务</button>
          </footer>
        </form>
      </div>
    </Teleport>
  </section>
</template>
