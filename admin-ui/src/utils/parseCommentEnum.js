/**
 * 从数据库 COMMENT 等文本中解析「数字-展示文案」片段，生成 Telegram 枚举 JSON。
 *
 * 支持示例：
 * - `支付状态 2-已支付 3-未支付 (pay_state)` → {"2":"已支付","3":"未支付"}
 * - `2-已支付 3-未支付`
 *
 * 规则：去掉末尾括号说明；按空白/中英文逗号切分；每段匹配 `^\d+\s*-\s*.+$`
 */
export function parseCommentToEnumJson(comment) {
    if (!comment?.trim())
        return null;
    let s = comment.trim();
    s = s.replace(/\s*\([^)]*\)\s*$/u, '').trim();
    const parts = s.split(/[\s\u3000，,、]+/u).filter(Boolean);
    const pairs = {};
    for (const p of parts) {
        const m = p.match(/^(\d+)\s*-\s*(.+)$/);
        if (m) {
            const key = m[1];
            const val = m[2].trim();
            if (val)
                pairs[key] = val;
        }
    }
    return Object.keys(pairs).length ? JSON.stringify(pairs) : null;
}
