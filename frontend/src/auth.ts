export type AuthRole='ADMIN'|'OPERATOR'|'VIEWER';
export type AuthSession={username:string;role:AuthRole;token:string;expiresAt:string};
const AUTH_KEY='nexo-auth-v2';

export function clearAuthSession(){
  sessionStorage.removeItem(AUTH_KEY);
  sessionStorage.removeItem('nexo-auth');
}

export function readAuthSession():AuthSession|null{
  // Old sessions contained reversible credentials and must never be migrated.
  sessionStorage.removeItem('nexo-auth');
  try{
    const raw=sessionStorage.getItem(AUTH_KEY);
    if(!raw) return null;
    const parsed=JSON.parse(raw);
    if(typeof parsed?.username!=='string'||!parsed.username
      ||typeof parsed?.token!=='string'||!/^[A-Za-z0-9_-]{43}$/.test(parsed.token)
      ||typeof parsed?.expiresAt!=='string'
      ||!['ADMIN','OPERATOR','VIEWER'].includes(parsed.role)
      ||!Number.isFinite(Date.parse(parsed.expiresAt))||Date.parse(parsed.expiresAt)<=Date.now()){
      clearAuthSession();
      return null;
    }
    return {username:parsed.username,role:parsed.role,token:parsed.token,expiresAt:parsed.expiresAt};
  }catch{
    clearAuthSession();
    return null;
  }
}

export function saveAuthSession(session:AuthSession){
  clearAuthSession();
  sessionStorage.setItem(AUTH_KEY,JSON.stringify({
    username:session.username,role:session.role,token:session.token,expiresAt:session.expiresAt
  }));
}

export function isReadOnlySession(){return readAuthSession()?.role==='VIEWER';}

export async function apiFetch(input:RequestInfo|URL,init:RequestInit={}){
  const session=readAuthSession();
  const headers=new Headers(init.headers||{});
  if(session) headers.set('Authorization','Bearer '+session.token);
  const response=await fetch(input,{...init,headers,credentials:'omit'});
  if(response.status===401){
    clearAuthSession();
    window.dispatchEvent(new Event('nexo:session-expired'));
  }
  return response;
}

export async function logoutSession(apiUrl:string){
  const session=readAuthSession();
  clearAuthSession();
  if(session){
    await fetch(apiUrl+'/api/v1/auth/logout',{
      method:'POST',headers:{Authorization:'Bearer '+session.token},credentials:'omit',
      signal:AbortSignal.timeout(5000)
    });
  }
}

export function newIdempotencyKey(){
  if(typeof crypto!=='undefined' && 'randomUUID' in crypto) return crypto.randomUUID();
  return 'nexo-'+Date.now()+'-'+Math.random().toString(36).slice(2);
}
