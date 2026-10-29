export class ApiError extends Error { constructor(public status:number,public code:string,message:string){super(message);} }
let csrfToken:string|undefined;
let generation=0;
export function sessionGeneration(){return generation;}
export function resetCsrf(){csrfToken=undefined;generation++;}
export async function csrf(){
 const started=generation;
 const res=await fetch('/api/auth/csrf',{credentials:'same-origin',cache:'no-store'});
 if(!res.ok)throw new Error('无法获取安全令牌');
 const data=await res.json();if(started!==generation)throw new DOMException('身份已切换','AbortError');csrfToken=data.token as string;return csrfToken;
}
export async function api<T>(path:string,options:RequestInit={}):Promise<T>{
 const started=generation;
 const method=options.method||'GET';const headers=new Headers(options.headers);
 if(method!=='GET'&&method!=='HEAD')headers.set('X-CSRF-TOKEN',csrfToken||await csrf());
 if(options.body&&!(options.body instanceof FormData))headers.set('Content-Type','application/json');
 const response=await fetch(`/api${path}`,{...options,headers,credentials:'same-origin',cache:'no-store'});
 if(started!==generation||options.signal?.aborted)throw new DOMException('请求已取消或身份已切换','AbortError');
 if(!response.ok){
  const data=await response.json().catch(()=>({code:'REQUEST_ERROR',message:'请求失败，请稍后重试'}));
  if(started!==generation||options.signal?.aborted)throw new DOMException('身份已切换','AbortError');
  if(response.status===401&&path!=='/auth/login'&&path!=='/auth/me')window.dispatchEvent(new Event('session-expired'));
  throw new ApiError(response.status,data.code,data.message);
 }
 if(response.status===204)return undefined as T;
 const data=await response.json() as T;
 if(started!==generation||options.signal?.aborted)throw new DOMException('身份已切换','AbortError');
 return data;
}
