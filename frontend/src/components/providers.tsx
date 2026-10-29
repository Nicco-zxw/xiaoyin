'use client';
import {createContext,useCallback,useContext,useEffect,useState,type ReactNode} from 'react';
import {QueryClient,QueryClientProvider,useQueryClient} from '@tanstack/react-query';
import {api,ApiError,resetCsrf,sessionGeneration} from '@/lib/api';
import type {User} from '@/lib/types';
import {TaskProvider} from './tasks';

type Auth={user:User|null;loading:boolean;error:string|null;reload:()=>Promise<void>;logout:()=>Promise<void>};
const AuthContext=createContext<Auth>(null!);
export const useAuth=()=>useContext(AuthContext);
function AuthProvider({children}:{children:ReactNode}){
 const cache=useQueryClient();const [user,setUser]=useState<User|null>(null);const [loading,setLoading]=useState(true);const [error,setError]=useState<string|null>(null);
 const reload=useCallback(async()=>{
  const started=sessionGeneration();setLoading(true);setError(null);
  try{const value=await api<User>('/auth/me');if(started===sessionGeneration())setUser(value);}catch(e){if(started===sessionGeneration()){setUser(null);if(!(e instanceof ApiError&&e.status===401))setError('暂时无法连接服务，请重试');}}finally{if(started===sessionGeneration())setLoading(false);}
 },[]);
 const clear=useCallback(async()=>{resetCsrf();const started=sessionGeneration();setUser(null);setLoading(false);await cache.cancelQueries();if(started===sessionGeneration())cache.clear();},[cache]);
 useEffect(()=>{
  const controller=new AbortController();const started=sessionGeneration();
  void api<User>('/auth/me',{signal:controller.signal}).then(value=>{if(!controller.signal.aborted&&started===sessionGeneration())setUser(value);}).catch(e=>{if(!controller.signal.aborted&&started===sessionGeneration()&&!(e instanceof ApiError&&e.status===401))setError('暂时无法连接服务，请重试');}).finally(()=>{if(!controller.signal.aborted&&started===sessionGeneration())setLoading(false);});
  const expire=()=>{void clear();};window.addEventListener('session-expired',expire);
  return()=>{controller.abort();window.removeEventListener('session-expired',expire);};
 },[clear]);
 const logout=async()=>{await api('/auth/logout',{method:'POST'});await clear();};
 return <AuthContext.Provider value={{user,loading,error,reload,logout}}><TaskProvider key={user?.id||'guest'} user={user}>{children}</TaskProvider></AuthContext.Provider>;
}
export function Providers({children}:{children:ReactNode}){
 const [client]=useState(()=>new QueryClient({defaultOptions:{queries:{retry:false,refetchOnWindowFocus:false}}}));
 return <QueryClientProvider client={client}><AuthProvider>{children}</AuthProvider></QueryClientProvider>;
}
