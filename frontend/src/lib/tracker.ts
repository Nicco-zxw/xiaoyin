import type {Task} from './types';
export type Snapshot={tasks:Record<string,Task>;queryError:string|null};
export class TaskTracker {
 private state:Snapshot={tasks:{},queryError:null};
 private listeners=new Set<()=>void>();
 private disposed=false;
 private request:AbortController|null=null;
 snapshot=():Snapshot=>this.state;
 subscribe=(listener:()=>void)=>{this.listeners.add(listener);return()=>{this.listeners.delete(listener);};};
 private emit(){for(const listener of this.listeners)listener();}
 merge(tasks:Task[]){
  if(this.disposed)return;
  const next={...this.state.tasks};
  for(const task of tasks){const previous=next[task.id];if(previous&&(previous.status==='SUCCEEDED'||previous.status==='FAILED'))continue;next[task.id]=task;}
  this.state={...this.state,tasks:next};this.emit();
 }
 activeIds():string[]{return Object.values(this.state.tasks).filter(t=>t.status==='QUEUED'||t.status==='RUNNING').map(t=>t.id);}
 async refresh(active:(signal:AbortSignal)=>Promise<Task[]>,one:(id:string,signal:AbortSignal)=>Promise<Task>){
  if(this.disposed||this.request)return;
  const controller=new AbortController();this.request=controller;
  const before=this.activeIds();
  try{
   const current=await active(controller.signal);
   const remaining=new Set(current.map(t=>t.id));
   const settled=await Promise.allSettled(before.filter(id=>!remaining.has(id)).map(id=>one(id,controller.signal)));
   if(this.disposed||controller.signal.aborted)return;
   this.merge([...current,...settled.flatMap(r=>r.status==='fulfilled'?[r.value]:[])]);
   this.state={...this.state,queryError:settled.some(r=>r.status==='rejected')?'部分任务暂时无法更新':null};this.emit();
  }catch(e){if(!this.disposed&&!controller.signal.aborted){this.state={...this.state,queryError:e instanceof Error?e.message:'查询暂时不可用'};this.emit();}}
  finally{if(this.request===controller)this.request=null;}
 }
 dispose(){this.disposed=true;this.request?.abort();this.listeners.clear();}
}
