/**
 * Dashboard 工具函数
 */

import type { DisplayPipelineStep, TelegramReplyStyle } from '../types/dashboard'

/**
 * 解析 DisplayPipeline JSON 字符串
 */
export function parseDisplayPipelineJson(json: string | null | undefined): DisplayPipelineStep[] {
  if (!json?.trim()) return []
  try {
    const arr = JSON.parse(json) as unknown
    if (!Array.isArray(arr)) return []
    return arr.map((x) => {
      if (x && typeof x === 'object' && 'op' in (x as object)) {
        return { ...(x as Record<string, unknown>) } as DisplayPipelineStep
      }
      return { op: 'trim' }
    })
  } catch {
    return []
  }
}

/**
 * 构建 DisplayPipeline JSON 字符串
 */
export function buildDisplayPipelineJson(steps: DisplayPipelineStep[]): string | null {
  const rows: Record<string, unknown>[] = []
  for (const raw of steps) {
    if (!raw.op?.trim()) continue
    const o: Record<string, unknown> = { op: raw.op.trim() }
    for (const [k, v] of Object.entries(raw)) {
      if (k === 'op') continue
      if (v === undefined || v === null) continue
      if (typeof v === 'string' && v.trim() === '') continue
      if (typeof v === 'number' && !Number.isFinite(v)) continue
      o[k] = v
    }
    rows.push(o)
  }
  if (!rows.length) return null
  const json = JSON.stringify(rows)
  if (json.length > 24000) {
    throw new Error('PIPELINE_TOO_LONG')
  }
  return json
}

/**
 * PayLink 管道预设
 */
export function payLinkPipelinePreset(): DisplayPipelineStep[] {
  return [
    { op: 'trim' },
    { op: 'url_to_origin', lowercaseHost: true },
    { op: 'url_host_labels', count: 2, fromRight: true, withScheme: true },
  ]
}

/**
 * 安全解析 paramSchemaJson
 */
export function safeParseParamSchema(json: string): { params: string[]; examples: string[] } {
  try {
    const o = JSON.parse(json || '{}') as { params?: unknown[]; examples?: unknown[] }
    const params = Array.isArray(o.params) ? o.params.map((x) => String(x)) : []
    const examples = Array.isArray(o.examples) ? o.examples.map((x) => String(x ?? '')) : []
    return { params, examples }
  } catch {
    return { params: [], examples: [] }
  }
}

/**
 * 规范化 JSON 输入
 */
export function normalizeJsonInput(raw: string, label: string): string | null {
  const trimmed = raw.trim()
  if (!trimmed) return null
  try {
    return JSON.stringify(JSON.parse(trimmed))
  } catch {
    return '__INVALID__'
  }
}

/**
 * 格式化 JSON 文本（美化）
 */
export function prettyJsonText(raw: string | null | undefined): string {
  if (!raw?.trim()) return ''
  try {
    return JSON.stringify(JSON.parse(raw), null, 2)
  } catch {
    return raw
  }
}

/**
 * 解析字符串 Map 文本
 */
export function parseStringMapText(raw: string | null | undefined): Record<string, string> {
  if (!raw?.trim()) return {}
  try {
    const parsed = JSON.parse(raw)
    if (!parsed || typeof parsed !== 'object' || Array.isArray(parsed)) return {}
    return Object.entries(parsed).reduce<Record<string, string>>((acc, [key, value]) => {
      if (value == null) return acc
      acc[key] = String(value)
      return acc
    }, {})
  } catch {
    return {}
  }
}

/**
 * 解析可选的 Long ID
 */
export function parseOptionalLongId(raw: string): number | undefined {
  const t = raw.trim()
  if (!t) return undefined
  const n = Number(t)
  if (!Number.isFinite(n) || !Number.isInteger(n)) return undefined
  return n
}

/**
 * CSV 单元格转义
 */
export function csvEscapeCell(val: unknown): string {
  const s = val == null ? '' : String(val)
  if (/[",\n\r]/.test(s)) {
    return `"${s.replace(/"/g, '""')}"`
  }
  return s
}

/**
 * 解析 Telegram 回复样式
 */
export function parseTelegramReplyStyle(raw?: string | null): { style: TelegramReplyStyle; delimiter: string } {
  const text = (raw ?? '').trim()
  if (!text) return { style: 'LIST', delimiter: ' ' }
  if (text.toUpperCase().startsWith('VALUES_JOIN_CUSTOM:')) {
    return { style: 'VALUES_JOIN_CUSTOM', delimiter: text.slice('VALUES_JOIN_CUSTOM:'.length) || ' ' }
  }
  return { style: (text as TelegramReplyStyle) ?? 'LIST', delimiter: ' ' }
}

/**
 * 构建 Telegram 回复样式 payload
 */
export function buildTelegramReplyStylePayload(style: TelegramReplyStyle, delimiter: string): string {
  if (style !== 'VALUES_JOIN_CUSTOM') return style
  const d = (delimiter ?? '').trim()
  return `VALUES_JOIN_CUSTOM:${d || ' '}`
}

/**
 * 规范化命令提示
 */
export function normalizeCommandHint(raw: string): string {
  return raw.trim().toLowerCase().replace(/^\/+/, '').replace(/\s+/g, '_')
}

/**
 * 生成随机 4 位数字
 */
export function random4Digits(): string {
  return Math.floor(1000 + Math.random() * 9000).toString()
}
