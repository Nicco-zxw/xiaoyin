'use client';
import {use,useState,useEffect,useRef} from 'react';
import Link from 'next/link';
import {useQuery,useQueryClient} from '@tanstack/react-query';
import {useAuth} from '@/components/providers';
import {useTasks} from '@/components/tasks';
import {UploadSlot,type Slot} from '@/components/upload-slot';
import {TaskCard} from '@/components/task-card';
import {api,ApiError} from '@/lib/api';
import {stageNames,type Template,type Task,type Page,type Stage} from '@/lib/types';
export default function Detail({params}:{params:Promise<{id:string}>}){
 const {id}=use(params);const {user}=useAuth();const query=useQuery({queryKey:['template',id],queryFn:({signal})=>api<Template>(`/templates/${id}`,{signal})});
 if(query.isPending)return <div className="empty">正在加载模板…</div>;
 if(query.isError)return <div className="empty" role="alert">{query.error instanceof ApiError&&query.error.status===404?'这个模板不存在。':'模板加载失败。'} <button onClick={()=>void query.refetch()}>重新加载</button> <Link href="/">返回模板列表</Link></div>;
 return <Workspace key={`${id}:${user?.id||'guest'}`} template={query.data}/>;
}
function Workspace({template}:{template:Template}){
 const {user,loading}=useAuth();const cache=useQueryClient();const {tasks,add,queryError,retry}=useTasks();
 const [slots,setSlots]=useState<Slot[]>(()=>Array.from({length:template.requiredImageCount},()=>({asset:null,uploading:false,error:null})));
 const [submitting,setSubmitting]=useState(false);const [error,setError]=useState('');const [notice,setNotice]=useState('');const [delay,setDelay]=useState(6000);const [fail,setFail]=useState<Stage|''>('');const [simulate,setSimulate]=useState(false);
 const history=useQuery({queryKey:['history',user?.id,template.id],enabled:!!user,queryFn:({signal})=>api<Page<Task>>(`/tasks?templateId=${template.id}`,{signal})});
 const combined=new Map((history.data?.items||[]).map(t=>[t.id,t]));
 Object.values(tasks).filter(t=>t.templateId===template.id).forEach(t=>combined.set(t.id,t));
 const records=[...combined.values()].sort((a,b)=>b.createdAt.localeCompare(a.createdAt));
 const ready=slots.every(s=>s.asset&&!s.uploading);
 const submission=useRef<AbortController|null>(null);
 useEffect(()=>()=>submission.current?.abort(),[]);
 async function submit(){
  if(submitting||!ready||!user)return;setSubmitting(true);setError('');setNotice('');
  const controller=new AbortController();submission.current=controller;
  try{const task=await api<Task>('/tasks',{method:'POST',signal:controller.signal,body:JSON.stringify({templateId:template.id,assetIds:slots.map(s=>s.asset!.id),...(simulate?{simulation:{stageDelayMs:{MATERIAL_CHECK:1000,MOCK_GENERATION:delay,RESULT_PACKAGING:1000},failAtStage:fail||null}}:{})})});if(controller.signal.aborted)return;add(task);setNotice('任务已受理，可以继续创建其他作品。');void cache.invalidateQueries({queryKey:['history',user.id]});}
  catch(e){if(!controller.signal.aborted)setError(e instanceof ApiError?e.message:'提交结果暂时无法确认，请刷新历史检查；不要立即重复提交。');}
  finally{if(!controller.signal.aborted)setSubmitting(false);}
 }
 return <div className="workspace"><section className="maker-panel"><Link className="back-link" href="/">‹ 返回模板列表</Link><div className="maker-cover"><img src={template.coverUrl} alt={`${template.title}示例封面`}/><span>模板预览 / 模拟效果</span></div><h1>{template.title}</h1><p className="muted description">{template.description}</p><div className="section-heading"><h2>上传素材</h2><span className="muted">{slots.filter(s=>s.asset).length} / {template.requiredImageCount}</span></div><p className="upload-hint">需要 {template.requiredImageCount} 张不同图片 · JPEG / PNG · 每张最多 5MB</p>{loading?<p>正在确认登录状态…</p>:!user?<div className="login-prompt"><p>登录后即可上传并保存个人作品</p><Link className="button" href={`/login?next=/templates/${template.id}`}>登录 / 注册 ↗</Link></div>:<><div className={`upload-grid ${slots.length===1?'single':''}`}>{slots.map((slot,i)=><UploadSlot key={i} index={i} template={template} value={slot} disabled={submitting} onChange={next=>setSlots(old=>old.map((s,n)=>n===i?next:s))}/>)}</div><details className="simulation"><summary>开发演示 · 模拟设置</summary><label className="checkbox"><input type="checkbox" checked={simulate} onChange={e=>setSimulate(e.target.checked)}/>使用自定义模拟配置</label>{simulate&&<><label>生成耗时（毫秒）<input type="number" min={0} max={60000} value={delay} onChange={e=>setDelay(Number(e.target.value))}/></label><label>指定失败阶段<select value={fail} onChange={e=>setFail(e.target.value as Stage|'')}><option value="">不失败</option>{Object.entries(stageNames).map(([key,name])=><option key={key} value={key}>{name}</option>)}</select></label><p>素材检查、结果整理各 1 秒。服务端关闭模拟控制时使用默认设置。</p></>}</details><button className="button full create-button" disabled={!ready||submitting} onClick={()=>void submit()}>{submitting?'正在提交…':slots.some(s=>s.uploading)?'素材上传中…':'开始制作 ✧'}</button>{error&&<p role="alert" className="error">{error}</p>}{notice&&<p role="status" className="success">{notice}</p>}<p className="fine-print">任务受理后可离开页面，后台会继续处理。</p><a className="sample-link" href="/api/demo-files/sample.png" target="_blank" rel="noreferrer">打开本地示例图片 ↗</a></>}</section><section className="workspace-history"><div className="section-heading"><div><p className="eyebrow">YOUR CREATIONS</p><h2>制作记录</h2></div><button className="icon-button" aria-label="刷新制作记录" onClick={()=>{void history.refetch();retry();}}>↻</button></div>{queryError&&<div role="alert" className="banner">暂时无法更新任务，已保留最近状态。<button onClick={retry}>恢复查询</button></div>}{user&&history.isError&&<div role="alert" className="banner">历史加载失败 <button onClick={()=>void history.refetch()}>重试</button></div>}{!user?<div className="empty"><span className="empty-symbol">▧</span><h3>你的创意将在这里相遇</h3><p>登录并创建第一份作品。</p></div>:!records.length?<div className="empty"><span className="empty-symbol">✳</span><h3>{history.isPending?'正在加载记录…':'还没有作品，从一张照片开始'}</h3><p>上传素材，下一份创意就属于你。</p></div>:<div className="task-grid">{records.map(t=><TaskCard key={t.id} task={t}/>)}</div>}<Link className="all-history" href="/history">查看全部历史记录 ↗</Link></section></div>;
}
