import {roundRoute,sampleCurves} from './bezier.js';
export const distance=(a,b)=>Math.hypot(a.x-b.x,a.y-b.y);
export function continuous(a,b){return a.valid&&b.valid&&a.generation===b.generation&&b.sequence===a.sequence+1&&b.t>a.t&&b.t-a.t<=.3;}
export function clearPoint(p,field,r){
  if(p.x<r||p.y<r||p.x>field.sizeMm-r||p.y>field.sizeMm-r)return false;
  return field.obstacles.every(o=>Math.hypot(p.x-Math.max(o.x,Math.min(p.x,o.x+o.w)),p.y-Math.max(o.y,Math.min(p.y,o.y+o.h)))>r);
}
export function clearSegment(a,b,field,r){
  // Exact segment vs conservatively expanded rectangle, including endpoints.
  if(!clearPoint(a,field,r)||!clearPoint(b,field,r))return false;
  return field.obstacles.every(o=>{
    let lo=0,hi=1;
    for(const [p,d,min,max] of [[a.x,b.x-a.x,o.x-r,o.x+o.w+r],[a.y,b.y-a.y,o.y-r,o.y+o.h+r]]){
      if(Math.abs(d)<1e-10){if(p<min||p>max)return true;}
      else{let t1=(min-p)/d,t2=(max-p)/d;if(t1>t2)[t1,t2]=[t2,t1];lo=Math.max(lo,t1);hi=Math.min(hi,t2);if(lo>hi)return true;}
    }
    return false;
  });
}
/** Conservative static-field candidate, never an autonomous drive command. */
export function route(start,end,field,r){
  if(clearSegment(start,end,field,r))return [start,end];
  const step=60,n=Math.floor(field.sizeMm/step)+1,key=(x,y)=>y*n+x;
  const cell=p=>({x:Math.round(p.x/step),y:Math.round(p.y/step)}),point=c=>({x:c.x*step,y:c.y*step});
  const s=cell(start),e=cell(end),sp=point(s),ep=point(e);
  if(!clearSegment(start,sp,field,r)||!clearSegment(ep,end,field,r))return null;
  const costs=new Map([[key(s.x,s.y),0]]),parents=new Map(),closed=new Set(),open=[{...s,f:distance(sp,ep)}];
  while(open.length){
    let best=0;for(let i=1;i<open.length;i++)if(open[i].f<open[best].f)best=i;
    const c=open.splice(best,1)[0],ck=key(c.x,c.y);if(closed.has(ck))continue;closed.add(ck);
    if(c.x===e.x&&c.y===e.y){let k=ck,path=[end];while(k!==undefined){path.push({x:(k%n)*step,y:Math.floor(k/n)*step});k=parents.get(k);}path.push(start);path.reverse();
      // Greedy smoothing with collision validation on every shortcut.
      const smooth=[path[0]];let i=0;while(i<path.length-1){let j=path.length-1;while(j>i+1&&!clearSegment(path[i],path[j],field,r))j--;smooth.push(path[j]);i=j;}return smooth;
    }
    for(let dy=-1;dy<=1;dy++)for(let dx=-1;dx<=1;dx++){
      if(!dx&&!dy)continue;const q={x:c.x+dx,y:c.y+dy};if(q.x<0||q.y<0||q.x>=n||q.y>=n)continue;
      const qk=key(q.x,q.y);if(closed.has(qk)||!clearSegment(point(c),point(q),field,r))continue;
      const g=costs.get(ck)+Math.hypot(dx,dy)*step;
      if(g<(costs.get(qk)??Infinity)){costs.set(qk,g);parents.set(qk,ck);open.push({...q,f:g+distance(point(q),ep)});}
    }
  }return null;
}
export function analyze(samples,events,config,field){
  let travelled=0,stationary=0,coverage=0,gaps=0,pauseStart=null;const suggestions=[];
  const closePause=end=>{if(pauseStart!==null&&end-pauseStart>=1.5)suggestions.push({kind:'pause',start:pauseStart,end,title:`${(end-pauseStart).toFixed(1)} s stationary`,detail:'Review this pause alongside intake, scoring and traffic. Stationary time is not necessarily wasted.',label:'Review opportunity · context required'});pauseStart=null;};
  for(let i=1;i<samples.length;i++){
    const a=samples[i-1],b=samples[i];if(!continuous(a,b)){gaps++;closePause(a.t);continue;}
    const dt=b.t-a.t,d=distance(a.pose,b.pose);coverage+=dt;if(d>=2)travelled+=d;
    if(d/dt<30){stationary+=dt;if(pauseStart===null)pauseStart=a.t;}else closePause(a.t);
  }if(samples.length)closePause(samples.at(-1).t);
  let pickup=null;const cycles=[];
  for(const e of events){if(e.name==='pickup-confirmed')pickup=e;else if(e.name==='score-confirmed'&&pickup){if(e.t>pickup.t)cycles.push([pickup,e]);pickup=null;}}
  for(const [a,b] of cycles){
    const segment=samples.filter(s=>s.t>=a.t&&s.t<=b.t);if(segment.length<2||segment[0].t-a.t>.3||b.t-segment.at(-1).t>.3)continue;
    if(!segment.every((s,i)=>i===0?s.valid:continuous(segment[i-1],s)))continue;
    if(!config?.fieldGeometryConfirmed||config.fieldRevision!==field.id)continue;
    const radius=Math.hypot(config.robotLengthMm,config.robotWidthMm)/2+config.clearanceMm;
    if(!Number.isFinite(radius)||radius<=0)continue;
    const polyline=route(segment[0].pose,segment.at(-1).pose,field,radius);if(!polyline)continue;
    const curves=roundRoute(polyline,field,radius),candidate=sampleCurves(curves);
    const length=points=>points.slice(1).reduce((v,p,i)=>v+distance(points[i],p),0);
    const actual=length(segment.map(s=>s.pose)),planned=length(candidate),saving=actual-planned;
    if(saving>150&&saving/actual>.15)suggestions.push({kind:'route',start:a.t,end:b.t,path:candidate,curves,title:`${(saving/1000).toFixed(2)} m shorter candidate`,detail:`Same cycle endpoints; ${(actual/1000).toFixed(2)} m recorded vs ${(planned/1000).toFixed(2)} m static Bézier/line route. Pickup/scoring approach, traffic and moving pieces need driver review.`,label:'Static geometry estimate · not a time saving'});
  }
  return {travelled,stationary,coverage,gaps,cycles,elapsed:samples.length?samples.at(-1).t:0,suggestions:suggestions.sort((a,b)=>a.start-b.start).slice(0,30)};
}
export function parseSession(text){
  const lines=text.split(/\r?\n/).filter(x=>x.trim());let meta=null,end=null;const samples=[],events=[];let truncated=false;
  for(let i=0;i<lines.length;i++){
    let item;try{item=JSON.parse(lines[i]);}catch(e){if(i===lines.length-1){truncated=true;break;}throw new Error('Invalid JSONL at line '+(i+1));}
    if(item.type==='meta'){if(meta)throw new Error('Multiple session headers');meta=item;}
    else if(item.type==='sample'){
      if(samples.length>=18000)throw new Error('Session exceeds 18,000 samples');
      const p=item.pose,prev=samples.at(-1);
      if(!p||![item.t,p.x,p.y,p.heading,item.sequence,item.generation].every(Number.isFinite)||item.t<0||typeof item.valid!=='boolean'||(prev&&(item.t<=prev.t||item.sequence<=prev.sequence)))throw new Error('Invalid or unordered sample');samples.push(item);
    }else if(item.type==='event'){if(events.length>=5000||!Number.isFinite(item.t)||item.t<0||typeof item.name!=='string'||item.name.length>160)throw new Error('Invalid event');events.push(item);}
    else if(item.type==='end')end=item;
  }
  if(!meta||meta.schema!==1)throw new Error('Unsupported or missing CubicLib session header');
  return {meta,samples,events:events.sort((a,b)=>a.t-b.t),complete:!!end&&!truncated,dropped:end?.dropped??0,error:end?.error??''};
}
