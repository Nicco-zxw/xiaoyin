'use client';
import {useState} from 'react';
import {useRouter} from 'next/navigation';
import {useAuth} from '@/components/providers';
import {api,resetCsrf} from '@/lib/api';
export default function Login(){
 const [register,setRegister]=useState(false);const [busy,setBusy]=useState(false);const [error,setError]=useState('');const [notice,setNotice]=useState('');const {reload}=useAuth();const router=useRouter();
 async function submit(e:React.FormEvent<HTMLFormElement>){e.preventDefault();setBusy(true);setError('');setNotice('');const data=new FormData(e.currentTarget);
  try{await api(register?'/auth/register':'/auth/login',{method:'POST',body:JSON.stringify(Object.fromEntries(data))});
   if(register){setRegister(false);setNotice('注册成功，请使用新账户登录。');}else{resetCsrf();await reload();const next=new URLSearchParams(window.location.search).get('next');router.push(next?.startsWith('/')&&!next.startsWith('//')?next:'/');}
  }catch(e){setError((e as Error).message);}finally{setBusy(false);}
 }
 return <div className="auth-page"><section className="auth-intro"><p className="eyebrow">YOUR IDEAS LIVE HERE</p><h1>欢迎来到<br/><span>你的创意空间。</span></h1><p>把照片里的故事，变成值得收藏的作品。</p><img src="/api/demo-files/portrait.svg" alt="午后光影模板示例"/></section><section className="auth-panel"><p className="eyebrow">MATERIAL STUDIO</p><h2>{register?'创建你的账户':'好久不见，欢迎回来'}</h2><p className="muted">{register?'注册后即可制作并保存个人作品。':'登录后继续你的创作。'}</p><form className="form-stack" onSubmit={submit}><label>用户名<input name="username" required pattern="[a-zA-Z0-9_]{3,32}" minLength={3} maxLength={32} autoComplete="username" placeholder="3–32 位字母、数字或下划线"/></label><label>密码<input name="password" required minLength={8} maxLength={64} type="password" autoComplete={register?'new-password':'current-password'} placeholder="至少 8 位"/></label>{register&&<label>确认密码<input name="confirmPassword" required type="password" autoComplete="new-password"/></label>}{error&&<p className="error" role="alert">{error}</p>}{notice&&<p className="success" role="status">{notice}</p>}<button className="button full" disabled={busy}>{busy?'请稍候…':register?'创建账户 ↗':'登录 ↗'}</button></form><p className="auth-switch">{register?'已经有账户？':'还没有账户？'} <button className="text-button" disabled={busy} onClick={()=>{setRegister(!register);setError('');setNotice('');}}>{register?'去登录':'免费注册'}</button></p><p className="fine-print">演示环境：请勿使用你在其他网站的真实密码。</p></section></div>;
}
