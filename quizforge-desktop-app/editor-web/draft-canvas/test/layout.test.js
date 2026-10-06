import test from 'node:test';
import assert from 'node:assert/strict';
import {defaultLayout,configureLayout,initialCardWidth} from '../src/shared/ui/layout.js';
import {practiceViewport} from '../src/learning/mode-policy.js';

test('invalid layout patches are rejected without partially replacing configuration',()=>{
  const original=defaultLayout();
  for(const patch of [null,[],{cardWidth:0},{cardWidth:'720px'},{cardWidth:Infinity},{maxCardWidth:'0%'},{maxCardWidth:'101%'},{maxCardWidth:'url(remote)'},{padding:-1},{horizontalAlign:'floating'},{verticalAlign:'baseline'},{paper:'yellow'},{navigation:false}])
    assert.throws(()=>configureLayout(original,patch));
  assert.equal(original.cardWidth,720);assert.equal(original.padding,20);
  assert.equal(defaultLayout('EDITOR').verticalAlign,'top');
});

test('alignment, percentage limits and reading scroll apply without moving saved World geometry',()=>{
  const card=Object.freeze({x:125,y:70,width:600});
  for(const horizontalAlign of ['left','center','right'])for(const verticalAlign of ['top','center','bottom']){
    const layout=configureLayout(defaultLayout(),{horizontalAlign,verticalAlign,padding:30,maxCardWidth:'80%'});
    const view=practiceViewport(card,1000,0,800,300,layout);
    const left=(card.x+view.x)*view.zoom,top=(card.y+view.y)*view.zoom;
    assert.equal(left,horizontalAlign==='left'?30:horizontalAlign==='right'?370:200);
    assert.equal(top,verticalAlign==='top'?30:verticalAlign==='bottom'?470:250);
    const scrolled=practiceViewport(card,1000,125,800,300,layout);
    assert.equal((view.y-scrolled.y)*view.zoom,125);
  }
  assert.deepEqual(card,{x:125,y:70,width:600});
});

test('narrow previews fit the available area and long cards always start within reach',()=>{
  const layout=configureLayout(defaultLayout(),{cardWidth:900,maxCardWidth:'90%',verticalAlign:'bottom',padding:24});
  const card={x:120,y:70,width:900},view=practiceViewport(card,500,0,600,2400,layout);
  assert.equal(initialCardWidth(layout,500),406.8);
  assert.ok(Math.abs(card.width*view.zoom-406.8)<1e-8);
  assert.equal((card.y+view.y)*view.zoom,24);
});
