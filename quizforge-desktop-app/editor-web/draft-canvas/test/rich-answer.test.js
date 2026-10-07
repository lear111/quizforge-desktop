import test from 'node:test';
import assert from 'node:assert/strict';
import vm from 'node:vm';
import {build} from 'esbuild';
import {fileURLToPath} from 'node:url';

const source=(await build({entryPoints:[fileURLToPath(new URL('../../../../extensions/shared/rich-answer.js',import.meta.url))],bundle:true,format:'iife',globalName:'Answers',write:false})).outputFiles[0].text;
async function fixture(){
  class Element {
    constructor(tag){this.tagName=tag.toUpperCase();this.nodeType=1;this.children=[];this.dataset={};this.events=new Map();this.classes=new Set();
      this.classList={toggle:(name,on)=>on?this.classes.add(name):this.classes.delete(name),contains:name=>this.classes.has(name)};}
    setAttribute(){} addEventListener(name,fn){this.events.set(name,fn);}
    append(...nodes){for(const node of nodes){node.remove?.();node.parentElement=this;this.children.push(node);}}
    replaceChildren(...nodes){for(const node of this.children)node.parentElement=null;this.children=[];this.append(...nodes);}
    remove(){if(this.parentElement){this.parentElement.children=this.parentElement.children.filter(n=>n!==this);this.parentElement=null;}}
    get childNodes(){return this.children;}
    get innerText(){return this.children.map(n=>n.data??n.innerText).join('');}
    set textContent(value){this.replaceChildren({nodeType:3,data:value});}
    contains(node){return node===this||this.children.some(child=>child===node||child.contains?.(node));}
    querySelectorAll(){return this.children.flatMap(node=>node.nodeType===1?[node,...node.querySelectorAll()]:[]);}
  }
  const root=new Element('section'),renders=[],changes=[],events=new Map();
  const document={createElement:tag=>new Element(tag),addEventListener(){},removeEventListener(){}};
  const context=vm.createContext({document,Node:{TEXT_NODE:3,ELEMENT_NODE:1},Option:class extends Element{constructor(text,value){super('option');this.value=value;this.textContent=String(text);}},
    window:{getSelection:()=>null,addEventListener:(name,fn)=>events.set(name,fn)},
    getComputedStyle:()=>({fontFamily:'Arial',fontSize:'16px',color:'#000000',fontWeight:'400',fontStyle:'normal',textDecorationLine:'none',textAlign:'left',textIndent:'0'}),
    QF:{content:{renderAsync(node,value){assert.ok(root.contains(node));return new Promise((resolve,reject)=>renders.push({node,value,resolve(){node.textContent=value.text||'';resolve();},reject}));}}}});
  vm.runInContext(source,context);
  const input=await context.Answers.richAnswer(root,value=>changes.push(value));
  const body=root.children.find(node=>node.className==='qf-answer-input');
  return {input,body,renders,changes,close:()=>events.get('pagehide')(),type(text){if(body.contentEditable==='true')body.textContent=text;body.events.get('input')();}};
}

test('answer restore locks input until rendering commits and cannot save empty loading content',async()=>{
  const f=await fixture(),load=f.input.update({kind:'TEXT',text:'saved answer'},true);
  assert.equal(f.body.contentEditable,'false');assert.throws(()=>f.input.getValue(),/加载/);
  f.type('new input during loading');assert.equal(f.changes.length,0);
  f.renders[0].resolve();await load;assert.equal(f.body.contentEditable,'true');
  f.type('new input');assert.equal(f.changes.at(-1).text,'new input');assert.equal(f.input.getValue().text,'new input');
});

test('late restore never overwrites a newer answer or subsequent typing',async()=>{
  const f=await fixture(),old=f.input.update({kind:'TEXT',text:'old answer'},true),fresh=f.input.update({kind:'TEXT',text:'new answer'},true);
  f.renders[1].resolve();await fresh;f.type('latest input');
  f.renders[0].resolve();await old;assert.equal(f.body.innerText,'latest input');assert.equal(f.input.getValue().text,'latest input');
});

test('render failure stays locked, retry recovers, and revoked permissions stay readonly',async()=>{
  const f=await fixture(),failed=f.input.update({kind:'TEXT',text:'saved'},true);
  f.renders[0].reject(new Error('resolve failed'));await assert.rejects(failed,/resolve failed/);
  assert.equal(f.body.contentEditable,'false');assert.throws(()=>f.input.getValue(),/加载/);
  const retry=f.input.update({kind:'TEXT',text:'saved'},true),revoke=f.input.update({kind:'TEXT',text:'saved'},false);
  f.renders[2].resolve();await revoke;f.renders[1].resolve();await retry;
  assert.equal(f.body.innerText,'saved');assert.equal(f.body.contentEditable,'false');
});

test('returning to the committed answer cancels an in-flight different answer',async()=>{
  const f=await fixture(),initial=f.input.update({kind:'TEXT',text:'A'},true);f.renders[0].resolve();await initial;
  const old=f.input.update({kind:'TEXT',text:'B'},true);await f.input.update({kind:'TEXT',text:'A'},true);f.type('typed A');
  f.renders[1].resolve();await old;assert.equal(f.body.innerText,'typed A');
});
