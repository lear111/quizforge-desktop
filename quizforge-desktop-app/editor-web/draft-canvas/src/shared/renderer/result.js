import { renderContent } from './content.js';
import { element } from './contract.js';
/** Display only the authoritative Core result; never compute points. */
export function resultSection(r) {
  const section = element('section', 'practice-result'); section.id = 'practice-result'; section.hidden = !r;
  if (r) {
    section.append(element('p', `practice-result-${r.status.toLowerCase()}`, r.status === 'UNSCORED' ? '已提交 · 未评分' : r.status === 'CORRECT' ? '回答正确' : '回答错误'));
    if (r.score != null) section.append(element('p', 'practice-earned-score', `得分：${r.score} / ${r.maxScore}`));
    if (r.analysis.text || r.analysis.kind!=='TEXT') section.append(element('strong', '', '答案与解析'), renderContent(element('div', 'practice-analysis'),r.analysis));
  }
  return section;
}
