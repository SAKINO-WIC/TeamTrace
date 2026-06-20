/** 仅在用户点击「导出 PDF」时加载 html2pdf（约 600KB+），避免打进首屏。 */
export async function loadHtml2Pdf() {
  const module = await import('html2pdf.js')
  return module.default
}
