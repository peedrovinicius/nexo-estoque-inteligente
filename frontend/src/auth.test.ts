// @vitest-environment jsdom
import {afterEach,describe,expect,it,vi} from 'vitest';
import {apiFetch,clearAuthSession,isReadOnlySession,logoutSession,newIdempotencyKey,readAuthSession,saveAuthSession} from './auth';

const token='a'.repeat(43);
const expiresAt=()=>new Date(Date.now()+1800000).toISOString();
afterEach(()=>{clearAuthSession();vi.restoreAllMocks();});

describe('auth session',()=>{
  it('keeps the demo read only and the operator writable',()=>{
    saveAuthSession({username:'demo',role:'VIEWER',token,expiresAt:expiresAt()});
    expect(isReadOnlySession()).toBe(true);
    saveAuthSession({username:'operador',role:'OPERATOR',token,expiresAt:expiresAt()});
    expect(isReadOnlySession()).toBe(false);
  });

  it('discards legacy credential storage instead of restoring it',()=>{
    sessionStorage.setItem('nexo-auth',JSON.stringify({username:'admin',role:'ADMIN',authorization:'Basic secret'}));
    expect(readAuthSession()).toBeNull();
    expect(sessionStorage.getItem('nexo-auth')).toBeNull();
  });

  it('does not persist additional credentials and expires the session locally',()=>{
    const session={username:'admin',role:'ADMIN' as const,token,expiresAt:expiresAt(),password:'secret',authorization:'Basic secret'};
    saveAuthSession(session);
    expect(sessionStorage.getItem('nexo-auth-v2')).not.toContain('secret');
    vi.spyOn(Date,'now').mockReturnValue(Date.now()+1800001);
    expect(readAuthSession()).toBeNull();
    expect(sessionStorage.getItem('nexo-auth-v2')).toBeNull();
  });

  it('rejects malformed session tokens',()=>{
    sessionStorage.setItem('nexo-auth-v2',JSON.stringify({username:'demo',role:'VIEWER',token:'Basic secret',expiresAt:expiresAt()}));
    expect(readAuthSession()).toBeNull();
  });

  it('sends the bearer token and invalidates a rejected session',async()=>{
    saveAuthSession({username:'demo',role:'VIEWER',token,expiresAt:expiresAt()});
    const expire=vi.fn();
    window.addEventListener('nexo:session-expired',expire);
    const fetchMock=vi.spyOn(globalThis,'fetch').mockResolvedValue(new Response('',{status:401}));
    await apiFetch('https://example.test/api/v1/products');
    const init=fetchMock.mock.calls[0][1] as RequestInit;
    expect(new Headers(init.headers).get('Authorization')).toBe('Bearer '+token);
    expect(init.credentials).toBe('omit');
    expect(readAuthSession()).toBeNull();
    expect(expire).toHaveBeenCalledTimes(1);
    window.removeEventListener('nexo:session-expired',expire);
  });

  it('revokes the server token on logout and clears browser state',async()=>{
    saveAuthSession({username:'demo',role:'VIEWER',token,expiresAt:expiresAt()});
    const fetchMock=vi.spyOn(globalThis,'fetch').mockResolvedValue(new Response(null,{status:204}));
    await logoutSession('https://example.test');
    expect(fetchMock.mock.calls[0][0]).toBe('https://example.test/api/v1/auth/logout');
    expect(new Headers(fetchMock.mock.calls[0][1]?.headers).get('Authorization')).toBe('Bearer '+token);
    expect(readAuthSession()).toBeNull();
  });
});

describe('idempotency key',()=>{
  it('creates a non-empty key per operation',()=>{
    const first=newIdempotencyKey();
    expect(first.length).toBeGreaterThan(10);
    expect(newIdempotencyKey()).not.toBe(first);
  });
});
