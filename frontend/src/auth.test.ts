// @vitest-environment jsdom
import {afterEach,describe,expect,it,vi} from 'vitest';
import {
  apiFetch,
  clearAuthSession,
  isReadOnlySession,
  newIdempotencyKey,
  readAuthSession,
  saveAuthSession
} from './auth';

afterEach(()=>{
  clearAuthSession();
  vi.restoreAllMocks();
});

describe('auth session',()=>{
  it('recognizes the public demo as read only',()=>{
    saveAuthSession({
      username:'demo',
      role:'VIEWER',
      authorization:'Basic ZGVtbw=='
    });

    expect(readAuthSession()?.username).toBe('demo');
    expect(isReadOnlySession()).toBe(true);
  });

  it('keeps operator sessions writable',()=>{
    saveAuthSession({
      username:'operador',
      role:'OPERATOR',
      authorization:'Basic b3BlcmFkb3I='
    });

    expect(isReadOnlySession()).toBe(false);
  });

  it('adds the authorization header to API requests',async()=>{
    saveAuthSession({
      username:'demo',
      role:'VIEWER',
      authorization:'Basic ZGVtbw=='
    });

    const fetchMock=vi.spyOn(globalThis,'fetch').mockResolvedValue(
      new Response('{}',{status:200,headers:{'Content-Type':'application/json'}})
    );

    await apiFetch('https://example.test/api/v1/products');

    const init=fetchMock.mock.calls[0][1] as RequestInit;
    const headers=new Headers(init.headers);
    expect(headers.get('Authorization')).toBe('Basic ZGVtbw==');
  });
});

describe('idempotency key',()=>{
  it('creates a non-empty key per operation',()=>{
    const first=newIdempotencyKey();
    const second=newIdempotencyKey();
    expect(first.length).toBeGreaterThan(10);
    expect(second).not.toBe(first);
  });
});
