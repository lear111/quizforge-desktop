import { singleChoiceRenderer } from '../../renderer/single-choice/index.js';
import { multipleChoiceRenderer } from '../../renderer/multiple-choice/index.js';
import { readingRenderer } from '../../renderer/reading/index.js';
import { clozeRenderer } from '../../renderer/cloze/index.js';
import { matchingRenderer } from '../../renderer/matching/index.js';
import { translationRenderer } from '../../renderer/translation/index.js';
import { essayRenderer } from '../../renderer/essay/index.js';
const builtins = new Map([singleChoiceRenderer, multipleChoiceRenderer, readingRenderer, clozeRenderer, matchingRenderer, translationRenderer, essayRenderer].map(renderer => [renderer.questionType, renderer]));
/** Static internal registry. No external registration or dynamic plugin loading. */
export const QuestionRendererRegistry = Object.freeze({
  types: Object.freeze([...builtins.keys()]),
  require(type) {
    const renderer = builtins.get(type);
    if (!renderer) throw new TypeError(`Unsupported question type: ${type}`);
    return renderer;
  }
});
