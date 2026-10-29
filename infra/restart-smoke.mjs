import assert from 'node:assert/strict';
import {readFile,writeFile} from 'node:fs/promises';

const base=process.env.BASE_URL||'http://frontend:3000';
const sleep=ms=>new Promise(resolve=>setTimeout(resolve,ms));
function client(initial={}){
 let cookie=initial.cookie||'',token=initial.token||'';
 async function request(path,options={},expected=200){
  const headers=new Headers(options.headers);if(cookie)headers.set('Cookie',cookie);
  if(!['GET','HEAD'].includes(options.method||'GET'))headers.set('X-CSRF-TOKEN',token);
  if(options.body&&!(options.body instanceof FormData))headers.set('Content-Type','application/json');
  const response=await fetch(base+'/api'+path,{...options,headers});
  for(const value of response.headers.getSetCookie())if(value.startsWith('JSESSIONID='))cookie=value.split(';')[0];
  assert.equal(response.status,expected,`${options.method||'GET'} ${path}: ${await response.clone().text()}`);
  if(response.status===204)return null;
  return response.headers.get('Content-Type')?.includes('application/json')?response.json():response;
 }
 async function csrf(){token=(await request('/auth/csrf')).token;}
 async function login(username,password){await csrf();await request('/auth/login',{method:'POST',body:JSON.stringify({username,password})});await csrf();}
 return {request,csrf,login,state:()=>({cookie,token})};
}
async function ready(){
 for(let i=0;i<90;i++){
  try{if((await fetch(base+'/api/templates')).ok)return;}catch{}
  await sleep(1000);
 }
 throw new Error('isolated restart website did not become ready');
}
async function waitFor(api,id,predicate){
 for(let i=0;i<100;i++){const task=await api.request('/tasks/'+id);if(predicate(task))return task;await sleep(50);}
 throw new Error(`task did not reach checkpoint: ${id}`);
}

await ready();
if(process.argv[2]==='before'){
 const api=client();const username='restart_'+crypto.randomUUID().replaceAll('-','').slice(0,12),password='restart-test-password';
 await api.csrf();await api.request('/auth/register',{method:'POST',body:JSON.stringify({username,password,confirmPassword:password})},201);await api.login(username,password);
 const data=new FormData();data.set('templateId','pet');data.set('file',await (await api.request('/demo-files/sample.png')).blob(),'sample.png');
 const asset=await api.request('/assets',{method:'POST',body:data},201);
 const create=delay=>api.request('/tasks',{method:'POST',body:JSON.stringify({templateId:'pet',assetIds:[asset.id],simulation:{stageDelayMs:{MATERIAL_CHECK:delay,MOCK_GENERATION:0,RESULT_PACKAGING:0},failAtStage:null}})},202);
 const completed=await waitFor(api,(await create(0)).id,t=>t.status==='SUCCEEDED');
 const image=await (await api.request('/tasks/'+completed.id+'/result')).text();
 const running=await waitFor(api,(await create(60000)).id,t=>t.status==='RUNNING'&&t.currentStage==='MATERIAL_CHECK');
 const queued=await create(60000);assert.equal(queued.status,'QUEUED');
 await writeFile('/state/checkpoint.json',JSON.stringify({username,password,session:api.state(),completed,running,queued,image}));
 console.log('Restart checkpoint prepared: one completed, one running, one queued; ready for SIGKILL');
}else if(process.argv[2]==='after'){
 const saved=JSON.parse(await readFile('/state/checkpoint.json','utf8'));
 const oldSession=client(saved.session);await oldSession.request('/auth/me',{},401);
 const api=client();await api.login(saved.username,saved.password);
 for(const path of ['/tasks?page=abc','/tasks?size=2147483648'])assert.equal((await api.request(path,{},400)).code,'VALIDATION_ERROR');
 assert.equal((await api.request('/assets',{method:'POST',body:new FormData()},400)).code,'VALIDATION_ERROR');
 const completed=await api.request('/tasks/'+saved.completed.id);
 assert.equal(completed.status,'SUCCEEDED');assert.equal(completed.finishedAt,saved.completed.finishedAt);
 assert.equal(await (await api.request('/tasks/'+completed.id+'/result')).text(),saved.image);
 for(const kind of ['running','queued']){
  const before=saved[kind],task=await api.request('/tasks/'+before.id);
  assert.equal(task.status,'FAILED');assert.equal(task.failure.code,'SERVICE_RESTARTED');
  assert.match(task.failure.message,/重启/);assert.equal(task.createdAt,before.createdAt);
  assert.ok(task.finishedAt);assert.equal(task.resultUrl,null);
  assert.equal(task.currentStage,kind==='running'?'MATERIAL_CHECK':null);
  assert.deepEqual(task.steps.map(s=>s.status),kind==='running'?['FAILED','SKIPPED','SKIPPED']:['SKIPPED','SKIPPED','SKIPPED']);
  await api.request('/tasks/'+task.id+'/result',{},409);
 }
 const history=await api.request('/tasks');assert.equal(history.totalElements,3);
 assert.deepEqual((await api.request('/tasks/active')).items,[]);
 // A later query must not turn interrupted tasks back into RUNNING.
 await sleep(200);
 assert.equal((await api.request('/tasks/'+saved.running.id)).status,'FAILED');
 console.log('Restart smoke passed: SIGKILL interruption persisted, queued stages skipped, session expired, history and completed image survived');
}else{
 throw new Error('Usage: node restart-smoke.mjs before|after');
}
