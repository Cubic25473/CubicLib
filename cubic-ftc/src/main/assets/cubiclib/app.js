import {bezierAt,headingAt} from './bezier.js?v=20261016';
import {analyze,continuous,parseSession} from './analysis.js?v=20261016';
const $=id=>document.getElementById(id),field=await (await fetch('/field.json')).json();
const canvas=$('field'),ctx=canvas.getContext('2d'),S=field.sizeMm,pad=50,scale=800/S;
let data={samples:[],events:[],meta:null},view='live',play=false,at=0,selected=null,report=null,token='',config=null,lastResponse=0,latestStatus='Disconnected',polling=false;
const msg=text=>{$('notice').textContent=text;},el=(tag,text,cls)=>{const e=document.createElement(tag);e.textContent=text;if(cls)e.className=cls;return e;};
const map=p=>({x:pad+p.x*scale,y:850-p.y*scale});
function path(points,color,width=3,dashed=false){if(!points.length)return;ctx.beginPath();points.forEach((p,i)=>{const q=map(p);i?ctx.lineTo(q.x,q.y):ctx.moveTo(q.x,q.y);});ctx.strokeStyle=color;ctx.lineWidth=width;ctx.setLineDash(dashed?[10,8]:[]);ctx.stroke();ctx.setLineDash([]);}
function drawCurves(segments){
  ctx.beginPath();for(const points of segments){const p=points.map(map);ctx.moveTo(p[0].x,p[0].y);if(p.length===3)ctx.quadraticCurveTo(p[1].x,p[1].y,p[2].x,p[2].y);else if(p.length===4)ctx.bezierCurveTo(p[1].x,p[1].y,p[2].x,p[2].y,p[3].x,p[3].y);else ctx.lineTo(p[1].x,p[1].y);}
  ctx.strokeStyle='#69b7e7';ctx.lineWidth=3;ctx.setLineDash([10,8]);ctx.stroke();ctx.setLineDash([]);
}
function rect(x,y,w,h,color){const p=map({x,y:y+h});ctx.fillStyle=color;ctx.fillRect(p.x,p.y,w*scale,h*scale);}
function draw(){
  ctx.clearRect(0,0,900,900);rect(0,0,S,S,'#242c27');ctx.strokeStyle='#3b453d';ctx.lineWidth=1;
  for(let i=0;i<=6;i++){const v=i*S/6;path([{x:v,y:0},{x:v,y:S}],'#3b453d',1);path([{x:0,y:v},{x:S,y:v}],'#3b453d',1);}
  ctx.font='12px monospace';ctx.textAlign='center';ctx.fillStyle='#8a998b';for(let i=0;i<=6;i++){ctx.fillText(String(i*24),pad+i*800/6,875);ctx.fillText(String(i*24),26,854-i*800/6);}ctx.fillText('in',875,875);
  rect(0,2438.4,279.4,584.2,'#713f3a');rect(S-279.4,609.6,279.4,584.2,'#304f79');rect(0,0,584.2,50.8,'#c46559');rect(S-584.2,S-50.8,584.2,50.8,'#5688be');
  const h=field.obstacles[0];rect(h.x,h.y,h.w,h.h,'#343f35');ctx.strokeStyle='#6a7565';ctx.setLineDash([7,7]);const hp=map({x:h.x,y:h.y+h.h});ctx.strokeRect(hp.x,hp.y,h.w*scale,h.h*scale);ctx.setLineDash([]);
  for(const x of [1450,2130])for(const y of [1510,2110]){rect(x-190,y-180,380,360,x<1800?'#78473d':'#385b7b');}
  ctx.fillStyle='#d6ddcb';ctx.font='14px monospace';ctx.fillText('HIVE',450,450);ctx.font='10px monospace';ctx.fillStyle='#9ba993';ctx.fillText('CONSERVATIVE ENVELOPE',450,469);
  field.flowers.forEach(([x,y])=>{const p=map({x,y});ctx.beginPath();ctx.arc(p.x,p.y,9,0,Math.PI*2);ctx.fillStyle='#e7c86e';ctx.fill();ctx.beginPath();ctx.arc(p.x,p.y,4,0,Math.PI*2);ctx.fillStyle='#363d24';ctx.fill();});
  const samples=data.samples.filter(s=>s.t<=at);let segment=[];
  samples.forEach((s,i)=>{if(!s.valid||(i&&!continuous(samples[i-1],s))){path(segment,'#d8f58b',3);segment=[];}if(s.valid)segment.push(s.pose);});path(segment,'#d8f58b',3);
  if(selected){const pts=samples.filter(s=>s.t>=selected.start&&s.t<=selected.end&&s.valid).map(s=>s.pose);path(pts,'#f1aa75',5);if(selected.curves)drawCurves(selected.curves);else if(selected.path)path(selected.path,'#69b7e7',3,true);}
  const current=samples.at(-1);if(current){const p=map(current.pose),c=data.meta?.config??config;ctx.save();ctx.translate(p.x,p.y);ctx.rotate(-current.pose.heading);ctx.fillStyle=current.valid?'#f1aa7580':'#72727280';ctx.strokeStyle=current.valid?'#f1aa75':'#aaa';ctx.lineWidth=2;
    const l=(c?.robotLengthMm??450)*scale,w=(c?.robotWidthMm??450)*scale;ctx.fillRect(-l/2,-w/2,l,w);ctx.strokeRect(-l/2,-w/2,l,w);ctx.beginPath();ctx.moveTo(-l/4,0);ctx.lineTo(l/2+12,0);ctx.lineTo(l/2+2,-7);ctx.moveTo(l/2+12,0);ctx.lineTo(l/2+2,7);ctx.stroke();ctx.restore();
    $('pose-label').textContent=`${Math.round(current.pose.x)}, ${Math.round(current.pose.y)} mm · ${(current.pose.heading*180/Math.PI).toFixed(0)}°${current.valid?'':' · INVALID'}`;
  }else $('pose-label').textContent='No pose';
  $('time').textContent=at.toFixed(1)+' s';$('scrub').value=at;
}
function recompute(){
  const c=data.meta?.config??config;report=analyze(data.samples,data.events,c,field);
  $('distance').textContent=(report.travelled/1000).toFixed(2)+' m';$('elapsed').textContent=report.elapsed.toFixed(1)+' s';$('stationary').textContent=report.stationary.toFixed(1)+' s';$('cycles').textContent=report.cycles.length;
  $('mode').textContent=(view==='demo'?'SIMULATED · ':view==='live'?'LIVE · ':'REPLAY · ')+(data.meta?.opMode??'No session');
  $('scrub').max=Math.max(report.elapsed,.1);$('review-count').textContent=report.suggestions.length;
  const insights=$('insights');insights.replaceChildren();
  if(!report.suggestions.length)insights.append(el('p','No flagged segments. Cycle reports require pickup and score events.','empty'));
  report.suggestions.forEach(s=>{const b=el('button','', 'insight');b.append(el('b',s.title),el('span',s.detail),el('small',`${s.start.toFixed(1)}–${s.end.toFixed(1)} s · ${s.label}`));b.onclick=()=>{selected=s;at=s.end;play=false;draw();insights.querySelectorAll('button').forEach(e=>e.classList.remove('selected'));b.classList.add('selected');};insights.append(b);});
  $('quality').textContent=`${report.coverage.toFixed(1)} s usable coverage · ${report.gaps} data gaps · ${data.dropped??0} dropped records. ${c?.fieldGeometryConfirmed&&c.fieldRevision===field.id?'Static route estimates enabled; traffic and task approach remain unmodeled.':'Route comparisons disabled until field geometry is confirmed in setup.'}`;
  const events=$('events');events.replaceChildren();data.events.slice(-40).forEach(e=>{const row=el('div','', 'event');row.append(el('time',e.t.toFixed(1)+' s'),el('span',e.name));events.append(row);});draw();
}
async function api(url,options){const r=await fetch(url,options);if(!r.ok){let m=`Robot API unavailable (HTTP ${r.status})`;try{const body=await r.json();if(typeof body.error==='string')m=body.error;}catch{}throw new Error(m);}return r.json();}
async function poll(){
  if(polling)return;polling=true;
  try{
    const status=await api('/api/status');token=status.token;config=status.config;latestStatus=status.status;lastResponse=Date.now();
    $('connection').textContent=status.status;$('led').classList.add('connected');
    if(view==='live'){
      const response=await api('/api/live?after='+(data.samples.at(-1)?.sequence??-1)+'&id='+encodeURIComponent(data.meta?.id??''));
      if(view!=='live')return;
      if(response.id&&response.id!==data.meta?.id){data={samples:[],events:[],meta:response.meta};selected=null;}
      data.samples.push(...response.samples);data.events=response.events;data.dropped=response.dropped??0;data.error=response.error??'';data.complete=!!response.id&&!response.recording&&!response.finishing&&!response.error;
      if(response.recording&&!play)at=data.samples.at(-1)?.t??0;
      if(response.samples.length||response.events.length!==($('events').childElementCount)||!report)recompute();
      msg(status.error||response.error||(response.recording?'Recording TeleOp.':response.finishing?'Finishing recording…':data.samples.length?'TeleOp stopped. Recording saved.':'Waiting for TeleOp.'));
    }
  }catch(e){$('connection').textContent='Robot disconnected';$('led').classList.remove('connected');if(view==='live')msg('Robot disconnected. Check network.');}
  finally{polling=false;}
}
async function sessions(){try{const list=await api('/api/sessions');$('sessions').replaceChildren(new Option('Choose a recording…',''));list.forEach(s=>$('sessions').add(new Option(`${s.id.slice(0,8)} · ${(s.bytes/1024).toFixed(0)} KB`,s.id)));}catch(e){msg('Could not load recordings: '+e.message);}}
function load(session,mode){data=session;view=mode;at=data.samples.at(-1)?.t??0;selected=null;play=false;recompute();msg(mode==='demo'?'SIMULATED DATA':`${data.complete?'Saved session':'Incomplete / recovered session'} · ${data.samples.length} samples${data.error?' · '+data.error:''}`);}
$('sessions').onchange=async e=>{if(!e.target.value)return;try{const r=await fetch('/api/sessions/'+e.target.value);if(!r.ok)throw new Error('Recording unavailable');load(parseSession(await r.text()),'replay');}catch(e){msg(e.message);}};
$('refresh').onclick=sessions;$('live').onclick=()=>{view='live';data={samples:[],events:[],meta:null};selected=null;play=false;report=null;poll();};
$('scrub').oninput=e=>{at=+e.target.value;play=false;view=view==='live'?'replay':view;draw();};$('play').onclick=()=>{if(!data.samples.length)return;if(at>=data.samples.at(-1).t)at=0;play=!play;if(view==='live')view='replay';};
let lastFrame=performance.now();function frame(now){if(play){at=Math.min(at+(now-lastFrame)/1000,data.samples.at(-1)?.t??0);if(at>=data.samples.at(-1).t)play=false;draw();}$('play').textContent=play?'Ⅱ':'▶';lastFrame=now;if(view==='live'&&lastResponse&&Date.now()-lastResponse>3000){$('connection').textContent='Stale robot connection';$('led').classList.remove('connected');}requestAnimationFrame(frame);}requestAnimationFrame(frame);
$('export').onclick=()=>{if(!data.meta)return msg('Record or load a session first.');const records=[data.meta,...data.samples,...data.events];if(data.complete||view==='demo')records.push({type:'end',dropped:data.dropped??0,error:data.error??''});const blob=new Blob([records.map(x=>JSON.stringify(x)).join('\n')+'\n'],{type:'application/x-ndjson'}),url=URL.createObjectURL(blob),a=document.createElement('a');a.href=url;a.download=`cubiclib-${data.meta.id}.jsonl`;a.click();setTimeout(()=>URL.revokeObjectURL(url),1000);};
$('import').onclick=()=>$('file').click();$('file').onchange=async()=>{try{const file=$('file').files[0];if(!file)return;if(file.size>16*1024*1024)throw new Error('Session file exceeds 16 MB');load(parseSession(await file.text()),'replay');}catch(e){msg(e.message);}finally{$('file').value='';}};
$('setup').onclick=async()=>{try{const s=await api('/api/status');token=s.token;config=s.config;$('config-json').value=JSON.stringify(config,null,2);$('backend').value=config.backend;syncPoseFields(config.startPose);$('preset').value='custom';$('geometry').checked=config.fieldGeometryConfirmed;$('config-message').textContent=s.attached?'Stop the OpMode before saving configuration.':s.error||'';$('config-dialog').showModal();}catch(e){msg('Connect to the robot to configure hardware. Demo and replay remain available.');}};
$('close-setup').onclick=()=>$('config-dialog').close();
$('backend').onchange=()=>{try{const c=JSON.parse($('config-json').value);c.backend=$('backend').value;if(c.backend==='THREE_WHEEL'){c.wheels=[{...c.wheels[0],name:'odoLeftParallel',xMm:0,yMm:150,angleRadians:0},{...c.wheels[0],name:'odoRightParallel',xMm:0,yMm:-150,angleRadians:0},{...c.wheels[1],name:'odoPerpendicular',xMm:100,yMm:0,angleRadians:Math.PI/2}];}else if(c.wheels.length===3)c.wheels=[c.wheels[0],c.wheels[2]];$('config-json').value=JSON.stringify(c,null,2);$('config-message').textContent='Example mounting offsets inserted. Replace them with your measured values.';}catch(e){$('config-message').textContent=e.message;}};
$('preset').onchange=()=>{try{if($('preset').value==='custom')return;const c=JSON.parse($('config-json').value);c.wheels.forEach(w=>w.pod={wheelDiameterMm:$('preset').value==='four'?32:48,countsPerEncoderRev:2000,encoderRevsPerWheelRev:1});$('config-json').value=JSON.stringify(c,null,2);}catch(e){$('config-message').textContent=e.message;}};
$('config-form').onsubmit=async e=>{e.preventDefault();try{const c=JSON.parse($('config-json').value);c.startPose=readPoseFields();c.fieldGeometryConfirmed=$('geometry').checked;await api('/api/config',{method:'POST',headers:{'Content-Type':'application/json','X-Cubic-Token':token},body:JSON.stringify(c)});config=c;$('config-message').textContent='Saved on robot. Use Init to load this configuration.';}catch(e){$('config-message').textContent=e.message;}};
$('demo').onclick=()=>{
  const cfg={robotLengthMm:350,robotWidthMm:350,clearanceMm:30,fieldGeometryConfirmed:true,fieldRevision:field.id};
  // Two cubic arcs below the HIVE envelope. First arc strafes with a fixed heading.
  const first=[{x:400,y:500},{x:400,y:1000},{x:1500,y:1000},{x:1500,y:500}];
  const second=[first[3],{x:1500,y:300},{x:3000,y:300},{x:3000,y:1800}];
  const samples=[];
  for(let i=0;i<=480;i++){
    const t=i*.05;
    // Smoothstep starts/stops each arc gently; preserve the scoring pause at 12–15 s.
    const u=t<=12?t/12:t<15?1:(t-15)/9, eased=u*u*(3-2*u);
    const position=t<=12?bezierAt(first,eased):t<15?first[3]:bezierAt(second,eased);
    const heading=t<=15?0:headingAt(0,Math.PI/2,eased);
    samples.push({type:'sample',sequence:i,t,generation:0,valid:true,pose:{...position,heading},batteryVolts:12.8,loopMs:20});
  }
  load({meta:{type:'meta',schema:1,id:'demo',opMode:'Bézier / mecanum practice',config:cfg},samples,events:[{type:'event',t:0,name:'pickup-confirmed'},{type:'event',t:12,name:'score-confirmed'},{type:'event',t:15,name:'intake-start'}],complete:true},'demo');
  at=0;play=true;lastFrame=performance.now();draw();
};
draw();poll();setInterval(poll,250);sessions();

function syncPoseFields(p){$('start-x').value=p.x;$('start-y').value=p.y;$('start-heading').value=p.heading*180/Math.PI;}
function readPoseFields(){const x=$('start-x').valueAsNumber,y=$('start-y').valueAsNumber,degrees=$('start-heading').valueAsNumber;if(![x,y,degrees].every(Number.isFinite)||x<0||y<0||x>field.sizeMm||y>field.sizeMm)throw new Error('Start position must be inside the field; heading must be finite.');return {x,y,heading:Math.atan2(Math.sin(degrees*Math.PI/180),Math.cos(degrees*Math.PI/180))};}
for(const id of ['start-x','start-y','start-heading'])$(id).oninput=()=>{try{const c=JSON.parse($('config-json').value);c.startPose=readPoseFields();$('config-json').value=JSON.stringify(c,null,2);$('config-message').textContent='';}catch(e){$('config-message').textContent=e.message;}};
$('config-json').oninput=()=>{try{const c=JSON.parse($('config-json').value);if(c.startPose)syncPoseFields(c.startPose);}catch{}};
