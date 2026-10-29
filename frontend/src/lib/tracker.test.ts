import {describe,it,expect} from 'vitest';
import {TaskTracker} from './tracker';
import type {Task} from './types';
const task=(id:string,status:Task['status']='RUNNING'):Task=>({id,status,templateId:'pet',templateTitle:'宠物',currentStage:'MOCK_GENERATION',steps:[],failure:null,resultUrl:null,createdAt:'2026-10-29T00:00:00Z',startedAt:null,finishedAt:null});
describe('task lifecycle',()=>{
 it('keeps independent tasks and stops tracking terminal tasks',()=>{
  const tracker=new TaskTracker(); tracker.merge([task('a'),task('b')]);
  tracker.merge([{...task('a','SUCCEEDED'),resultUrl:'/result/a'}]);
  expect(tracker.snapshot().tasks.b.status).toBe('RUNNING');
  expect(tracker.snapshot().tasks.a.resultUrl).toBe('/result/a');
  expect(tracker.activeIds()).toEqual(['b']);
  tracker.merge([task('b','FAILED')]);expect(tracker.activeIds()).toEqual([]);
 });
 it('keeps server information on query failure and ignores stale responses after disposal',async()=>{
  const tracker=new TaskTracker();tracker.merge([task('a')]);
  await tracker.refresh(async()=>{throw Error('offline');},async()=>task('a'));
  expect(tracker.snapshot().tasks.a.status).toBe('RUNNING');expect(tracker.snapshot().queryError).toBeTruthy();
  await tracker.refresh(async()=>[],async()=>task('a','FAILED'));
  expect(tracker.snapshot().tasks.a.status).toBe('FAILED');expect(tracker.snapshot().queryError).toBeNull();
  const other=new TaskTracker();let resolve!:(tasks:Task[])=>void;
  const pending=other.refresh(()=>new Promise(r=>{resolve=r;}),async()=>task('a'));
  other.dispose();resolve([task('other-user')]);await pending;
  expect(other.snapshot().tasks).toEqual({});
 });
 it('does not regress terminal status from a stale response',()=>{
  const tracker=new TaskTracker();tracker.merge([task('a','SUCCEEDED')]);tracker.merge([task('a')]);
  expect(tracker.snapshot().tasks.a.status).toBe('SUCCEEDED');
 });
 it('preserves one failed lookup while accepting another terminal result and recovers independently',async()=>{
  const tracker=new TaskTracker();tracker.merge([task('a'),task('b')]);
  await tracker.refresh(async()=>[],async id=>{if(id==='a')throw Error('temporary 503');return {...task('b','FAILED'),failure:{stage:'MOCK_GENERATION',code:'SIMULATED_STAGE_FAILURE',message:'指定失败'}};});
  expect(tracker.snapshot().tasks.a.status).toBe('RUNNING');
  expect(tracker.snapshot().tasks.b.failure?.message).toBe('指定失败');
  expect(tracker.snapshot().queryError).toBeTruthy();
  const lookedUp:string[]=[];
  await tracker.refresh(async()=>[],async id=>{lookedUp.push(id);return task(id,'SUCCEEDED');});
  expect(lookedUp).toEqual(['a']);expect(tracker.snapshot().queryError).toBeNull();
  expect(tracker.snapshot().tasks.b.status).toBe('FAILED');expect(tracker.activeIds()).toEqual([]);
 });
 it('does not overlap refresh requests and aborts its signal when disposed',async()=>{
  const tracker=new TaskTracker();let resolve!:(value:Task[])=>void;let signal!:AbortSignal;
  let calls=0;
  const active=(s:AbortSignal)=>{calls++;signal=s;return new Promise<Task[]>(r=>{resolve=r;});};
  const pending=tracker.refresh(active,async()=>task('a'));
  await tracker.refresh(active,async()=>task('a'));expect(calls).toBe(1);
  tracker.dispose();expect(signal.aborted).toBe(true);resolve([task('a')]);await pending;
  expect(tracker.snapshot().tasks).toEqual({});
 });
});
