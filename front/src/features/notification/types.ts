// API 명세서 NOTI-01·02
export type NotificationCategory = 'COMMENT' | 'TEAM_BUILDING' | 'WORKSPACE' | 'DEADLINE' | 'NOTICE'

export interface NotificationItem {
  notificationId: number
  type: string
  category: NotificationCategory
  message: string
  linkUrl: string
  actor: { memberId: number; nickname: string } | null
  isRead: boolean
  groupCount: number
  createdAt: string
  invitation: {
    invitationId: number
    status: 'PENDING' | 'ACCEPTED' | 'REJECTED' | 'CLOSED'
  } | null
}

// MY-10 notification_setting.setting_type
export type SettingType =
  | 'APPLY_RESULT'
  | 'APPLY_NEW'
  | 'TEAM_INVITE'
  | 'POST_DELETED'
  | 'COMMENT'
  | 'WORKSPACE'
  | 'BOOKMARK_DEADLINE'
  | 'COMMUNITY_NEW'

export interface NotificationSetting {
  type: SettingType
  siteEnabled: boolean
  emailEnabled: boolean
}

export interface ServiceNotice {
  serviceNoticeId: number
  title: string
  content: string
  createdAt: string
}
