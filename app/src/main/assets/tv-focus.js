(function(){
  'use strict';
  const api=window.__aitv=window.__aitv||{};
  const selector='a[href],button,input,textarea,select,summary,[role=button],[tabindex]:not([tabindex="-1"])';
  const candidates=()=>Array.from(document.querySelectorAll(selector)).filter(el=>{
    const r=el.getBoundingClientRect(),s=getComputedStyle(el);
    return !el.disabled && el.getAttribute('aria-disabled')!=='true' && s.display!=='none' && s.visibility!=='hidden' && r.width>2 && r.height>2;
  });
  const center=el=>{const r=el.getBoundingClientRect();return {x:(r.left+r.right)/2,y:(r.top+r.bottom)/2};};
  const clear=()=>{const el=document.querySelector('[data-aitv-focus=true]');if(el){el.style.removeProperty('outline');el.style.removeProperty('outline-offset');el.removeAttribute('data-aitv-focus');}};
  api.move=dir=>{
    const els=candidates(),current=document.querySelector('[data-aitv-focus=true]');
    if(!els.length)return false;
    const pos=current&&els.includes(current)?center(current):{x:innerWidth/2,y:innerHeight/2};
    let best=null,score=Infinity;
    for(const el of els){
      if(el===current)continue;
      const q=center(el),dx=q.x-pos.x,dy=q.y-pos.y;
      const primary=dir==='left'?-dx:dir==='right'?dx:dir==='up'?-dy:dy;
      const secondary=(dir==='left'||dir==='right')?Math.abs(dy):Math.abs(dx);
      if(primary<3)continue;
      const value=primary+2.5*secondary;
      if(value<score){score=value;best=el;}
    }
    if(!best&&!current)best=els[0];
    if(!best)return false;
    clear();best.setAttribute('data-aitv-focus','true');
    best.style.outline='4px solid #00ddff';best.style.outlineOffset='4px';
    best.focus({preventScroll:true});best.scrollIntoView({block:'nearest',inline:'nearest'});
    return true;
  };
  api.activate=()=>{
    const el=document.querySelector('[data-aitv-focus=true]');
    if(!el)return false;
    el.focus();el.click();return true;
  };
  api.reset=()=>{clear();return true;};
  return true;
})()