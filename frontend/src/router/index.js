import { createRouter, createWebHashHistory, createWebHistory } from 'vue-router'
import { getActiveRole, hasActiveAccountSession, hasSession, switchRole } from '../utils/auth'
import { resolveTeacherTaskDetailEntry } from '../utils/teacherTaskNavigation'
import { applyDocumentTitle, bindDocumentTitleSync } from '../utils/routeTitle'
import { syncThemeForRoute } from '../utils/theme'

const AuthView = () => import('../views/AuthView.vue')
const CeremonyView = () => import('../views/CeremonyView.vue')
const MoyuTerminatorView = () => import('../views/MoyuTerminatorView.vue')
const ProductIntroView = () => import('../views/ProductIntroView.vue')

const StudentShell = () => import('../components/student/StudentShell.vue')
const StudentHomeView = () => import('../views/StudentHomeView.vue')
const StudentFreeCollaborationView = () => import('../views/StudentFreeCollaborationView.vue')
const StudentClassDetailView = () => import('../views/StudentClassDetailView.vue')
const StudentClassGroupsView = () => import('../views/StudentClassGroupsView.vue')
const StudentTasksView = () => import('../views/StudentTasksView.vue')
const StudentTaskDetailView = () => import('../views/StudentTaskDetailView.vue')
const StudentAppealsView = () => import('../views/StudentAppealsView.vue')
const StudentNotificationsView = () => import('../views/StudentNotificationsView.vue')
const StudentProfileView = () => import('../views/StudentProfileView.vue')

const TeacherShell = () => import('../components/teacher/TeacherShell.vue')
const TeacherClassesView = () => import('../views/TeacherClassesView.vue')
const TeacherClassDetailView = () => import('../views/TeacherClassDetailView.vue')
const TeacherTaskDetailView = () => import('../views/TeacherTaskDetailView.vue')
const TeacherTaskProgressView = () => import('../views/TeacherTaskProgressView.vue')
const TeacherPeerReviewsView = () => import('../views/TeacherPeerReviewsView.vue')
const TeacherScoreSummariesView = () => import('../views/TeacherScoreSummariesView.vue')
const TeacherAppealsView = () => import('../views/TeacherAppealsView.vue')
const TeacherStudentsView = () => import('../views/TeacherStudentsView.vue')
const TeacherGroupsView = () => import('../views/TeacherGroupsView.vue')
const TeacherTasksView = () => import('../views/TeacherTasksView.vue')
const TeacherScoresCenterView = () => import('../views/TeacherScoresCenterView.vue')
const TeacherAppealsCenterView = () => import('../views/TeacherAppealsCenterView.vue')
const TeacherNotificationLogsView = () => import('../views/TeacherNotificationLogsView.vue')
const TeacherProfileView = () => import('../views/TeacherProfileView.vue')

const AdminShell = () => import('../components/admin/AdminShell.vue')
const AdminHomeView = () => import('../views/AdminHomeView.vue')
const AdminInviteCodesView = () => import('../views/AdminInviteCodesView.vue')
const AdminUsersView = () => import('../views/AdminUsersView.vue')
const AdminEmailCenterView = () => import('../views/AdminEmailCenterView.vue')
const AdminSystemAnnouncementsView = () => import('../views/AdminSystemAnnouncementsView.vue')
const AdminSecurityView = () => import('../views/AdminSecurityView.vue')
const AdminMonitorView = () => import('../views/AdminMonitorView.vue')
const AdminLogsView = () => import('../views/AdminLogsView.vue')

const routerMode = String(import.meta.env.VITE_ROUTER_MODE || '').toLowerCase()
const useHashHistory = routerMode ? routerMode === 'hash' : import.meta.env.PROD

