// One-time source migration. Existing editable source files are never overwritten.
import {readFile,writeFile,mkdir,access,copyFile} from 'node:fs/promises';
import {resolve,dirname} from 'node:path';
import {fileURLToPath} from 'node:url';
const repo=resolve(dirname(fileURLToPath(import.meta.url)),'../..');
const replacements=[
  ['QF.editor.getData()','page.question()'],['QF.editor.update(patch)','page.edit(patch)'],
  ['QF.bank.getState()','page.state()'],['QF.host.getContext()','page.host()'],
  ['QF.bank.save()','page.commit()'],['QF.bank.addQuestion(type)',"page.action('addQuestion', {type})"],
  ['QF.bank.duplicateQuestion()',"page.action('duplicateQuestion')"],['QF.bank.deleteQuestion()',"page.action('deleteQuestion')"],
  ['QF.sources.add(link)',"page.action('addSource', {link})"],
  ['QF.sources.open(Number(button.dataset.sourceIndex))',"page.action('openSource', {index:Number(button.dataset.sourceIndex)})"],
  ['QF.sources.remove(Number(button.dataset.sourceIndex))',"page.action('removeSource', {index:Number(button.dataset.sourceIndex)})"],
  ['QF.practice.getQuestion()','page.question()'],['QF.practice.getState()','page.practiceState()'],
  ['QF.practice.getResult()','page.result()'],['QF.navigation.getState()','page.state()'],
  ['QF.answer.get()','page.answer()'],['QF.answer.update({ selectedOptionIds })','page.write({ selectedOptionIds })'],
  ['QF.practice.submit()','page.submit()'],['QF.practice.retry()',"page.action('retry')"],
  ['QF.host.subscribe(', 'page.subscribe(']
];
// The handwritten true-false template deliberately has no generated source or helper library.
for(const slug of ['single-choice','multiple-choice']){
  const directory=resolve(repo,'extensions/packages',slug);await mkdir(resolve(directory,'lib'),{recursive:true});
  await copyFile(resolve(repo,'extensions/shared/page-client.js'),resolve(directory,'lib/page-client.js'));
  for(const name of ['editor','practice']){
    const sourceFile=resolve(directory,name+'-source.js');
    try{await access(sourceFile);continue;}catch{}
    let source=await readFile(resolve(directory,name+'.js'),'utf8');
    for(const [from,to] of replacements)source=source.replaceAll(from,to);
    if(/QF\.(host|editor|bank|answer|practice|navigation|sources)\./.test(source))throw new Error('Unconverted page call in '+slug+'/'+name);
    await writeFile(sourceFile,"import {connectPage} from './lib/page-client.js';\nexport async function start(){\nconst page=await connectPage(QF);\n"+source+'\n}\n');
  }
}
