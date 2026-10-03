import { element } from './contract.js';
/** Display only the authoritative Core result; never compute points. */
export function resultSection(r) {
  const section = element('section', 'practice-result'); section.id = 'practice-result'; section.hidden = !r;
  if (r) {
    section.append(element('p', `practice-result-${r.status.toLowerCase()}`, r.status === 'UNSCORED' ? '已提交 · 未评分' : r.status === 'CORRECT' ? '回答正确' : '回答错误'));
    if (r.score != null) section.append(element('p', 'practice-earned-score', `得分：${r.score} / ${r.maxScore}`));
    if (r.analysis.text) section.append(element('strong', '', '答案与解析'), element('p', 'practice-analysis', r.analysis.text));
  }
  return section;
}
