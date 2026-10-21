/**
 * Standalone test of D-pad scrolling and picker-specific scroll targeting.
 * Run: node scripts/test-scroll.cjs
 */
const assert = require("node:assert/strict");
const fs = require("node:fs");
const vm = require("node:vm");

const source = fs.readFileSync("app/src/main/assets/tv-scroll.js", "utf8");

function element({parent=null, rect=[0,0,300,200], overflowX="visible", overflowY="visible",
                  h=200, sh=200, w=300, sw=300, role=null, state=null}={}) {
  const children = [];
  const calls = [];
  const node = {
    parentElement: parent, children, calls,
    clientHeight: h, scrollHeight: sh, clientWidth: w, scrollWidth: sw,
    overflowX, overflowY, role, state,
    getBoundingClientRect: () => {
      const [x,y,width,height]=rect;
      return {x,y,left:x,top:y,width,height,right:x+width,bottom:y+height};
    },
    getAttribute: k => k === "role" ? role : k === "data-state" ? state : null,
    contains(other) {
      for(let n=other;n;n=n.parentElement) if(n===node)return true;
      return false;
    },
    querySelectorAll: () => children.flatMap(c => [c, ...c.querySelectorAll("*")]),
    scrollBy: options => calls.push({...options})
  };
  if(parent) parent.children.push(node);
  return node;
}

function setup({popupOpen=false, popupScrollable=true, horizontal=false, pointerInsidePopup=true}={}) {
  const root = element({rect:[0,0,960,540],overflowY:"auto",h:540,sh:3500,w:960,sw:960});
  const body = element({parent:root,rect:[0,0,960,540],h:540,sh:3500,w:960,sw:960});
  const panel = element({parent:body,rect:[50,50,250,200],
    overflowY:horizontal?"hidden":"auto",overflowX:horizontal?"auto":"hidden",
    h:200,sh:horizontal?200:1000,w:250,sw:horizontal?1200:250});
  const leaf = element({parent:panel,rect:[80,75,50,35]});
  const popup = popupOpen ? element({parent:body,role:"dialog",state:"open",
    rect:[554,60,256,300],h:300,sh:300,w:256,sw:256}) : null;
  const popupList = popup && element({parent:popup,rect:[560,105,245,220],
    overflowY:"auto",h:220,sh:popupScrollable?900:220,w:245,sw:245});
  const popupSearch = popup && element({parent:popup,rect:[560,68,245,32]});
  const screen = {
    innerWidth:960,innerHeight:540,
    getComputedStyle(node){return {overflowY:node.overflowY,overflowX:node.overflowX,visibility:"visible",display:"block"};}
  };
  const doc = {
    body,documentElement:root,scrollingElement:root,activeElement:popup ? popupSearch : leaf,
    querySelectorAll:() => popup ? [popup] : [],
    elementFromPoint:(x,y)=>{
      if(popup && x >= 554 && x <= 810 && y >= 60 && y <= 360) {
        return popupList && y >= 105 ? popupList : popupSearch;
      }
      return popup && !pointerInsidePopup ? leaf : leaf;
    }
  };
  const sandbox={window:screen,document:doc,innerWidth:960,innerHeight:540,getComputedStyle:screen.getComputedStyle};
  vm.runInNewContext(source,sandbox);
  return {scroll:screen.__aiTvScroll,root,panel,popup,popupList,doc};
}

let t=setup();
assert.equal(t.scroll("down",180,0.1,0.15),true);
assert.equal(t.panel.calls[0].top,180,"nested panel scrolls");
assert.equal(t.root.calls.length,0);

t=setup();
t.scroll("up",90,0.1,0.15);
assert.equal(t.panel.calls[0].top,-90);

t=setup({horizontal:true});
t.scroll("right",220,0.1,0.15);
assert.equal(t.panel.calls[0].left,220);

t=setup();
t.doc.elementFromPoint=()=>t.body;
t.scroll("down",100,0.5,0.9);
assert.equal(t.root.calls[0].top,100,"fallback scrolls document root");

t=setup({popupOpen:true,popupScrollable:true,pointerInsidePopup:false});
t.scroll("down",150,0.2,0.5);
assert.equal(t.popupList.calls[0].top,150,"picker list scrolls despite pointer outside");
assert.equal(t.root.calls.length,0);
assert.equal(t.panel.calls.length,0);

t=setup({popupOpen:true,popupScrollable:false,pointerInsidePopup:false});
assert.equal(t.scroll("down",150,0.2,0.5),false,"non-scrollable modal isn't a page scroll");
assert.equal(t.root.calls.length,0);

t=setup();
assert.equal(t.scroll("diagonal",100,0.2,0.2),false);
console.log("Scroll checks passed: nested, direction, page fallback, popup list, popup no-op.");