const router = createRouter({
  history: useHashHistory
    ? createWebHashHistory(import.meta.env.BASE_URL)
    : createWebHistory(import.meta.env.BASE_URL),
  routes: [
    {
      path: '/',
      redirect: '/auth',
    },
    {
      path: '/auth',
      name: 'auth',
      component: AuthView,
    },
    {
      path: '/moyu-terminator',
      name: 'moyu-terminator',
      component: MoyuTerminatorView,
      meta: { title: '摸鱼终结者计划' },
    },
    {
      path: '/product',
      name: 'product-intro',
      component: ProductIntroView,
      meta: { title: 'TeamTrace 产品介绍' },
    },
    {
      path: '/ceremony',
      name: 'ceremony',
      component: CeremonyView,
      meta: { requiresAuth: true, title: '欢迎加入 TeamTrace' },
    },
    {
      path: '/dashboard',
      name: 'dashboard',
      redirect: () => {
        const role = getActiveRole()
        if (role === 'teacher') return '/teacher/classes'
        if (role === 'admin') return '/admin'
        return '/student'
      },
    },
    {
      path: '/student',
      name: 'student-shell',
      component: StudentShell,
      meta: { requiresAuth: true, role: 'student', title: '学生端' },
      children: [
        {
          path: '',
          name: 'student-home',
          component: StudentHomeView,
          meta: { title: '学习协作首页' },
        },

        {
          path: 'free',
          name: 'student-free-collaboration',
          component: StudentFreeCollaborationView,
          meta: { title: '自由协作' },
        },
        {
          path: 'free/notifications',
          name: 'student-free-notifications',
          component: StudentFreeCollaborationView,
          meta: { title: '自由协作通知中心' },
        },
        {
          path: 'free/:section',
          name: 'student-free-collaboration-section',
          component: StudentFreeCollaborationView,
          meta: { title: '自由协作' },
        },
        {
          path: 'classes',
          name: 'student-classes',
          redirect: (to) => ({
            path: '/student',
            query: to.query,
          }),
        },
        {
          path: 'classes/:classId',
          name: 'student-class-detail',
          component: StudentClassDetailView,
          meta: { title: '班级详情' },
        },
        {
          path: 'classes/:classId/groups',
          name: 'student-class-groups',
          component: StudentClassGroupsView,
          meta: { title: '我的小组' },
        },
        {
          path: 'classes/:classId/tasks',
          redirect: to => ({
            path: '/student/tasks',
            query: {
              from: 'class-tasks',
              fromClassId: String(to.params.classId),
              fromTab: 'tasks',
              classFilterId: String(to.params.classId),
            },
          }),
        },
        {
          path: 'classes/:classId/tasks/:taskId',
          name: 'student-task-detail',
          component: StudentTaskDetailView,
          meta: { title: '任务详情' },
        },
        {
          path: 'classes/:classId/tasks/:taskId/peer-reviews',
          redirect: (to) => ({
            path: `/student/classes/${to.params.classId}/tasks/${to.params.taskId}`,
            query: { ...to.query, tab: 'peer' },
          }),
        },
        {
          path: 'classes/:classId/tasks/:taskId/score-summary',
          redirect: (to) => ({
            path: `/student/classes/${to.params.classId}/tasks/${to.params.taskId}`,
            query: { ...to.query, tab: 'score' },
          }),
        },
        {
          path: 'classes/:classId/tasks/:taskId/appeals',
          redirect: (to) => ({
            path: `/student/classes/${to.params.classId}/tasks/${to.params.taskId}`,
            query: { ...to.query, tab: 'appeals' },
          }),
        },
        {
          path: 'tasks',
          name: 'student-tasks',
          component: StudentTasksView,
          meta: { title: '任务中心' },
        },
        {
          path: 'appeals',
          name: 'student-appeals',
          component: StudentAppealsView,
          meta: { title: '申诉中心' },
        },
        {
          path: 'notifications',
          name: 'student-notifications',
          component: StudentNotificationsView,
          meta: { title: '通知中心' },
        },
        {
          path: 'profile',
          name: 'student-profile',
          component: StudentProfileView,
          meta: { title: '个人中心' },
        },
      ],
    },
    {
      path: '/teacher',
      name: 'teacher-shell',
      component: TeacherShell,
      meta: { requiresAuth: true, role: 'teacher', title: '教师端' },
      children: [
        {
          path: '',
          redirect: '/teacher/classes',
        },
        {
          path: 'classes',
          name: 'teacher-classes',
          component: TeacherClassesView,
          meta: { titleKey: 'classes.title' },
        },
        {
          path: 'classes/:classId',
          name: 'teacher-class-detail',
          component: TeacherClassDetailView,
          meta: { title: '班级详情' },
        },
        {
          path: 'classes/:classId/tasks/:taskId',
          name: 'teacher-task-detail',
          component: TeacherTaskDetailView,
          meta: { title: '任务详情' },
        },
        {
          path: 'classes/:classId/tasks/:taskId/progress',
          name: 'teacher-task-progress',
          component: TeacherTaskProgressView,
          meta: { title: '子任务进度' },
        },
        {
          path: 'classes/:classId/tasks/:taskId/peer-reviews',
          name: 'teacher-peer-reviews',
          component: TeacherPeerReviewsView,
          meta: { title: '互评列表' },
        },
        {
          path: 'classes/:classId/tasks/:taskId/score-summaries',
          name: 'teacher-score-summaries',
          component: TeacherScoreSummariesView,
          meta: { title: '成绩汇总' },
        },
        {
          path: 'classes/:classId/tasks/:taskId/appeals',
          name: 'teacher-appeals',
          component: TeacherAppealsView,
          meta: { title: '申诉处理' },
        },
        {
          path: 'classes/:classId/students',
          name: 'teacher-students',
          component: TeacherStudentsView,
          meta: { title: '学生管理' },
        },
        {
          path: 'classes/:classId/groups',
          name: 'teacher-groups',
          component: TeacherGroupsView,
          meta: { title: '分组管理' },
        },
        {
          path: 'classes/:classId/tasks',
          name: 'teacher-tasks',
          component: TeacherTasksView,
          meta: { title: '任务管理' },
        },
        {
          path: 'dashboard',
          redirect: '/teacher/classes',
        },
        {
          path: 'tasks',
          redirect: '/teacher/classes',
        },
        {
          path: 'task-detail/:taskId?',
          name: 'teacher-task-detail-standalone',
          component: TeacherTaskDetailView,
          meta: { title: '任务详情' },
          beforeEnter: async (to) => {
            const location = await resolveTeacherTaskDetailEntry(
              to.query.classId,
              to.params.taskId || to.query.taskId,
            )
            if (location.path !== to.path) {
              return { ...location, query: { ...to.query, ...(location.query || {}) } }
            }
            return true
          },
        },
        {
          path: 'scores',
          name: 'teacher-scores-center',
          component: TeacherScoresCenterView,
          meta: { title: '评分中心' },
        },
        {
          path: 'reports',
          redirect: '/teacher/scores',
        },
        {
          path: 'appeals',
          name: 'teacher-appeals-center',
          component: TeacherAppealsCenterView,
          meta: { titleKey: 'appeals.title' },
        },
        {
          path: 'notifications-logs',
          name: 'teacher-notification-logs',
          component: TeacherNotificationLogsView,
          meta: { title: '通知' },
        },
        {
          path: 'notifications',
          redirect: '/teacher/notifications-logs',
        },
        {
          path: 'help',
          redirect: '/teacher',
        },
        {
          path: 'profile',
          name: 'teacher-profile',
          component: TeacherProfileView,
          meta: { title: '个人中心' },
        },
        {
          path: 'settings',
          redirect: '/teacher/profile',
        },
      ],
    },
    {
      path: '/admin',
      component: AdminShell,
      meta: { requiresAuth: true, role: 'admin' },
      children: [
        {
          path: '',
          name: 'admin-home',
          component: AdminHomeView,
          meta: { title: '系统总览', description: '查看管理员工作区与已接通能力' },
        },
        {
          path: 'users',
          name: 'admin-users',
          component: AdminUsersView,
          meta: { title: '账户管理', description: '筛选用户、控制状态与重置密码' },
        },
        {
          path: 'emails',
          name: 'admin-emails',
          component: AdminEmailCenterView,
          meta: { title: '邮件中心', description: '向教师、学生或指定邮箱发送管理员通知' },
        },
        {
          path: 'announcements',
          name: 'admin-announcements',
          component: AdminSystemAnnouncementsView,
          meta: { title: '系统公告', description: '发布登录弹窗公告、维护提醒与重要变更通知' },
        },
        {
          path: 'invites',
          name: 'admin-invites',
          component: AdminInviteCodesView,
          meta: { title: '教师邀请码', description: '生成、停用、恢复与删除邀请码' },
        },
        {
          path: 'security',
          name: 'admin-security',
          component: AdminSecurityView,
          meta: { title: '安全设置', description: '维护当前管理员账户安全' },
        },
        {
          path: 'monitor',
          name: 'admin-monitor',
          component: AdminMonitorView,
          meta: { title: '系统监控', description: '查看班级、任务与小组运行情况' },
        },
        {
          path: 'logs',
          name: 'admin-logs',
          component: AdminLogsView,
          meta: { title: '操作日志', description: '追踪管理员关键操作记录' },
        },
      ],
    },
  ],
})

