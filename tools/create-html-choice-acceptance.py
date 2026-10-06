"""Create the current choice acceptance bank; never overwrite an existing file."""
import argparse
import copy
import json
from pathlib import Path
import zipfile

parser = argparse.ArgumentParser()
parser.add_argument('output', type=Path)
args = parser.parse_args()
if args.output.exists():
    parser.error('Output already exists; select a fresh path')
root = Path(__file__).resolve().parents[1]
questions = []
cases = [
    ('single-choice', '请选择偶数。', ['1', '2', '3', '5'], [1], 2, '2 是偶数。'),
    ('multiple-choice', '请选择所有偶数。', ['2', '3', '4', '5'], [0, 2], 3, '2 和 4 是偶数；多选必须选全且不能多选。'),
    ('single-choice', '哪一项描述新版题型扩展的结构？', ['仅提供编辑界面', '仅提供评分脚本', '编辑 HTML、练习 HTML、默认 JSON 和题型逻辑', '需要修改主程序才能新增题型'], [2], 2, '两个页面与独立默认数据共同构成题型扩展。'),
    ('multiple-choice', '哪些内容由宿主统一管理？', ['草稿白板', '练习记录与统计', '每个开发者自行保存全局模型密码', '题库保存'], [0, 1, 3], 3, '宿主负责公共生命周期；题型提供界面和题型逻辑。'),
]
for index, (slug, stem, options, correct, maximum, explanation) in enumerate(cases, 1):
    question = copy.deepcopy(json.loads((root / 'extensions/packages' / slug / 'default.json').read_text(encoding='utf-8')))
    question['id'] = f'q_html_acceptance_{index}'
    question['prompt'] = {'kind': 'TEXT', 'text': stem}
    ids = [f'opt_html_acceptance_{index}_{letter}' for letter in 'abcd']
    question['payload']['options'] = [{'id': identifier, 'content': {'kind': 'TEXT', 'text': label}} for identifier, label in zip(ids, options)]
    question['answerSpec']['correctOptionIds'] = [ids[number] for number in correct]
    question['scoreSpec']['defaultMaxScore'] = maximum
    question['analysis'] = {'kind': 'RICH', 'document': {'blocks': [
        {'type': 'PARAGRAPH', 'children': [{'type': 'TEXT', 'text': '答案与解析：', 'marks': ['BOLD']}, {'type': 'TEXT', 'text': explanation}]}
    ]}}
    questions.append(question)
manifest = {'format': 'quizforge-question-bank', 'schemaVersion': '2.0', 'assetId': 'qb_html_choice_acceptance_v2', 'title': 'HtmlChoiceAcceptance', 'resources': []}
args.output.parent.mkdir(parents=True, exist_ok=True)
with zipfile.ZipFile(args.output, 'x', zipfile.ZIP_DEFLATED) as archive:
    for name, content in [('manifest.json', manifest), ('bank.json', {'stimuli': [], 'questions': questions})]:
        archive.writestr(name, json.dumps(content, ensure_ascii=False, indent=2))
print(f'Created {args.output}: {len(questions)} questions, 10 points')
