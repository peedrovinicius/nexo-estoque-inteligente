import { useMemo, useState } from 'react';
import { AlertTriangle, Boxes, BrainCircuit, ChevronRight, ClipboardCheck, LayoutDashboard, LogOut, PackageSearch, ScanLine, ShieldCheck, Sparkles, TrendingUp } from 'lucide-react';

const DEMO_USER='admin';
const DEMO_PASSWORD='Nexo@2026';

function NexoLogo({compact=false,tagline=false}:{compact?:boolean;tagline?:boolean}){
  return <div className={'nexo-logo '+(compact?'compact':'')}>
    <svg className="nexo-symbol" viewBox="0 0 64 64" aria-hidden="true">
      <defs>
        <linearGradient id="nexoGradient" x1="8" y1="4" x2="54" y2="60" gradientUnits="userSpaceOnUse">
          <stop offset="0" stopColor="#20e6b2"/>
          <stop offset=".48" stopColor="#08aaf2"/>
          <stop offset="1" stopColor="#2449ff"/>
        </linearGradient>
      </defs>
      <path fill="url(#nexoGradient)" d="M31.8 3.7 56 17.5v28L32 60 8 46.1V19.2l9.8-5.8 9.6 5.5-9.1 5.3v16.4L32 48.4l13.8-8V24.1L32 16.2l-5.5 3.2 17 9.8v10.6L32 46.4 20.4 39.7V28.8L32 35.5l3.4-2-17-9.8V12.5L31.8 3.7Z"/>
    </svg>
    {!compact&&<div className="nexo-wordmark"><strong>Nexo</strong>{tagline&&<span>Sistema inteligente de gestão de estoque</span>}</div>}
  </div>;
}


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
      <div className="brand official-brand"><img src="/nexo-logo.png" alt="Nexo — Sistema inteligente de gestão de estoque"/></div>
      <div className="hero">
        <span className="eyebrow">GESTÃO QUE ANTECIPA</span>
        <h1>Seu estoque deixa de ser um número e passa a ser uma decisão.</h1>
        <p>Controle lotes, validade e movimentações. Simule demanda, atraso de fornecedor e risco de ruptura antes que o problema aconteça.</p>
        <div className="trust"><span><ShieldCheck size={18}/> decisões auditáveis</span><span><BrainCircuit size={18}/> assistência inteligente</span></div>
      </div>
    </section>
    <section className="login-side">
      <form className="login-card" onSubmit={submit}>
        <div className="login-title"><img className="login-logo" src="/nexo-logo.png" alt="Nexo"/><div><strong>Acesso administrativo</strong><small>Ambiente demonstrativo</small></div></div>
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
  const API_URL=import.meta.env.VITE_API_URL || 'https://nexo-estoque-api.onrender.com';
  const [demand,setDemand]=useState(20);
  const [delay,setDelay]=useState(3);
  const [loading,setLoading]=useState(false);
  const [advisorLoading,setAdvisorLoading]=useState(false);
  const [explanation,setExplanation]=useState('');
  const [advisorSource,setAdvisorSource]=useState('');
  const [source,setSource]=useState<'local'|'api'>('local');

  const localResult=useMemo(()=>{
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

  const [result,setResult]=useState(localResult);

  async function simulate(){
    setLoading(true);
    setExplanation('');
    try{
      const response=await fetch(API_URL+'/api/v1/simulations',{
        method:'POST',
        headers:{'Content-Type':'application/json'},
        body:JSON.stringify({
          productName:'Dipirona 500 mg',
          currentStock:120,
          averageDailyDemand:14,
          supplierLeadTimeDays:5,
          demandVariationPercent:demand,
          supplierDelayDays:delay,
          plannedPurchase:0,
          unitCost:6.85
        })
      });
      if(!response.ok) throw new Error('API indisponível');
      const data=await response.json();
      setResult({
        coverage:data.coverageDays,
        recommended:Number(data.recommendedPurchase),
        stockout:data.estimatedStockoutDate
          ? new Date(data.estimatedStockoutDate+'T12:00:00').toLocaleDateString('pt-BR')
          : 'Sem previsão',
        value:Number(data.estimatedValueAtRisk),
        risk:data.riskLevel
      });
      setSource('api');
    }catch{
      setResult(localResult);
      setSource('local');
    }finally{
      setLoading(false);
    }
  }

  async function explain(){
    setAdvisorLoading(true);
    setExplanation('');
    try{
      const response=await fetch(API_URL+'/api/v1/advisor/explain',{
        method:'POST',
        headers:{'Content-Type':'application/json'},
        body:JSON.stringify({
          productName:'Dipirona 500 mg',
          riskLevel:String(result.risk),
          coverageDays:result.coverage,
          recommendedPurchase:result.recommended,
          estimatedValueAtRisk:Number(result.value.toFixed(2)),
          supplierLeadTimeDays:5,
          supplierDelayDays:delay
        })
      });
      if(!response.ok) throw new Error('Assistente indisponível');
      const data=await response.json();
      setExplanation(data.explanation);
      setAdvisorSource(data.source==='openai'?'OpenAI '+data.model:'explicação determinística');
    }catch{
      setExplanation('O assistente está temporariamente indisponível. Os números acima continuam válidos porque são calculados pelo motor determinístico.');
      setAdvisorSource('fallback local');
    }finally{
      setAdvisorLoading(false);
    }
  }

  return <section className="simulator">
    <div className="section-head"><div><span className="eyebrow">LABORATÓRIO DE DECISÃO</span><h2>E se o cenário mudar?</h2></div><span className="audit"><ShieldCheck size={16}/> cálculo reproduzível</span></div>
    <div className="sim-grid">
      <div className="controls">
        <div className="selected"><div><small>Produto simulado</small><strong>Dipirona 500 mg</strong></div><ScanLine size={22}/></div>
        <label>Demanda aumenta <b>{demand}%</b><input type="range" min="0" max="80" value={demand} onChange={e=>{setDemand(+e.target.value);setSource('local');setExplanation('')}}/></label>
        <label>Atraso do fornecedor <b>{delay} dias</b><input type="range" min="0" max="14" value={delay} onChange={e=>{setDelay(+e.target.value);setSource('local');setExplanation('')}}/></label>
        <button className="simulate-btn" onClick={simulate} disabled={loading}>{loading?'Calculando...':'Simular com a API Java'}</button>
        <div className="note"><Sparkles size={18}/><p>A IA explica o cenário; as quantidades continuam sendo calculadas por regras auditáveis.</p></div>
      </div>
      <div className="result">
        <div className="risk-line"><span>Risco projetado</span><strong className={'risk '+String(result.risk).toLowerCase()}>{result.risk}</strong></div>
        <div className="api-status"><span className={'status-dot '+source}></span>{source==='api'?'Resultado calculado pelo Spring Boot':'Prévia local — execute a API para validar'}</div>
        <div className="coverage">{result.coverage}<small> dias de cobertura</small></div>
        <div className="result-grid">
          <div><span>Ruptura estimada</span><b>{result.stockout}</b></div>
          <div><span>Compra sugerida</span><b>{result.recommended} un.</b></div>
          <div><span>Valor em risco</span><b>R$ {result.value.toFixed(2).replace('.',',')}</b></div>
        </div>
        <button className="ghost" onClick={explain} disabled={advisorLoading}><BrainCircuit size={18}/>{advisorLoading?'Analisando...':'Explicar esta decisão'}</button>
        {explanation&&<div className="advisor-box"><div><BrainCircuit size={17}/><strong>Assistente Nexo</strong><span>{advisorSource}</span></div><p>{explanation}</p></div>}
      </div>
    </div>
  </section>;
}

function ProductsPanel(){
  const [query,setQuery]=useState('');
  const [showForm,setShowForm]=useState(false);
  const [products,setProducts]=useState([
    {sku:'MED-001',name:'Dipirona 500 mg',category:'Medicamentos',stock:120,min:60,lot:'DIP2609A',expiry:'18/12/2026'},
    {sku:'MED-014',name:'Amoxicilina 500 mg',category:'Medicamentos',stock:42,min:80,lot:'AMX2608C',expiry:'10/02/2027'},
    {sku:'MER-031',name:'Arroz tipo 1 1 kg',category:'Mercearia',stock:248,min:90,lot:'ARZ0926',expiry:'14/08/2027'},
    {sku:'REF-008',name:'Iogurte natural 170 g',category:'Refrigerados',stock:48,min:24,lot:'IOG2809',expiry:'07/10/2026'},
    {sku:'HIG-022',name:'Detergente neutro 500 ml',category:'Higiene e limpeza',stock:76,min:30,lot:'DET26091',expiry:'—'}
  ]);
  const [form,setForm]=useState({sku:'',name:'',category:'',stock:'',min:''});
  const filtered=products.filter(p=>(p.name+' '+p.sku+' '+p.category).toLowerCase().includes(query.toLowerCase()));

  function addProduct(e:React.FormEvent){
    e.preventDefault();
    if(!form.sku||!form.name||!form.category) return;
    setProducts(current=>[{
      sku:form.sku.toUpperCase(),
      name:form.name,
      category:form.category,
      stock:Number(form.stock||0),
      min:Number(form.min||0),
      lot:'—',
      expiry:'—'
    },...current]);
    setForm({sku:'',name:'',category:'',stock:'',min:''});
    setShowForm(false);
  }

  return <>
    <header className="page-header">
      <div><span className="eyebrow">CATÁLOGO E SALDOS</span><h1>Produtos</h1><p>Consulte estoque, lote e validade em uma única visão operacional.</p></div>
      <button className="new-action" onClick={()=>setShowForm(true)}>+ Novo produto</button>
    </header>

    <section className="product-toolbar">
      <input value={query} onChange={e=>setQuery(e.target.value)} placeholder="Buscar por produto, SKU ou categoria"/>
      <div><span>{filtered.length} produtos exibidos</span><span className="demo-badge">dados de demonstração</span></div>
    </section>

    {showForm&&<form className="product-form" onSubmit={addProduct}>
      <div className="form-title"><div><span className="eyebrow">CADASTRO RÁPIDO</span><h2>Novo produto</h2></div><button type="button" onClick={()=>setShowForm(false)}>Fechar</button></div>
      <div className="form-grid">
        <label>SKU<input value={form.sku} onChange={e=>setForm({...form,sku:e.target.value})} placeholder="Ex.: MED-102"/></label>
        <label>Nome<input value={form.name} onChange={e=>setForm({...form,name:e.target.value})} placeholder="Nome do produto"/></label>
        <label>Categoria<input value={form.category} onChange={e=>setForm({...form,category:e.target.value})} placeholder="Categoria"/></label>
        <label>Estoque inicial<input type="number" min="0" value={form.stock} onChange={e=>setForm({...form,stock:e.target.value})}/></label>
        <label>Estoque mínimo<input type="number" min="0" value={form.min} onChange={e=>setForm({...form,min:e.target.value})}/></label>
      </div>
      <div className="form-actions"><span>A persistência definitiva será feita pela Procedure MySQL.</span><button className="primary compact">Adicionar à demonstração</button></div>
    </form>}

    <section className="product-table-wrap">
      <table className="product-table">
        <thead><tr><th>Produto</th><th>Categoria</th><th>Estoque</th><th>Mínimo</th><th>Lote</th><th>Validade</th><th>Situação</th></tr></thead>
        <tbody>
          {filtered.map(p=>{
            const critical=p.stock<p.min;
            return <tr key={p.sku}>
              <td><strong>{p.name}</strong><small>{p.sku}</small></td>
              <td>{p.category}</td>
              <td><b>{p.stock}</b></td>
              <td>{p.min}</td>
              <td>{p.lot}</td>
              <td>{p.expiry}</td>
              <td><span className={'stock-pill '+(critical?'critical':'healthy')}>{critical?'Crítico':'Saudável'}</span></td>
            </tr>;
          })}
        </tbody>
      </table>
    </section>
  </>;
}


function Dashboard({logout}:{logout:()=>void}){
  const [page,setPage]=useState<'dashboard'|'products'>('dashboard');
  const cards=[
    ['Itens em estoque','18.421',Boxes,'+3,8%'],
    ['Estoque crítico','27',AlertTriangle,'8 urgentes'],
    ['Risco de validade','14',PackageSearch,'R$ 1.840'],
    ['Precisão inventário','98,7%',ClipboardCheck,'+1,2 p.p.']
  ] as const;

  return <div className="app-shell">
    <aside>
      <div className="brand sidebar-brand"><img src="/nexo-logo.png" alt="Nexo"/></div>
      <nav>
        <a className={page==='dashboard'?'active':''} onClick={()=>setPage('dashboard')}><LayoutDashboard size={19}/> Visão geral</a>
        <a className={page==='products'?'active':''} onClick={()=>setPage('products')}><Boxes size={19}/> Produtos</a>
        <a><ClipboardCheck size={19}/> Inventário cego</a>
        <a><TrendingUp size={19}/> Simulador</a>
        <a><BrainCircuit size={19}/> Assistente</a>
      </nav>
      <button className="logout" onClick={logout}><LogOut size={18}/> Sair</button>
    </aside>
    <main className="workspace">
      {page==='products'
        ? <ProductsPanel/>
        : <>
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
        </>}
    </main>
  </div>;
}

export default function App(){
  const [auth,setAuth]=useState(sessionStorage.getItem('nexo-auth')==='demo');
  return auth?<Dashboard logout={()=>{sessionStorage.removeItem('nexo-auth');setAuth(false)}}/>:<Login onLogin={()=>setAuth(true)}/>;
}