router.beforeEach((to) => {
  const activeRole = getActiveRole()
  const hasActiveSession = !!activeRole && hasActiveAccountSession(activeRole)

  // /auth 登录页始终允许访问
  if (to.path === '/auth') {
    return true
  }

  // 需要认证但当前标签页没有活跃会话
  if (to.meta.requiresAuth && !hasActiveSession) {
    return { path: '/auth', query: { reason: 'session_expired' } }
  }

  // 路由指定了角色要求，但当前活跃角色不匹配
  if (to.meta.role && activeRole !== to.meta.role) {
    // 如果目标角色有会话，自动切换过去
    if (hasSession(to.meta.role)) {
      switchRole(to.meta.role)
      return hasActiveAccountSession(to.meta.role)
        ? to.fullPath
        : { path: '/auth', query: { reason: 'session_expired' } }
    }
    // 没有该角色的会话，跳转到当前活跃角色的首页
    if (hasActiveSession) {
      if (activeRole === 'teacher') return '/teacher/classes'
      if (activeRole === 'student') return '/student'
      if (activeRole === 'admin') return '/admin'
    }
    return '/auth'
  }

  return true
})

router.afterEach((to) => {
  applyDocumentTitle(to)
  syncThemeForRoute(to.path)
})

bindDocumentTitleSync(router)

export default router
