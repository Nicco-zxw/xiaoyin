'use client';
import {createContext,useCallback,useContext,useEffect,useRef,useState,type ReactNode} from 'react';
import {useQueryClient} from '@tanstack/react-query';
import {TaskTracker,type Snapshot} from '@/lib/tracker';
import {api} from '@/lib/api';
import type {Task,User} from '@/lib/types';
type Context=Snapshot&{add:(task:Task)=>void;retry:()=>void};
const TaskContext=createContext<Context>({tasks:{},queryError:null,add:()=>{},retry:()=>{}});
export const useTasks=()=>useContext(TaskContext);
export function TaskProvider({user,children}:{user:User|null;children:ReactNode}){
 const [snapshot,setSnapshot]=useState<Snapshot>({tasks:{},queryError:null});const tracker=useRef<TaskTracker|null>(null);const cache=useQueryClient();
 const refresh=useCallback(()=>{if(user&&tracker.current)void tracker.current.refresh(async signal=>(await api<{items:Task[]}>('/tasks/active',{signal})).items,(id,signal)=>api<Task>(`/tasks/${id}`,{signal}));},[user]);
 useEffect(()=>{
  if(!user)return;
  const instance=new TaskTracker();tracker.current=instance;
  const unsubscribe=instance.subscribe(()=>{
   const next=instance.snapshot();setSnapshot(next);
   if(Object.values(next.tasks).some(t=>t.status==='SUCCEEDED'||t.status==='FAILED'))void cache.invalidateQueries({queryKey:['history',user.id]});
  });
  refresh();window.addEventListener('focus',refresh);window.addEventListener('online',refresh);
  return()=>{unsubscribe();instance.dispose();tracker.current=null;window.removeEventListener('focus',refresh);window.removeEventListener('online',refresh);};
 },[user,cache,refresh]);
 const pending=Object.values(snapshot.tasks).some(t=>t.status==='QUEUED'||t.status==='RUNNING');
 useEffect(()=>{
  if(!pending||snapshot.queryError)return;
  const timer=window.setInterval(refresh,2000);return()=>window.clearInterval(timer);
 },[pending,snapshot.queryError,refresh]);
 return <TaskContext.Provider value={{...snapshot,add:task=>{tracker.current?.merge([task]);refresh();},retry:refresh}}>{children}</TaskContext.Provider>;
}
