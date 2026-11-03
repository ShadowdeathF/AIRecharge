const {test}=require('node:test'),assert=require('node:assert/strict'),fs=require('node:fs'),path=require('node:path'),vm=require('node:vm'),ts=require('typescript');

function storage(seed={}){const data=new Map(Object.entries(seed));return{getItem:key=>data.get(key)??null,setItem:(key,value)=>data.set(key,String(value)),removeItem:key=>data.delete(key)}}
function tab(shared=storage(),own=storage()){
  const calls=[],cache=new Map(),window={localStorage:shared,sessionStorage:own,location:{pathname:'/cart',href:''}};
  const sandbox=vm.createContext({window,localStorage:shared,sessionStorage:own,URLSearchParams,process:{env:{}},console,fetch:async(url,options)=>{calls.push({url,options});return{ok:true,status:200,headers:{get:()=>null},json:async()=>({code:0,data:{}}),blob:async()=>({})}}});
  function load(name){
    if(name==='@/lib/fingerprint')return{getDeviceId:async()=>''};
    const file=path.join(__dirname,'..',name.replace(/^@\//,'')+'.ts');if(cache.has(file))return cache.get(file).exports;
    const module={exports:{}};cache.set(file,module);const code=ts.transpileModule(fs.readFileSync(file,'utf8'),{compilerOptions:{module:ts.ModuleKind.CommonJS,target:ts.ScriptTarget.ES2020}}).outputText;
    const localRequire=ref=>load(ref.startsWith('.')?'@/'+path.relative(path.join(__dirname,'..'),path.resolve(path.dirname(file),ref)).replaceAll('\\','/'):ref);
    vm.runInContext('(function(require,module,exports){'+code+'\n})',sandbox)(localRequire,module,module.exports);return module.exports;
  }
  return{api:load('@/services/api'),finance:()=>load('@/services/finance-api'),session:()=>load('@/lib/tab-session'),calls,window,shared,own,setFetch:fn=>{sandbox.fetch=fn}};
}
const response401=()=>({ok:false,status:401,statusText:'Unauthorized',headers:{get:()=>null},json:async()=>({code:401,message:'expired'})});

test('two tabs on the same origin keep separate request identities',async()=>{
  const shared=storage(),a=tab(shared),b=tab(shared);a.api.setToken('test-tab-A');b.api.setToken('test-tab-B');
  await a.api.request('/users/me');await b.api.request('/users/me');
  assert.equal(a.calls[0].options.headers.Authorization,'Bearer test-tab-A');assert.equal(b.calls[0].options.headers.Authorization,'Bearer test-tab-B');
});
test('logout and expired authentication in one tab leave the other tab signed in',async()=>{
  const shared=storage(),a=tab(shared),b=tab(shared);a.api.setToken('test-tab-A');b.api.setToken('test-tab-B');b.api.clearToken();await a.api.request('/users/me');assert.equal(a.calls[0].options.headers.Authorization,'Bearer test-tab-A');
  b.api.setToken('test-tab-B');b.setFetch(async()=>response401());await assert.rejects(b.api.request('/users/me'));await a.api.request('/users/me');assert.equal(a.calls[1].options.headers.Authorization,'Bearer test-tab-A');
});
test('reload retains its own session while a new tab cannot reuse the old shared login',async()=>{
  const shared=storage({auth_token:'legacy-shared',userProfile:'{"id":"legacy"}',theme:'dark',locale:'zh'}),own=storage();
  const a=tab(shared,own);a.api.setToken('test-tab-A');const reload=tab(shared,own);await reload.api.request('/users/me');assert.equal(reload.calls[0].options.headers.Authorization,'Bearer test-tab-A');
  const fresh=tab(shared);await fresh.api.request('/users/me');assert.equal(fresh.calls[0].options.headers.Authorization,undefined);assert.equal(shared.getItem('theme'),'dark');assert.equal(shared.getItem('locale'),'zh');assert.equal(shared.getItem('auth_token'),null);
});
test('a delayed 401 from the previous account cannot clear the new account',async()=>{
  const a=tab();let release,started;const reached=new Promise(resolve=>{started=resolve});
  a.api.setToken('test-old');a.setFetch(()=>{started();return new Promise(resolve=>{release=resolve})});const pending=a.api.request('/users/me');await reached;
  a.api.setToken('test-new');release(response401());await assert.rejects(pending);a.setFetch(async(url,options)=>{a.calls.push({url,options});return{ok:true,headers:{get:()=>null},json:async()=>({code:0,data:{}})}});await a.api.request('/users/me');
  assert.equal(a.calls[0].options.headers.Authorization,'Bearer test-new');assert.equal(a.window.location.href,'');
});
test('finance and workbench downloads use the current tab authentication',async()=>{
  const shared=storage(),a=tab(shared),b=tab(shared);a.api.setToken('test-tab-A');b.api.setToken('test-tab-B');
  await a.api.adminWorkbenchApi.exportCsv({start_date:'2026-11-01',end_date:'2026-11-03'});await a.finance().financeApi.export({start_date:'2026-11-01',end_date:'2026-11-03'});await a.finance().financeApi.exportDocument('test-document');
  assert.equal(a.calls.length,3);for(const call of a.calls)assert.equal(call.options.headers.Authorization,'Bearer test-tab-A');
});
test('profiles and guest cart sessions persist in their own tab without affecting another tab',()=>{
  const shared=storage(),own=storage(),a=tab(shared,own),b=tab(shared);a.api.setToken('test-tab-A');b.api.setToken('test-tab-B');
  a.session().setStoredUser({id:'user-A'});b.session().setStoredUser({id:'user-B'});a.session().setGuestToken('guest-A');b.session().setGuestToken('guest-B');
  const reload=tab(shared,own);assert.equal(reload.session().getStoredUser().id,'user-A');assert.equal(reload.session().getGuestToken(),'guest-A');
  b.api.clearSessionToken();b.api.clearToken();assert.equal(a.session().getStoredUser().id,'user-A');assert.equal(a.session().getGuestToken(),'guest-A');assert.equal(b.session().getStoredUser(),null);
});
test('a first guest add returning during login keeps its token for the authenticated cart merge',async()=>{
  const a=tab();let release,started;const reached=new Promise(resolve=>{started=resolve});a.setFetch(()=>{started();return new Promise(resolve=>{release=resolve})});
  const pending=a.api.cartApi.addItem({product_id:'test-product',spec_id:null,quantity:1});await reached;a.api.setToken('test-first-login');
  release({ok:true,status:200,headers:{get:()=> 'test-pending-guest'},json:async()=>({code:0,data:null})});await pending;
  assert.equal(a.session().getGuestToken(),'test-pending-guest');
});
test('a guest response from before logout cannot install its token into the next login',async()=>{
  const a=tab();let release,started;const reached=new Promise(resolve=>{started=resolve});a.setFetch(()=>{started();return new Promise(resolve=>{release=resolve})});
  const pending=a.api.cartApi.addItem({product_id:'test-product',spec_id:null,quantity:1});await reached;a.api.clearToken();a.api.clearSessionToken();a.api.setToken('test-next-account');
  release({ok:true,status:200,headers:{get:()=> 'test-obsolete-guest'},json:async()=>({code:0,data:null})});await pending;assert.equal(a.session().getGuestToken(),null);
});
