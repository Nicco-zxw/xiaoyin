import assert from 'node:assert/strict';
const base=process.env.BASE_URL||'http://frontend:3000';
function client(){
 let cookie='',token='';
 async function request(path,options={},expected=200){
  const method=options.method||'GET';const headers=new Headers(options.headers);
  if(cookie)headers.set('Cookie',cookie);
  if(!['GET','HEAD'].includes(method))headers.set('X-CSRF-TOKEN',token);
  if(options.body&&!(options.body instanceof FormData))headers.set('Content-Type','application/json');
  const response=await fetch(base+'/api'+path,{...options,headers});
  for(const value of response.headers.getSetCookie())if(value.startsWith('JSESSIONID='))cookie=value.split(';')[0];
  assert.equal(response.status,expected,`${method} ${path}: ${response.status} ${await response.clone().text()}`);
  if(response.status===204)return null;
  return response.headers.get('Content-Type')?.includes('application/json')?response.json():response;
 }
 async function csrf(){token=(await request('/auth/csrf')).token;}
 async function login(username,password){await csrf();await request('/auth/login',{method:'POST',body:JSON.stringify({username,password})});await csrf();}
 async function register(){const username='smoke_'+crypto.randomUUID().replaceAll('-','').slice(0,12),password='smoke-password';await csrf();await request('/auth/register',{method:'POST',body:JSON.stringify({username,password,confirmPassword:password})},201);await login(username,password);return {username,password};}
 return {request,csrf,login,register};
}
for(let i=0;i<60;i++){
 try{const response=await fetch(base+'/api/templates');if(response.ok)break;}catch{}
 if(i===59)throw new Error('Website did not become ready');
 await new Promise(r=>setTimeout(r,1000));
}
const a=client();const account=await a.register();
for(const path of ['/tasks?page=abc','/tasks?size=2147483648'])assert.equal((await a.request(path,{},400)).code,'VALIDATION_ERROR');
assert.equal((await a.request('/assets',{method:'POST',body:new FormData()},400)).code,'VALIDATION_ERROR');
assert.equal((await a.request('/templates')).items.length,6);
const image=await (await a.request('/demo-files/sample.png')).blob();
const data=new FormData();data.set('templateId','pet');data.set('file',image,'sample.png');
const asset=await a.request('/assets',{method:'POST',body:data},201);
const create=failAtStage=>a.request('/tasks',{method:'POST',body:JSON.stringify({templateId:'pet',assetIds:[asset.id],simulation:{stageDelayMs:{MATERIAL_CHECK:100,MOCK_GENERATION:3000,RESULT_PACKAGING:100},failAtStage}})},202);
const start=performance.now();const success=await create(null);const failure=await create('MOCK_GENERATION');
assert.ok(performance.now()-start<2500,'submission waited for generation');
assert.ok(['QUEUED','RUNNING'].includes(success.status));
// Deliberately do not query while Java continues working.
await new Promise(r=>setTimeout(r,3500));
async function completed(id){for(let i=0;i<100;i++){const task=await a.request('/tasks/'+id);if(['SUCCEEDED','FAILED'].includes(task.status))return task;await new Promise(r=>setTimeout(r,100));}throw Error('Task timeout');}
const done=await completed(success.id),failed=await completed(failure.id);
assert.equal(done.status,'SUCCEEDED');assert.ok(done.steps.every(s=>s.status==='SUCCEEDED'));
assert.equal(failed.status,'FAILED');assert.equal(failed.failure.stage,'MOCK_GENERATION');assert.equal(failed.steps[2].status,'SKIPPED');
const result=await a.request('/tasks/'+done.id+'/result');assert.match(await result.text(),/<svg/);
const b=client();await b.register();
await b.request('/tasks/'+done.id,{},404);await b.request('/assets/'+asset.id+'/content',{},404);await b.request('/tasks/'+done.id+'/result',{},404);
const restored=client();await restored.login(account.username,account.password);
assert.equal((await restored.request('/tasks')).totalElements,2);
assert.equal((await restored.request('/tasks/active')).items.length,0);
await restored.request('/auth/logout',{method:'POST'},204);await restored.request('/auth/me',{},401);
console.log('HTTP smoke passed: proxy + CSRF + registration/login + upload + async success/failure + isolation + restored history + logout');
