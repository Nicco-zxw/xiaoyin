import {afterEach,expect,it,vi} from 'vitest';
import {api,resetCsrf} from './api';
afterEach(()=>vi.unstubAllGlobals());
it('does not expire a new identity when an old request returns 401',async()=>{
 let resolve!:(value:Response)=>void;
 vi.stubGlobal('fetch',vi.fn(()=>new Promise<Response>(r=>{resolve=r;})));
 const expired=vi.fn();window.addEventListener('session-expired',expired);
 try{
  const request=api('/tasks');resetCsrf(); // Login/logout advances the identity boundary.
  resolve(new Response(JSON.stringify({code:'UNAUTHENTICATED',message:'old session'}),{status:401}));
  await expect(request).rejects.toThrow();expect(expired).not.toHaveBeenCalled();
 }finally{window.removeEventListener('session-expired',expired);}
});
