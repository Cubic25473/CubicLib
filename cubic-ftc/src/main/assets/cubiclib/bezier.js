// Geometry only. Heading remains independent of the travel tangent for mecanum strafing.
export function bezierAt(points,t){
  if(!Array.isArray(points)||points.length<2||points.length>4||!Number.isFinite(t)||t<0||t>1||points.some(p=>!Number.isFinite(p.x)||!Number.isFinite(p.y)))throw new Error('Invalid Bezier control points or parameter');
  const q=points.map(p=>({x:p.x,y:p.y}));for(let n=q.length-1;n>0;n--)for(let i=0;i<n;i++)q[i]={x:(1-t)*q[i].x+t*q[i+1].x,y:(1-t)*q[i].y+t*q[i+1].y};return q[0];
}
const lerp=(a,b,t)=>({x:a.x+(b.x-a.x)*t,y:a.y+(b.y-a.y)*t});
export function splitBezier(points){let layer=points,left=[points[0]],right=[points.at(-1)];while(layer.length>1){layer=layer.slice(1).map((p,i)=>lerp(layer[i],p,.5));left.push(layer[0]);right.unshift(layer.at(-1));}return [left,right];}
export function headingAt(start,end,t){if(![start,end,t].every(Number.isFinite)||t<0||t>1)throw new Error('Invalid heading');const delta=Math.atan2(Math.sin(end-start),Math.cos(end-start)),h=start+t*delta;return Math.atan2(Math.sin(h),Math.cos(h));}
// Prove each curve's control-point bounding box is free. Subdivision tightens the bound.
// Unresolved boxes are rejected, so a collision between sampled points cannot slip through.
export function clearBezier(points,field,r,depth=0){
  bezierAt(points,0);if(!Number.isFinite(r)||r<0)throw new Error('Invalid clearance');
  const xs=points.map(p=>p.x),ys=points.map(p=>p.y),minX=Math.min(...xs),maxX=Math.max(...xs),minY=Math.min(...ys),maxY=Math.max(...ys);
  const inside=minX>=r&&minY>=r&&maxX<=field.sizeMm-r&&maxY<=field.sizeMm-r;
  const free=field.obstacles.every(o=>maxX<o.x-r||minX>o.x+o.w+r||maxY<o.y-r||minY>o.y+o.h+r);
  if(inside&&free)return true;
  if(depth>=12)return false;
  const [a,b]=splitBezier(points);return clearBezier(a,field,r,depth+1)&&clearBezier(b,field,r,depth+1);
}
/** Round internal polyline corners with quadratic Beziers; retain a line corner if unsafe. */
export function roundRoute(points,field,r){
  const segments=[];let cursor=points[0];
  const line=end=>{if(Math.hypot(end.x-cursor.x,end.y-cursor.y)>1e-8)segments.push([cursor,end]);cursor=end;};
  for(let i=1;i<points.length-1;i++){
    const a=points[i-1],b=points[i],c=points[i+1];let accepted=false;
    for(const fraction of [.3,.15,.075,.025]){
      const entry=lerp(b,a,fraction),exit=lerp(b,c,fraction),curve=[entry,b,exit];
      if(clearBezier(curve,field,r)){line(entry);segments.push(curve);cursor=exit;accepted=true;break;}
    }if(!accepted)line(b);
  }line(points.at(-1));return segments;
}
export function sampleCurves(segments){
  const result=[];for(const segment of segments){const steps=segment.length===2?1:64;for(let i=result.length?1:0;i<=steps;i++)result.push(bezierAt(segment,i/steps));}return result;
}
