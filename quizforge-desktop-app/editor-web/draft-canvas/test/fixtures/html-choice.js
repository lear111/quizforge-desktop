import { readFileSync } from 'node:fs';
import { installQuestionExtension, compileQuestionExtension } from '../../src/extensions/sdk.js';
const clone = value => JSON.parse(JSON.stringify(value));
export function bundle(slug = 'single-choice') {
  const directory = new URL(`../../../../../extensions/packages/${slug}/`, import.meta.url);
  const read = file => readFileSync(new URL(file, directory), 'utf8');
  const manifest = JSON.parse(read('manifest.json')), type = manifest.types[0];
  return { manifest, assets: [{typeId:type.id, defaultQuestion:JSON.parse(read(type.defaultQuestion)),
    questionSchemaSource:read(type.questionSchema),answerSchemaSource:read(type.answerSchema),
    editorHtml:read(type.editor), rendererHtml:read(type.renderer),
    editorSource:read(type.editorScript), rendererSource:read(type.rendererScript),
    rulesSource:read(type.rules), stylesSource:read(type.styles[0])}] };
}
export function installChoices() { for (const slug of ['single-choice','multiple-choice']) installQuestionExtension(bundle(slug)); }
export function card(type = 'SINGLE_CHOICE', selected = [], result = null) {
  const source = bundle(type === 'SINGLE_CHOICE' ? 'single-choice' : 'multiple-choice');
  const question = clone(source.assets[0].defaultQuestion);
  const {answerSpec, analysis, evaluationSpec, ...publicQuestion} = question;
  return { schemaVersion:'1.0', session:{sessionId:'session',bankAssetId:'qb_test',bankContentId:'frozen'},
    question:{sessionQuestionId:'sq', questionId:question.id, type, index:0, total:2,
      prompt:question.prompt, options:[], selectedOptionIds:clone(selected), state:result?'SUBMITTED':selected.length?'DRAFT':'UNANSWERED',
      maxScore:question.scoreSpec.defaultMaxScore, result,
      presentation:{extensionId:source.manifest.id, extensionVersion:source.manifest.version, dataVersion:1,
        question:publicQuestion, answer:{selectedOptionIds:clone(selected)}, reference:result?{answerSpec,analysis}:null}}};
}
export function compiled(slug) {return compileQuestionExtension(bundle(slug));}
