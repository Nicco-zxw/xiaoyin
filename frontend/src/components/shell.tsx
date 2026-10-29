'use client';
import Link from 'next/link';
import {useEffect,useRef,useState,type ReactNode} from 'react';
import {useAuth} from './providers';
import {api,resetCsrf} from '@/lib/api';
import {useTasks} from './tasks';

export function Shell({children}:{children:ReactNode}){
 const {user,loading,error,reload,logout}=useAuth();const [account,setAccount]=useState(false);const {tasks}=useTasks();
 const active=Object.values(tasks).filter(t=>t.status==='RUNNING'||t.status==='QUEUED').length;
 return <><header className="site-header"><Link className="brand" href="/"><span className="brand-symbol">m</span> 素作 <small>MATERIAL STUDIO</small></Link><nav aria-label="主导航"><Link href="/">发现模板</Link><Link href="/history">我的作品 {active>0&&<span className="count">{active}</span>}</Link></nav><div className="header-account">{loading?<span className="muted">连接中…</span>:user?<button className="avatar-button" onClick={()=>setAccount(true)}><span className="avatar">{user.username[0].toUpperCase()}</span>{user.username}</button>:<Link className="button small" href="/login">登录 / 注册 ↗</Link>}</div></header>{error&&<div role="alert" className="banner">{error} <button onClick={()=>void reload()}>重试</button></div>}<main>{children}</main><footer>素作 MATERIAL STUDIO <span>把一点灵感，变成喜欢的模样。</span><small>本网站使用模拟生成器，不调用真实 AI。</small></footer>{account&&user&&<Account onClose={()=>setAccount(false)} logout={async()=>{await logout();setAccount(false);}}/>}</>;
}
function Account({onClose,logout}:{onClose:()=>void;logout:()=>Promise<void>}){
 const {user}=useAuth();const ref=useRef<HTMLDialogElement>(null);const [changing,setChanging]=useState(false);const [busy,setBusy]=useState(false);const [error,setError]=useState('');
 useEffect(()=>{const dialog=ref.current;dialog?.showModal();return()=>dialog?.close();},[]);
 async function password(event:React.FormEvent<HTMLFormElement>){event.preventDefault();setBusy(true);setError('');const data=new FormData(event.currentTarget);
  try{await api('/auth/password',{method:'PUT',body:JSON.stringify(Object.fromEntries(data))});resetCsrf();window.dispatchEvent(new Event('session-expired'));onClose();}catch(e){setError((e as Error).message);}finally{setBusy(false);}
 }
 return <dialog ref={ref} className="account-dialog" onCancel={onClose}><div className="section-heading"><h2>账户设置</h2><button className="icon-button" aria-label="关闭账户设置" onClick={onClose}>×</button></div><div className="account-card"><div className="account-identity"><span className="avatar large">{user?.username[0].toUpperCase()}</span><div><h3>{user?.username}</h3><p className="muted">注册于 {new Date(user!.createdAt).toLocaleDateString('zh-CN')}</p></div></div><hr/><div className="section-heading"><div><p className="muted">密码</p><p>••••••••••••</p></div><button className="text-button" onClick={()=>setChanging(!changing)}>修改密码</button></div>{changing&&<form onSubmit={password} className="form-stack"><label>当前密码<input required type="password" name="currentPassword" autoComplete="current-password"/></label><label>新密码<input required minLength={8} maxLength={64} type="password" name="newPassword" autoComplete="new-password"/></label><label>确认新密码<input required type="password" name="confirmPassword" autoComplete="new-password"/></label><p className="muted">修改后需要重新登录，所有现有会话将失效。</p><button className="button" disabled={busy}>{busy?'保存中…':'保存并重新登录'}</button></form>}</div>{error&&<p role="alert" className="error">{error}</p>}<button className="button secondary full" disabled={busy} onClick={async()=>{setBusy(true);try{await logout();}catch(e){setError((e as Error).message);setBusy(false);}}}>退出登录</button></dialog>;
}
