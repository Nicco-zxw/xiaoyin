'use client';
import {useEffect,useRef,useState} from 'react';
import {api} from '@/lib/api';
import type {Asset,Template} from '@/lib/types';
export type Slot={asset:Asset|null;uploading:boolean;error:string|null};
export function UploadSlot({index,template,value,onChange,disabled}:{index:number;template:Template;value:Slot;onChange:(slot:Slot)=>void;disabled:boolean}){
 const input=useRef<HTMLInputElement>(null);const request=useRef<AbortController|null>(null);const [local,setLocal]=useState<string|null>(null);
 useEffect(()=>()=>request.current?.abort(),[]);
 useEffect(()=>()=>{if(local)URL.revokeObjectURL(local);},[local]);
 async function select(file:File|undefined){
  if(!file)return;
  if(!template.acceptedMimeTypes.includes(file.type)){onChange({...value,error:'请选择 JPEG 或 PNG 图片'});return;}
  if(file.size>template.maxFileSizeBytes){onChange({...value,error:'图片不能超过 5MB'});return;}
  request.current?.abort();const controller=new AbortController();request.current=controller;
  setLocal(URL.createObjectURL(file));onChange({asset:null,uploading:true,error:null});
  const data=new FormData();data.set('templateId',template.id);data.set('file',file);
  try{const asset=await api<Asset>('/assets',{method:'POST',body:data,signal:controller.signal});if(!controller.signal.aborted)onChange({asset,uploading:false,error:null});}
  catch(e){if(!controller.signal.aborted)onChange({asset:null,uploading:false,error:(e as Error).message});}
 }
 const url=local||value.asset?.previewUrl;
 return <div className="upload-slot"><input ref={input} hidden type="file" accept={template.acceptedMimeTypes.join(',')} aria-label={`选择第 ${index+1} 张素材`} disabled={disabled||value.uploading} onChange={e=>{void select(e.target.files?.[0]);e.target.value='';}}/><button type="button" className={`upload-zone ${url?'has-image':''}`} disabled={disabled||value.uploading} onClick={()=>input.current?.click()} aria-label={`${url?'替换':'上传'}第 ${index+1} 张素材`}>{url?<img src={url} alt={`素材 ${index+1} 预览`}/>:<><span className="upload-icon">＋</span><strong>上传图片 {index+1}</strong><small>点击选择 JPEG / PNG</small></>}{value.uploading&&<span className="upload-overlay"><span className="spinner"/>上传中…</span>}</button>{url&&!value.uploading&&<div className="upload-actions"><button disabled={disabled} onClick={()=>input.current?.click()}>替换</button><button disabled={disabled} onClick={()=>{setLocal(null);onChange({asset:null,uploading:false,error:null});}}>移除</button></div>}{value.error&&<p role="alert" className="error">{value.error} <button onClick={()=>input.current?.click()}>重新选择</button></p>}</div>;
}
