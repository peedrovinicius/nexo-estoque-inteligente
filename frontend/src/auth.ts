export type AuthRole='ADMIN'|'OPERATOR'|'VIEWER';
export type AuthSession={username:string;role:AuthRole;authorization:string};

const AUTH_KEY='nexo-auth';

export function readAuthSession():AuthSession|null{
  try{
    const raw=sessionStorage.getItem(AUTH_KEY);
    if(!raw) return null;
    const parsed=JSON.parse(raw);
    if(!parsed?.username||!parsed?.role||!parsed?.authorization) return null;
    if(!['ADMIN','OPERATOR','VIEWER'].includes(parsed.role)) return null;
    return parsed as AuthSession;
  }catch{
    return null;
  }
}

export function saveAuthSession(session:AuthSession){
  sessionStorage.setItem(AUTH_KEY,JSON.stringify(session));
}

export function clearAuthSession(){
  sessionStorage.removeItem(AUTH_KEY);
}

export function isReadOnlySession(){
  return readAuthSession()?.role==='VIEWER';
}

export async function apiFetch(input:RequestInfo|URL,init:RequestInit={}){
  const session=readAuthSession();
  const headers=new Headers(init.headers||{});
  if(session?.authorization) headers.set('Authorization',session.authorization);

  const response=await fetch(input,{...init,headers});
  if(response.status===401){
    clearAuthSession();
    if(typeof window!=='undefined' && window.location) window.location.reload();
  }
  return response;
}

export function newIdempotencyKey(){
  if(typeof crypto!=='undefined' && 'randomUUID' in crypto) return crypto.randomUUID();
  return 'nexo-'+Date.now()+'-'+Math.random().toString(36).slice(2);
}
