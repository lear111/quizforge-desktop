import Ajv from 'ajv';
import {validateArguments} from './protocol.js';

const clone=value=>JSON.parse(JSON.stringify(value));
const object=value=>Boolean(value)&&typeof value==='object'&&!Array.isArray(value);
const own=(value,key)=>Object.prototype.hasOwnProperty.call(value,key);
const escape=value=>value.replace(/~/g,'~0').replace(/\//g,'~1');
const keywords=new Set('$schema $ref $comment title description default examples readOnly writeOnly type enum const multipleOf maximum exclusiveMaximum minimum exclusiveMinimum maxLength minLength pattern format items additionalItems maxItems minItems uniqueItems contains maxProperties minProperties required properties patternProperties additionalProperties dependencies propertyNames allOf anyOf oneOf not if then else definitions contentEncoding contentMediaType'.split(' '));
const maps=new Set(['properties','patternProperties','definitions','dependencies']);
const singles=new Set(['additionalItems','additionalProperties','contains','propertyNames','not','if','then','else']);
const arrays=new Set(['allOf','anyOf','oneOf']);
const annotations=new Set('$schema $ref $comment title description default examples readOnly writeOnly'.split(' '));

export class DataValidationError extends TypeError {
  constructor(issues){super(issues.map(issue=>`${issue.path}: ${issue.message}`).join('; '));this.code='DATA_VALIDATION_FAILED';this.issues=clone(issues);}
}
const reject=(path,message)=>{throw new DataValidationError([{path,message}]);};
export function validationFailure(error){return {ok:false,error:{code:error.code||'DATA_VALIDATION_FAILED',message:error.message,retryable:false,...(error.issues?{issues:clone(error.issues)}:{})}};}

// Conservative shared browser/native profile. Backtracking groups/alternations and unanchored
// repetitions are not accepted even inside the terminable Worker (native validation also runs it).
function safePattern(pattern, path) {
  if(typeof pattern!=='string'||pattern.length>256)reject(path,'pattern must be text of at most 256 characters');
  let characterClass=false,quantifiers=0;
  for(let index=0;index<pattern.length;index++) {
    const token=pattern[index];
    if(token==='\\') {
      const next=pattern[++index];
      if(!next||!('dDsSwW\\.^$[]{}()*+?|-/' .includes(next)))reject(path,'unsupported pattern escape');
      continue;
    }
    if(token==='['&&!characterClass){characterClass=true;continue;}
    if(token===']'&&characterClass){characterClass=false;continue;}
    if(characterClass){if(token==='['||token==='&')reject(path,'nested or intersected character classes are not supported');continue;}
    if('()|'.includes(token))reject(path,'pattern groups and alternation are not supported');
    if('*+?'.includes(token))quantifiers++;
    if(token==='{') {
      const end=pattern.indexOf('}',index),repeat=pattern.slice(index+1,end);
      if(end<0||!/^\d+(,\d*)?$/.test(repeat)||repeat.split(',').filter(Boolean).some(n=>Number(n)>1024))reject(path,'pattern repeat bounds must be at most 1024');
      quantifiers++;index=end;
    }
    if(quantifiers>1)reject(path,'patterns may contain at most one quantifier');
  }
  if(quantifiers&&(!pattern.startsWith('^')||!pattern.endsWith('$')||/\\\$$/.test(pattern)))reject(path,'patterns with quantifiers must be anchored with ^ and $');
}

/** This profile is also enforced by the native ExtensionSchemaValidator. No remote resolver exists. */
function schemaProfile(root,path){
  if(!object(root))reject(path,'schema must be a JSON object');
  const nodes=new Map(),edges=new Map();let patterns=0;
  function collect(node,pointer,depth){
    if(depth>32||nodes.size>=4096)reject(path+pointer,'schema is too large or deeply nested');
    if(!object(node)&&typeof node!=='boolean')reject(path+pointer,'must be a schema object or boolean');
    nodes.set(pointer,node);edges.set(pointer,[]);if(typeof node==='boolean')return;
    const child=(value,key)=>{collect(value,key,depth+1);edges.get(pointer).push(key);};
    for(const [key,value] of Object.entries(node)){
      if(!keywords.has(key))reject(path+pointer+'/'+escape(key),'unsupported schema keyword');
      if(key==='$schema'&&!['http://json-schema.org/draft-07/schema#','https://json-schema.org/draft-07/schema#'].includes(value))reject(path+pointer+'/$schema','only Draft-07 is supported; omit $schema to use Draft-07');
      if(own(node,'$ref')&&!annotations.has(key))reject(path+pointer,'$ref cannot have assertion siblings');
      if(key==='pattern'){if(++patterns>64)reject(path,'schema has too many patterns');safePattern(value,path+pointer+'/pattern');}
      if(key==='patternProperties'&&object(value))for(const pattern of Object.keys(value)) {
        if(++patterns>64)reject(path,'schema has too many patterns');safePattern(pattern,path+pointer+'/patternProperties/'+escape(pattern));
      }
      if(maps.has(key)&&object(value))for(const [name,sub] of Object.entries(value)){if(key!=='dependencies'||!Array.isArray(sub))child(sub,`${pointer}/${key}/${escape(name)}`);}
      else if((arrays.has(key)||key==='items')&&Array.isArray(value))value.forEach((sub,index)=>child(sub,`${pointer}/${key}/${index}`));
      else if(singles.has(key)||key==='items')child(value,`${pointer}/${key}`);
    }
  }
  collect(root,'',0);
  for(const [pointer,node] of nodes)if(object(node)&&own(node,'$ref')){
    if(typeof node.$ref!=='string'||!node.$ref.startsWith('#/'))reject(path+pointer+'/$ref','only local JSON pointer references are supported');
    const target=node.$ref.slice(1);if(!nodes.has(target))reject(path+pointer+'/$ref','reference must point to a declared schema');edges.get(pointer).push(target);
  }
  const active=new Set(),done=new Set();
  function visit(pointer,depth){
    if(active.has(pointer)||depth>32)reject(path+pointer,'cyclic or overly deep schema references are not supported');
    if(done.has(pointer))return;done.add(pointer);active.add(pointer);edges.get(pointer).forEach(target=>visit(target,depth+1));active.delete(pointer);
  }
  visit('',0);
  // Count expanded paths, not just unique nodes: a small repeated-reference DAG can be exponential.
  let steps=0;
  function expanded(pointer){if(++steps>16384)reject(path,'expanded schema exceeds its complexity budget');for(const target of edges.get(pointer))expanded(target);}
  expanded('');
  // Canonicalize the supported dialect URI; it is a local bundled meta-schema, never a fetch.
  for(const node of nodes.values())if(object(node)&&own(node,'$schema'))node.$schema='http://json-schema.org/draft-07/schema#';
}
const target={type:'object',required:['id'],properties:{id:{type:'string',minLength:1},number:{type:'integer',minimum:1},locked:{type:'boolean'},gradable:{type:'boolean'},label:{type:'string'}}};
const targets={type:'array',items:target};
const errors={type:'array',items:{type:'string'}};
const maximum={type:'number',exclusiveMinimum:0};
const outputSchemas={
  validate:{type:'object',required:['errors'],properties:{errors}},
  validateAnswer:{type:'object',required:['errors','empty'],properties:{errors,empty:{type:'boolean'}}},
  targets:{type:'object',required:['targets'],properties:{targets}},
  snapshot:{type:'object',properties:{targets,maxScore:maximum}},
  grade:{type:'object',required:['status','score'],properties:{status:{enum:['CORRECT','INCORRECT','UNSCORED']},score:{type:['number','null']},maxScore:maximum}}
};
const ajv=new Ajv({allErrors:true,strict:false,validateFormats:false,coerceTypes:false,useDefaults:false,removeAdditional:false,ownProperties:true});
function requireValid(validate,value,path){
  if(validate(value))return;
  throw new DataValidationError(validate.errors.slice(0,32).map(error=>({path:path+error.instancePath+(error.keyword==='required'?'/'+escape(error.params.missingProperty):error.keyword==='additionalProperties'?'/'+escape(error.params.additionalProperty):''),message:error.message})));
}
const outputs=Object.fromEntries(Object.entries(outputSchemas).map(([key,schema])=>[key,ajv.compile(schema)]));
export function compileDataValidation(type,asset){
  function compile(source,path){
    try{
      if((typeof source==='string'?source:JSON.stringify(source)).length>256*1024)reject(path,'schema exceeds 256 KiB character limit');
      const schema=typeof source==='string'?JSON.parse(source):clone(source);schemaProfile(schema,path);
      return ajv.compile(schema);
    }catch(error){if(error instanceof DataValidationError)throw error;reject(path,`invalid schema: ${error.message}`);}
  }
  const questionSchema=compile(asset.questionSchemaSource,'/schemas/question'),answerSchema=compile(asset.answerSchemaSource,'/schemas/answer');
  function question(value){
    if(!object(value))reject('/question','must be an object');
    validateArguments([value]);
    for(const key of ['id','type'])if(typeof value[key]!=='string'||!value[key].trim())reject('/question/'+key,'must be nonempty text');
    if(value.type!==type.id)reject('/question/type','does not match the registered type');
    for(const key of ['prompt','payload','answerSpec','scoreSpec'])if(!object(value[key]))reject('/question/'+key,'must be an object');
    if(!Number.isFinite(value.scoreSpec.defaultMaxScore)||value.scoreSpec.defaultMaxScore<=0)reject('/question/scoreSpec/defaultMaxScore','must be positive and finite');
    requireValid(questionSchema,value,'/question');
  }
  function answer(value){
    if(!object(value))reject('/answer','must be an object');validateArguments([value]);
    if(Object.keys(value).length)requireValid(answerSchema,value,'/answer');
  }
  function checkTargets(value,path){
    const ids=new Set(),numbers=new Set();value.forEach((item,index)=>{
      if(!item.id.trim()||ids.has(item.id))reject(`${path}/${index}/id`,'must be nonempty and unique');ids.add(item.id);
      const number=item.number??index+1;if(numbers.has(number))reject(`${path}/${index}/number`,'must be unique');numbers.add(number);
    });
  }
  function input(operation,value){
    validateArguments([value]);
    if(value.question!==undefined)question(value.question);
    if(['validateAnswer','grade'].includes(operation))answer(value.answer);
    if(operation==='grade'&&!Object.keys(value.answer).length)reject('/answer','write an answer first');
  }
  function output(operation,value,input){
    validateArguments([value]);
    if(['createDraft','duplicate'].includes(operation)){question(value);return;}
    if(!outputs[operation])reject('/rules','unsupported operation');
    requireValid(outputs[operation],value,'/rules/'+operation);
    if(value.targets)checkTargets(value.targets,'/rules/'+operation+'/targets');
    if(operation==='grade'){
      const max=input.maxScore;
      if(!Number.isFinite(max)||max<=0)reject('/rules/grade/maxScore','requires a frozen positive maximum');
      if(own(value,'maxScore')&&value.maxScore!==max)reject('/rules/grade/maxScore','cannot change the frozen maximum score');
      if(value.status==='UNSCORED'){if(value.score!==null)reject('/rules/grade/score','an unscored result requires null');}
      else if(!Number.isFinite(value.score)||value.score<0||value.score>max)reject('/rules/grade/score','is outside the allowed range');
      else if((value.status==='CORRECT')!==(value.score===max))reject('/rules/grade/score','full credit requires CORRECT status');
    }
  }
  return Object.freeze({question,answer,input,output});
}
/** Validate both directions even when an extension replaces its own rules registry. */
export function checkedRules(registry,validators){
  return Object.freeze({invoke(type,operation,encoded){
    const validator=validators.get(type);if(!validator)reject('/question/type','type is not registered');
    const input=JSON.parse(encoded);validator.input(operation,input);
    if(operation==='validateAnswer'&&!Object.keys(input.answer).length)return JSON.stringify({errors:[],empty:true});
    const check=response=>{const value=JSON.parse(response);validator.output(operation,value,input);return JSON.stringify(value);};
    const response=registry.invoke(type,operation,encoded);return response?.then?response.then(check):check(response);
  }});
}
