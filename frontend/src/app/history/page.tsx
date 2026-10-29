'use client';
import {useState} from 'react';
import Link from 'next/link';
import {useQuery} from '@tanstack/react-query';
import {useAuth} from '@/components/providers';
import {useTasks} from '@/components/tasks';
import {TaskCard} from '@/components/task-card';
import {api} from '@/lib/api';
import type {Page,Task} from '@/lib/types';
export default function History(){
 const {user,loading}=useAuth();const {tasks,queryError,retry}=useTasks();const [page,setPage]=useState(0);
 const history=useQuery({queryKey:['history',user?.id,'all',page],enabled:!!user,queryFn:({signal})=>api<Page<Task>>(`/tasks?page=${page}&size=12`,{signal})});
 const combined=new Map((history.data?.items||[]).map(t=>[t.id,t]));
 for(const task of Object.values(tasks)){if(page===0||combined.has(task.id))combined.set(task.id,task);}
 const records=[...combined.values()].sort((a,b)=>b.createdAt.localeCompare(a.createdAt));
 return <section className="history-page"><div className="section-heading"><div><p className="eyebrow">A COLLECTION OF YOUR IDEAS</p><h1>我的作品</h1><p className="muted">每一次灵感，都值得被好好收藏。</p></div><button className="button secondary" onClick={()=>{void history.refetch();retry();}} disabled={!user}>↻ 刷新记录</button></div>{loading?<div className="empty">正在确认登录状态…</div>:!user?<div className="empty"><h2>登录，找回你的创意</h2><Link className="button" href="/login?next=/history">登录 / 注册 ↗</Link></div>:<>{queryError&&<div className="banner" role="alert">任务状态暂时无法更新，原有状态已保留。<button onClick={retry}>恢复查询</button></div>}{history.isError&&<div className="banner" role="alert">历史记录加载失败 <button onClick={()=>void history.refetch()}>重试</button></div>}{!records.length?<div className="empty"><span className="empty-symbol">▧</span><h2>{history.isPending?'正在加载作品…':'你的第一份作品，还在等你'}</h2><Link className="button" href="/">去发现模板 ↗</Link></div>:<div className="task-grid history-grid">{records.map(t=><TaskCard key={t.id} task={t}/>)}</div>}<div className="pagination"><button disabled={page===0||history.isFetching} onClick={()=>setPage(p=>p-1)}>上一页</button><span>第 {page+1} 页 / 共 {Math.max(history.data?.totalPages||0,1)} 页</span><button disabled={history.isFetching||page+1>=(history.data?.totalPages||0)} onClick={()=>setPage(p=>p+1)}>下一页</button></div></>}</section>;
}
