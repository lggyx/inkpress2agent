/** LocalDateTime ISO 字符串 → 'YYYY-MM-DD HH:mm' */
export function formatDate(value) {
  if (!value) {
    return ''
  }
  return String(value).slice(0, 16).replace('T', ' ')
}

/** LocalDateTime ISO 字符串 → 'YYYY-MM-DD' */
export function formatDateOnly(value) {
  if (!value) {
    return ''
  }
  return String(value).slice(0, 10)
}
