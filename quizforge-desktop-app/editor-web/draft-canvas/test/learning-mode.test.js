import test from 'node:test';
import assert from 'node:assert/strict';
import { modePolicy, practiceViewport, SharedLearningSurfaceMode } from '../src/learning/mode-policy.js';
test('default active surface vocabulary is PRACTICE and DRAFT', () => {
  assert.deepEqual(SharedLearningSurfaceMode, { PRACTICE: 'PRACTICE', DRAFT: 'DRAFT' });
});
test('practice keeps answers and annotations, locks editing and user viewport', () => {
  assert.deepEqual(modePolicy('PRACTICE'), { answerInteractive:true, annotationsVisible:true, annotationEditing:false, userViewport:false });
});
test('draft enables answer interaction, annotation editing and user viewport', () => {
  assert.deepEqual(modePolicy('DRAFT'), { answerInteractive:true, annotationsVisible:true, annotationEditing:true, userViewport:true });
});
test('history stays readonly with view-only navigation', () => {
  assert.equal(modePolicy('HISTORY').answerInteractive,false);assert.equal(modePolicy('HISTORY').annotationEditing,false);
  assert.throws(() => modePolicy('NORMAL'), /Unknown/);
});
test('computed practice viewport centers fixed World geometry without mutating it', () => {
  const card=Object.freeze({x:150,y:95,width:720});
  for(const width of [500,720,1100]) {
    const viewport=practiceViewport(card,width,0,800,460);
    assert.ok(Math.abs((card.x+viewport.x)*viewport.zoom-(width-card.width*viewport.zoom)/2)<1e-9);
    assert.ok(Math.abs((card.y+viewport.y)*viewport.zoom-(800-460*viewport.zoom)/2)<1e-9);
    assert.ok(viewport.zoom<=1);assert.equal(card.width,720);
  }
});
test('long cards start at the top and system reading scroll affects only temporary practice transform', () => {
  const card={x:120,y:100,width:720};const first=practiceViewport(card,1000,0,800,1600),next=practiceViewport(card,1000,300,800,1600);
  assert.equal((card.y+first.y)*first.zoom,20);
  assert.equal(first.zoom,next.zoom);assert.equal(first.x,next.x);assert.equal((next.y-first.y)*next.zoom,-300);
});
