import {afterEach,beforeEach,describe,expect,it,vi} from 'vitest';
import {act,cleanup,fireEvent,render,screen} from '@testing-library/react';
import {QueryClient,QueryClientProvider} from '@tanstack/react-query';
import {TaskProvider,useTasks} from './tasks';
import {api} from '@/lib/api';
import type {Task,User} from '@/lib/types';

vi.mock('@/lib/api',()=>({api:vi.fn()}));
const request=vi.mocked(api);
const user:User={id:'user-a',username:'alice',createdAt:'2026-10-29T00:00:00Z'};
const task=(id:string,status:Task['status']='RUNNING'):Task=>({id,status,templateId:'pet',templateTitle:'宠物',currentStage:'MOCK_GENERATION',steps:[],failure:null,resultUrl:null,createdAt:user.createdAt,startedAt:null,finishedAt:null});
function Observer(){
 const {tasks,queryError,retry}=useTasks();
 return <><output data-testid="tasks">{JSON.stringify(tasks)}</output><output data-testid="query-error">{queryError}</output><button onClick={retry}>恢复查询</button></>;
}
function tree(client:QueryClient,identity:User=user){return <QueryClientProvider client={client}><TaskProvider key={identity.id} user={identity}><Observer/></TaskProvider></QueryClientProvider>;}
async function flush(){await act(async()=>{await Promise.resolve();});}
async function advance(ms:number){await act(async()=>{await vi.advanceTimersByTimeAsync(ms);});}
beforeEach(()=>{vi.useFakeTimers();request.mockReset();});
afterEach(()=>{cleanup();vi.clearAllTimers();vi.useRealTimers();});

describe('actual TaskProvider polling lifecycle',()=>{
 it('does not schedule polling when the server has no active tasks',async()=>{
  request.mockResolvedValue({items:[]});
  render(tree(new QueryClient()));await flush();
  expect(request).toHaveBeenCalledTimes(1);await advance(20000);
  expect(request).toHaveBeenCalledTimes(1);
 });
 it('stops polling each completed task and stops the timer after the last task',async()=>{
  let phase=0;
  request.mockImplementation(async path=>path==='/tasks/active'?{items:phase===0?[task('a'),task('b')]:phase===1?[task('b')]:[]}:task(path.endsWith('/a')?'a':'b','SUCCEEDED'));
  render(tree(new QueryClient()));await flush();
  phase=1;await advance(2000);
  expect(screen.getByTestId('tasks').textContent).toContain('SUCCEEDED');
  expect(request.mock.calls.filter(([path])=>path==='/tasks/a')).toHaveLength(1);
  phase=2;await advance(2000);
  const count=request.mock.calls.length;await advance(20000);
  expect(request).toHaveBeenCalledTimes(count);
  expect(request.mock.calls.filter(([path])=>path==='/tasks/a')).toHaveLength(1);
  expect(request.mock.calls.filter(([path])=>path==='/tasks/b')).toHaveLength(1);
 });
 it('preserves processing state on an outage, pauses polling and resumes via retry',async()=>{
  let offline=false,finished=false;
  request.mockImplementation(async path=>{if(offline)throw Error('offline');return path==='/tasks/active'?{items:finished?[]:[task('a')]}:task('a','SUCCEEDED');});
  render(tree(new QueryClient()));await flush();offline=true;await advance(2000);
  expect(screen.getByTestId('query-error').textContent).toBe('offline');
  expect(screen.getByTestId('tasks').textContent).toContain('RUNNING');
  expect(screen.getByTestId('tasks').textContent).not.toContain('FAILED');
  const count=request.mock.calls.length;await advance(10000);expect(request).toHaveBeenCalledTimes(count);
  offline=false;finished=true;fireEvent.click(screen.getByRole('button',{name:'恢复查询'}));await flush();
  expect(screen.getByTestId('query-error').textContent).toBe('');
  expect(screen.getByTestId('tasks').textContent).toContain('SUCCEEDED');
 });
 it('aborts old identity requests and removes timers and focus subscriptions on unmount',async()=>{
  let resolve!:(value:unknown)=>void;let oldSignal:AbortSignal|undefined;
  request.mockImplementationOnce((_path,options)=>{oldSignal=options?.signal as AbortSignal;return new Promise(r=>{resolve=r;});}).mockResolvedValue({items:[task('b')]});
  const client=new QueryClient();const view=render(tree(client));await flush();
  view.rerender(tree(client,{...user,id:'user-b',username:'bob'}));await flush();
  expect(oldSignal?.aborted).toBe(true);
  await act(async()=>{resolve({items:[task('a')]});});
  const visible=JSON.parse(screen.getByTestId('tasks').textContent||'{}');
  expect(Object.keys(visible)).toEqual(['b']);
  view.unmount();const count=request.mock.calls.length;
  await advance(10000);window.dispatchEvent(new Event('focus'));window.dispatchEvent(new Event('online'));await flush();
  expect(request).toHaveBeenCalledTimes(count);
 });
});
