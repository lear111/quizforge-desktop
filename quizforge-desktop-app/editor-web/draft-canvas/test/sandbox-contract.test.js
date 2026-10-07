import test from 'node:test';
import assert from 'node:assert/strict';
import {frameDocument} from '../src/extensions/frame-host.js';
import {defaultLayout} from '../src/shared/ui/layout.js';
import {defaultUi} from '../src/shared/ui/preferences.js';

test('isolated page receives data and local SDK, without host closures or native objects',()=>{
 const doc=frameDocument('<button data-answer>回答</button>','body{color:purple}','await QF.page.register({onLoad(){}});',{
  session:'test-session',mode:'PRACTICE',ui:defaultUi('PRACTICE'),layout:defaultLayout(),layoutState:{},questionId:'q1'
 });
 assert.match(doc,/connect-src 'none'/);assert.match(doc,/frame-src 'none'/);assert.match(doc,/nonce-qf-isolated-page-v1/);
 assert.match(doc,/<meta http-equiv="Content-Security-Policy" content="script-src 'unsafe-inline'">/);
 assert.doesNotMatch(doc,/allow-same-origin|window\.practiceHost|window\.editorHost|caps\.answerChanged/);
 assert.match(doc,/event\.source\s*!==\s*parent/);assert.match(doc,/message\.session\s*!==\s*boot\.session/);
 assert.match(doc,/await QF\.page\.register/);
});
test('package text cannot terminate the authorized script or inject markup through bootstrap data',()=>{
 const doc=frameDocument('<p>内容</p>','','const value="</script><script>bad()</script>";',{
  session:'test',mode:'EDITOR',ui:defaultUi('EDITOR'),layout:defaultLayout(),layoutState:{},questionId:'</script>'
 });
 assert.doesNotMatch(doc,/<script>bad\(\)<\/script>/);assert.match(doc,/\\u003c\/script>/);
 assert.equal((doc.match(/<script nonce=/g)||[]).length,2);
});
