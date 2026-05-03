/**
 * 平台相关工具函数
 */

const PLATFORM_LABELS: Record<string, string> = {
  TELEGRAM: 'Telegram',
  LARK: '飞书',
  DINGTALK: '钉钉',
  WEWORK: '企业微信',
  SLACK: 'Slack',
  DISCORD: 'Discord',
}

const PLATFORM_TAG_TYPES: Record<string, string> = {
  TELEGRAM: '',
  LARK: 'success',
  DINGTALK: 'primary',
  WEWORK: 'warning',
  SLACK: 'danger',
  DISCORD: 'info',
}

export function platformLabel(platform: string | null | undefined): string {
  if (!platform) return '-'
  return PLATFORM_LABELS[platform.toUpperCase()] || platform
}

export function platformTagType(platform: string | null | undefined): string {
  if (!platform) return 'info'
  return PLATFORM_TAG_TYPES[platform.toUpperCase()] || 'info'
}
