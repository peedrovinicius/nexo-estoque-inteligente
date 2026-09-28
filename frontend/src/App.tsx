import { useMemo, useState } from 'react';
import { AlertTriangle, Boxes, BrainCircuit, ChevronRight, ClipboardCheck, LayoutDashboard, LogOut, PackageSearch, ScanLine, ShieldCheck, Sparkles, TrendingUp } from 'lucide-react';

const DEMO_USER='admin';
const DEMO_PASSWORD='Nexo@2026';

function Login({onLogin}:{onLogin:()=>void}) {
  const [user,setUser]=useState('');
  const [password,setPassword]=useState('');
  const [error,setError]=useState('');
  function submit(e:React.FormEvent){
    e.preventDefault();
    if(user===DEMO_USER && password===DEMO_PASSWORD){
      sessionStorage.setItem('nexo-auth','demo');
      onLogin();
    } else setError('Usuário ou senha inválidos.');
  }
  return <main className="login-shell">
    <section className="login-copy">
      <div className="brand"><span className="brand-mark">N</span><span>Nexo Estoque</span></div>
      <div className="hero">
        <span className="eyebrow">GESTÃO QUE ANTECIPA</span>
        <h1>Seu estoque deixa de ser um número e passa a ser uma decisão.</h1>
        <p>Controle lotes, validade e movimentações. Simule demanda, atraso de fornecedor e risco de ruptura antes que o problema aconteça.</p>
        <div className="trust"><span><ShieldCheck size={18}/> decisões auditáveis</span><span><BrainCircuit size={18}/> assistência inteligente</span></div>
      </div>
    </section>
    <section className="login-side">
      <form className="login-card" onSubmit={submit}>
        <div className="login-title"><span className="mini-mark">N</span><div><strong>Acesso administrativo</strong><small>Ambiente demonstrativo</small></div></div>
        <label>Usuário<input value={user} onChange={e=>setUser(e.target.value)} placeholder="Digite seu usuário" autoFocus/></label>
        <label>Senha<input type="password" value={password} onChange={e=>setPassword(e.target.value)} placeholder="Digite sua senha"/></label>
        {error&&<div className="error">{error}</div>}
        <button className="primary">Entrar <ChevronRight size={18}/></button>
        <div className="demo"><span>Demo</span><code>admin</code><code>Nexo@2026</code></div>
      </form>
    </section>
  </main>;
}

function Simulator(){
  const [demand,setDemand]=useState(20);
  const [delay,setDelay]=useState(3);
  const result=useMemo(()=>{
    const stock=120;
    const daily=14*(1+demand/100);
    const lead=5+delay;
    const coverage=Math.floor(stock/daily);
    const safety=Math.max(2,Math.ceil(lead*.35));
    const recommended=Math.max(0,Math.ceil(daily*(lead+safety)-stock));
    const stockout=new Date(Date.now()+coverage*86400000).toLocaleDateString('pt-BR');
    const value=Math.max(0,lead-coverage)*daily*6.85;
    return {coverage,recommended,stockout,value,risk:coverage<lead?'ALTO':coverage<lead+4?'MODERADO':'BAIXO'};
  },[demand,delay]);
  return <section className="simulator">
    <div className="section-head"><div><span className="eyebrow">LABORATÓRIO DE DECISÃO</span><h2>E se o cenário mudar?</h2></div><span className="audit"><ShieldCheck size={16}/> cálculo reproduzível</span></div>
    <div className="sim-grid">
      <div className="controls">
        <div className="selected"><div><small>Produto simulado</small><strong>Dipirona 500 mg</strong></div><ScanLine size={22}/></div>
        <label>Demanda aumenta <b>{demand}%</b><input type="range" min="0" max="80" value={demand} onChange={e=>setDemand(+e.target.value)}/></label>
        <label>Atraso do fornecedor <b>{delay} dias</b><input type="range" min="0" max="14" value={delay} onChange={e=>setDelay(+e.target.value)}/></label>
        <div className="note"><Sparkles size={18}/><p>A IA explica o cenário; as quantidades continuam sendo calculadas por regras auditáveis.</p></div>
      </div>
      <div className="result">
        <div className="risk-line"><span>Risco projetado</span><strong className={'risk '+result.risk.toLowerCase()}>{result.risk}</strong></div>
        <div className="coverage">{result.coverage}<small> dias de cobertura</small></div>
        <div className="result-grid">
          <div><span>Ruptura estimada</span><b>{result.stockout}</b></div>
          <div><span>Compra sugerida</span><b>{result.recommended} un.</b></div>
          <div><span>Valor em risco</span><b>R$ {result.value.toFixed(2).replace('.',',')}</b></div>
        </div>
        <button className="ghost"><BrainCircuit size={18}/> Explicar esta decisão</button>
      </div>
    </div>
  </section>;
}

function Dashboard({logout}:{logout:()=>void}){
  const cards=[
    ['Itens em estoque','18.421',Boxes,'+3,8%'],
    ['Estoque crítico','27',AlertTriangle,'8 urgentes'],
    ['Risco de validade','14',PackageSearch,'R$ 1.840'],
    ['Precisão inventário','98,7%',ClipboardCheck,'+1,2 p.p.']
  ] as const;
  return <div className="app-shell">
    <aside>
      <div className="brand"><span className="brand-mark light">N</span><span>Nexo</span></div>
      <nav>
        <a className="active"><LayoutDashboard size={19}/> Visão geral</a>
        <a><Boxes size={19}/> Produtos</a>
        <a><ClipboardCheck size={19}/> Inventário cego</a>
        <a><TrendingUp size={19}/> Simulador</a>
        <a><BrainCircuit size={19}/> Assistente</a>
      </nav>
      <button className="logout" onClick={logout}><LogOut size={18}/> Sair</button>
    </aside>
    <main className="workspace">
      <header><div><span className="eyebrow">NEXO ESTOQUE</span><h1>Boa tarde, administrador.</h1><p>O estoque está estável, mas há 8 itens que merecem ação hoje.</p></div><button className="new-action">+ Nova movimentação</button></header>
      <section className="cards">
        {cards.map(([title,value,Icon,detail])=><article className="metric" key={title}><div className="metric-top"><span>{title}</span><Icon size={20}/></div><strong>{value}</strong><small>{detail}</small></article>)}
      </section>
      <section className="attention">
        <div className="section-head"><div><span className="eyebrow">PRIORIDADE DO DIA</span><h2>O que precisa da sua atenção</h2></div></div>
        <div className="attention-grid">
          <article className="action-card danger"><div className="icon"><AlertTriangle/></div><div><strong>Amoxicilina 500 mg</strong><span>Ruptura prevista antes da próxima entrega</span></div><b>6 dias</b></article>
          <article className="action-card warning"><div className="icon"><PackageSearch/></div><div><strong>Iogurte natural 170 g</strong><span>17 unidades podem vencer sem saída</span></div><b>9 dias</b></article>
          <article className="action-card"><div className="icon"><ClipboardCheck/></div><div><strong>Inventário corredor B</strong><span>Contagem cega pendente desde ontem</span></div><b>42 itens</b></article>
        </div>
      </section>
      <Simulator/>
    </main>
  </div>;
}

export default function App(){
  const [auth,setAuth]=useState(sessionStorage.getItem('nexo-auth')==='demo');
  return auth?<Dashboard logout={()=>{sessionStorage.removeItem('nexo-auth');setAuth(false)}}/>:<Login onLogin={()=>setAuth(true)}/>;
}
