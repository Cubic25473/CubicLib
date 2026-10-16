import test from 'node:test';import assert from 'node:assert/strict';
import {bezierAt,headingAt,clearBezier,roundRoute,sampleCurves} from '../cubic-ftc/src/main/assets/cubiclib/bezier.js';
const field={sizeMm:3000,obstacles:[]};
test('quadratic and cubic evaluation',()=>{assert.deepEqual(bezierAt([{x:0,y:0},{x:100,y:100},{x:0,y:200}],.5),{x:50,y:100});assert.deepEqual(bezierAt([{x:0,y:0},{x:0,y:100},{x:100,y:100},{x:100,y:0}],.5),{x:50,y:75});});
test('heading is independent, including wraparound',()=>{assert.equal(headingAt(0,0,.5),0);assert.ok(Math.abs(Math.abs(headingAt(170*Math.PI/180,-170*Math.PI/180,.5))-Math.PI)<1e-10);});
test('curve crossing obstacle is rejected even with clear endpoints',()=>{const f={...field,obstacles:[{x:1450,y:1400,w:100,h:200}]};assert.equal(clearBezier([{x:500,y:500},{x:1500,y:2500},{x:2500,y:500}],f,30),false);});
test('reject curve leaving field',()=>{assert.equal(clearBezier([{x:100,y:100},{x:-2000,y:1500},{x:100,y:2900}],field,20),false);});
test('rounding preserves endpoints and produces genuine curves',()=>{const points=[{x:400,y:400},{x:400,y:1600},{x:2200,y:1600}],curves=roundRoute(points,field,100),samples=sampleCurves(curves);assert.ok(curves.some(p=>p.length===3));assert.deepEqual(samples[0],points[0]);assert.deepEqual(samples.at(-1),points.at(-1));assert.ok(curves.every(p=>clearBezier(p,field,100)));});
test('reject nonfinite points',()=>{assert.throws(()=>bezierAt([{x:NaN,y:0},{x:1,y:1}],.5));});
