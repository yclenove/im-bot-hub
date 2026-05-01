/** 与后端常用 MySQL 连接参数一致（时区、编码等） */
const DEFAULT_QUERY =
  'useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true'

export function buildMysqlJdbcUrl(host: string, port: string | number, database: string): string {
  const h = host.trim()
  const p = String(port ?? '3306').trim() || '3306'
  const d = database.trim()
  return `jdbc:mysql://${h}:${p}/${d}?${DEFAULT_QUERY}`
}

/** 仅支持标准形态 jdbc:mysql://host[:port]/db?... */
export function parseMysqlJdbcUrl(
  url: string,
): { host: string; port: string; database: string } | null {
  const m = url.trim().match(/^jdbc:mysql:\/\/([^:/?]+)(?::(\d+))?\/([^?]+)/i)
  if (!m) return null
  return { host: m[1], port: m[2] || '3306', database: m[3] }
}
