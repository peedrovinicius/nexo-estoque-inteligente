// @vitest-environment jsdom
import React,{act} from 'react';
import {afterEach,beforeEach,describe,expect,it,vi} from 'vitest';
import {createRoot,Root} from 'react-dom/client';
import App,{Login} from './App';
import {clearAuthSession,readAuthSession} from './auth';

(globalThis as typeof globalThis & {IS_REACT_ACT_ENVIRONMENT?:boolean}).IS_REACT_ACT_ENVIRONMENT=true;

let root:Root;

function mount(node:React.ReactNode){
  const host=document.createElement('div');
  document.body.innerHTML='';
  document.body.appendChild(host);
  root=createRoot(host);
  act(()=>root.render(node));
  return host;
}

function buttonByText(text:string){
  return Array.from(document.querySelectorAll('button')).find(button=>button.textContent?.includes(text)) as HTMLButtonElement|undefined;
}

beforeEach(()=>{
  clearAuthSession();
  localStorage.clear();
  window.history.replaceState(null,'','/');
  Object.defineProperty(window,'scrollTo',{value:vi.fn(),writable:true});
});

afterEach(()=>{
  act(()=>root?.unmount());
  vi.restoreAllMocks();
  clearAuthSession();
});

describe('public navigation',()=>{
  it('opens login and returns home',()=>{
    mount(<App/>);

    expect(document.querySelector('.home3')).not.toBeNull();

    act(()=>buttonByText('Entrar')?.click());
    expect(document.querySelector('.login3')).not.toBeNull();
    expect(window.location.hash).toBe('#login');

    act(()=>buttonByText('Voltar')?.click());
    expect(document.querySelector('.home3')).not.toBeNull();
    expect(window.location.hash).toBe('');
  });

  it('follows browser history between home and login',()=>{
    mount(<App/>);
    act(()=>buttonByText('Entrar')?.click());
    expect(document.querySelector('.login3')).not.toBeNull();

    act(()=>{
      window.history.replaceState(null,'','/');
      window.dispatchEvent(new PopStateEvent('popstate'));
    });
    expect(document.querySelector('.home3')).not.toBeNull();
  });
});

describe('login flow',()=>{
  it('fills the demo account and authenticates',async()=>{
    const fetchMock=vi.spyOn(globalThis,'fetch').mockResolvedValue(
      new Response(JSON.stringify({username:'demo',role:'VIEWER'}),{
        status:200,
        headers:{'Content-Type':'application/json'}
      })
    );
    const onLogin=vi.fn();

    mount(<Login onLogin={onLogin} onBack={()=>{}} theme="light" onToggleTheme={()=>{}}/>);

    act(()=>buttonByText('Usar acesso demo')?.click());

    const username=document.querySelector('input[name="username"]') as HTMLInputElement;
    const password=document.querySelector('input[name="password"]') as HTMLInputElement;
    expect(username.value).toBe('demo');
    expect(password.value.length).toBeGreaterThan(0);

    await act(async()=>{
      buttonByText('Entrar')?.click();
      await Promise.resolve();
      await Promise.resolve();
    });

    expect(fetchMock).toHaveBeenCalledTimes(1);
    expect(onLogin).toHaveBeenCalledTimes(1);
    expect(readAuthSession()?.username).toBe('demo');
    expect(readAuthSession()?.role).toBe('VIEWER');
  });

  it('shows a credential error without entering the app',async()=>{
    vi.spyOn(globalThis,'fetch').mockResolvedValue(new Response('',{status:401}));
    const onLogin=vi.fn();

    mount(<Login onLogin={onLogin} onBack={()=>{}} theme="light" onToggleTheme={()=>{}}/>);
    act(()=>buttonByText('Usar acesso demo')?.click());

    await act(async()=>{
      buttonByText('Entrar')?.click();
      await Promise.resolve();
      await Promise.resolve();
    });

    expect(onLogin).not.toHaveBeenCalled();
    expect(document.querySelector('[role="alert"]')?.textContent).toContain('Usuário ou senha inválidos');
  });
});
