import { useEffect, useMemo, useState } from 'react';
import { AlertTriangle, Boxes, BrainCircuit, ChevronRight, ClipboardCheck, LayoutDashboard, LogOut, Moon, PackageSearch, ScanLine, ShieldCheck, Sparkles, Sun, TrendingUp } from 'lucide-react';

const DEMO_USER='admin';
const DEMO_PASSWORD='Nexo@2026';

type Theme='light'|'dark';

function BrandImage({theme,className,alt}:{theme:Theme;className:string;alt:string}){
  return <img
    src={theme==='light'?'/nexo-logo-light.png':'/nexo-logo.png'}
    alt={alt}
    className={className}
  />;
}

function ThemeToggle({theme,onToggle,compact=false}:{theme:Theme;onToggle:()=>void;compact?:boolean}){
  const dark=theme==='dark';
  return <button
    type="button"
    className={'theme-toggle '+(compact?'compact':'')}
    onClick={onToggle}
    aria-label={dark?'Ativar tema claro':'Ativar tema escuro'}
    title={dark?'Tema claro':'Tema escuro'}
  >
    {dark?<Sun size={17}/>:<Moon size={17}/>}
    {!compact&&<span>{dark?'Tema claro':'Tema escuro'}</span>}
  </button>;
}


function Login({onLogin,theme,onToggleTheme}:{onLogin:()=>void;theme:Theme;onToggleTheme:()=>void}) {
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
    <div className="login-theme-toggle"><ThemeToggle theme={theme} onToggle={onToggleTheme} compact /></div>
    <section className="login-copy">
      <div className="brand official-brand">
      <BrandImage
        theme={theme}
        alt="Nexo — Sistema inteligente de gestão de estoque"
        className="brand-logo-full"
      />
    </div>
      <div className="hero">
        <span className="eyebrow">GESTÃO QUE ANTECIPA</span>
        <h1>Seu estoque deixa de ser um número e passa a ser uma decisão.</h1>
        <p>Controle lotes, validade e movimentações. Simule demanda, atraso de fornecedor e risco de ruptura antes que o problema aconteça.</p>
        <div className="trust"><span><ShieldCheck size={18}/> decisões auditáveis</span><span><BrainCircuit size={18}/> assistência inteligente</span></div>
      </div>
    </section>
    <section className="login-side">
      <form className="login-card" onSubmit={submit}>
        <div className="login-title">
          <BrandImage theme={theme} className="login-logo" alt="Nexo" />
          <div>
            <strong>Acesso administrativo</strong>
            <small>Ambiente demonstrativo</small>
          </div>
        </div>
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

type ProductView={
  id?:number;
  sku:string;
  name:string;
  category:string;
  stock:number;
  min:number;
  lot:string;
  expiry:string;
};

const DEMO_PRODUCTS:ProductView[]=[
  {sku:'MED-001',name:'Dipirona 500 mg',category:'Medicamentos',stock:120,min:60,lot:'DIP2609A',expiry:'18/12/2026'},
  {sku:'MED-014',name:'Amoxicilina 500 mg',category:'Medicamentos',stock:42,min:80,lot:'AMX2608C',expiry:'10/02/2027'},
  {sku:'MER-031',name:'Arroz tipo 1 1 kg',category:'Mercearia',stock:248,min:90,lot:'ARZ0926',expiry:'14/08/2027'},
  {sku:'REF-008',name:'Iogurte natural 170 g',category:'Refrigerados',stock:48,min:24,lot:'IOG2809',expiry:'07/10/2026'},
  {sku:'HIG-022',name:'Detergente neutro 500 ml',category:'Higiene e limpeza',stock:76,min:30,lot:'DET26091',expiry:'—'}
];

function ProductsPanel(){
  const API_URL=import.meta.env.VITE_API_URL || 'https://nexo-estoque-api.onrender.com';
  const [query,setQuery]=useState('');
  const [showForm,setShowForm]=useState(false);
  const [products,setProducts]=useState<ProductView[]>(DEMO_PRODUCTS);
  const [source,setSource]=useState<'loading'|'api'|'demo'>('loading');
  const [saving,setSaving]=useState(false);
  const [feedback,setFeedback]=useState('');
  const [form,setForm]=useState({sku:'',name:'',category:'',stock:'',min:''});

  function normalizeProduct(product:any):ProductView{
    return {
      id:Number(product.id),
      sku:String(product.sku||''),
      name:String(product.name||''),
      category:String(product.category||'Sem categoria'),
      stock:Number(product.currentStock||0),
      min:Number(product.minimumStock||0),
      lot:'—',
      expiry:'—'
    };
  }

  async function loadProducts(){
    try{
      const response=await fetch(API_URL+'/api/v1/products');
      if(!response.ok) throw new Error('API de produtos indisponível');
      const data=await response.json();
      if(!Array.isArray(data)) throw new Error('Resposta inválida');
      setProducts(data.map(normalizeProduct));
      setSource('api');
      setFeedback('');
    }catch{
      setProducts(DEMO_PRODUCTS);
      setSource('demo');
    }
  }

  useEffect(()=>{
    void loadProducts();
  },[]);

  const filtered=products.filter(p=>(p.name+' '+p.sku+' '+p.category).toLowerCase().includes(query.toLowerCase()));

  async function addProduct(e:React.FormEvent){
    e.preventDefault();
    if(!form.sku||!form.name||!form.category) {
      setFeedback('Preencha SKU, nome e categoria.');
      return;
    }

    setSaving(true);
    setFeedback('');
    const draft:ProductView={
      sku:form.sku.trim().toUpperCase(),
      name:form.name.trim(),
      category:form.category.trim(),
      stock:Number(form.stock||0),
      min:Number(form.min||0),
      lot:'—',
      expiry:'—'
    };

    try{
      const response=await fetch(API_URL+'/api/v1/products',{
        method:'POST',
        headers:{'Content-Type':'application/json'},
        body:JSON.stringify({
          id:null,
          sku:draft.sku,
          barcode:'',
          name:draft.name,
          category:draft.category,
          costPrice:0,
          salePrice:0,
          currentStock:draft.stock,
          minimumStock:draft.min,
          active:true
        })
      });
      if(!response.ok) throw new Error('Não foi possível persistir o produto');

      setForm({sku:'',name:'',category:'',stock:'',min:''});
      setShowForm(false);
      await loadProducts();
      setFeedback('Produto salvo na base MySQL.');
    }catch{
      setProducts(current=>[draft,...current]);
      setSource('demo');
      setForm({sku:'',name:'',category:'',stock:'',min:''});
      setShowForm(false);
      setFeedback('API/MySQL indisponível. Produto mantido apenas nesta sessão de demonstração.');
    }finally{
      setSaving(false);
    }
  }

  return <>
    <header className="page-header">
      <div><span className="eyebrow">CATÁLOGO E SALDOS</span><h1>Produtos</h1><p>Consulte estoque, lote e validade em uma única visão operacional.</p></div>
      <button className="new-action" onClick={()=>{setShowForm(true);setFeedback('')}}>+ Novo produto</button>
    </header>

    <section className="product-toolbar">
      <input value={query} onChange={e=>setQuery(e.target.value)} placeholder="Buscar por produto, SKU ou categoria"/>
      <div>
        <span>{filtered.length} produtos exibidos</span>
        <span className={'data-source '+source}>
          {source==='loading'?'conectando...':source==='api'?'API + MySQL':'modo demonstração'}
        </span>
      </div>
    </section>

    {feedback&&<div className={'product-feedback '+(source==='api'?'success':'warning')}>{feedback}</div>}

    {showForm&&<form className="product-form" onSubmit={addProduct}>
      <div className="form-title"><div><span className="eyebrow">CADASTRO RÁPIDO</span><h2>Novo produto</h2></div><button type="button" onClick={()=>setShowForm(false)}>Fechar</button></div>
      <div className="form-grid">
        <label>SKU<input value={form.sku} onChange={e=>setForm({...form,sku:e.target.value})} placeholder="Ex.: MED-102"/></label>
        <label>Nome<input value={form.name} onChange={e=>setForm({...form,name:e.target.value})} placeholder="Nome do produto"/></label>
        <label>Categoria<input value={form.category} onChange={e=>setForm({...form,category:e.target.value})} placeholder="Categoria"/></label>
        <label>Estoque inicial<input type="number" min="0" step="0.001" value={form.stock} onChange={e=>setForm({...form,stock:e.target.value})}/></label>
        <label>Estoque mínimo<input type="number" min="0" step="0.001" value={form.min} onChange={e=>setForm({...form,min:e.target.value})}/></label>
      </div>
      <div className="form-actions">
        <span>{source==='api'?'Cadastro será persistido pela Procedure MySQL.':'A API será usada automaticamente quando a base estiver disponível.'}</span>
        <button className="primary compact" disabled={saving}>{saving?'Salvando...':'Salvar produto'}</button>
      </div>
    </form>}

    <section className="product-table-wrap">
      <table className="product-table">
        <thead><tr><th>Produto</th><th>Categoria</th><th>Estoque</th><th>Mínimo</th><th>Lote</th><th>Validade</th><th>Situação</th></tr></thead>
        <tbody>
          {filtered.map(p=>{
            const critical=p.stock<p.min;
            return <tr key={p.id??p.sku}>
              <td><strong>{p.name}</strong><small>{p.sku}</small></td>
              <td>{p.category}</td>
              <td><b>{p.stock}</b></td>
              <td>{p.min}</td>
              <td>{p.lot}</td>
              <td>{p.expiry}</td>
              <td><span className={'stock-pill '+(critical?'critical':'healthy')}>{critical?'Crítico':'Saudável'}</span></td>
            </tr>;
          })}
          {filtered.length===0&&<tr><td colSpan={7} className="empty-state">Nenhum produto encontrado.</td></tr>}
        </tbody>
      </table>
    </section>
  </>;
}


type MovementView={
  id?:number;
  productId?:number;
  productName:string;
  movementType:'ENTRY'|'EXIT'|'ADJUSTMENT'|'RETURN';
  quantity:number;
  balanceBefore?:number;
  balanceAfter?:number;
  reason?:string;
  createdAt?:string;
};

function StockMovementModal({onClose,onSaved}:{onClose:()=>void;onSaved:()=>void}){
  const API_URL=import.meta.env.VITE_API_URL || 'https://nexo-estoque-api.onrender.com';
  const [products,setProducts]=useState<ProductView[]>([]);
  const [loading,setLoading]=useState(true);
  const [saving,setSaving]=useState(false);
  const [error,setError]=useState('');
  const [form,setForm]=useState({productId:'',movementType:'ENTRY',quantity:'',reason:''});

  useEffect(()=>{
    (async()=>{
      try{
        const response=await fetch(API_URL+'/api/v1/products');
        if(!response.ok) throw new Error();
        const data=await response.json();
        setProducts((Array.isArray(data)?data:[]).map((p:any)=>({
          id:Number(p.id),
          sku:String(p.sku||''),
          name:String(p.name||''),
          category:String(p.category||''),
          stock:Number(p.currentStock||0),
          min:Number(p.minimumStock||0),
          lot:'—',
          expiry:'—'
        })));
      }catch{
        setError('A API de produtos está indisponível. Movimentações reais exigem conexão com a base.');
      }finally{
        setLoading(false);
      }
    })();
  },[]);

  async function submit(e:React.FormEvent){
    e.preventDefault();
    setError('');
    if(!form.productId||!form.quantity){
      setError('Selecione o produto e informe a quantidade.');
      return;
    }

    const rawQuantity=Number(form.quantity);
    if(!Number.isFinite(rawQuantity)||rawQuantity===0){
      setError('Informe uma quantidade válida e diferente de zero.');
      return;
    }

    const quantity=form.movementType==='ADJUSTMENT'
      ? rawQuantity
      : Math.abs(rawQuantity);

    setSaving(true);
    try{
      const response=await fetch(API_URL+'/api/v1/stock/movements',{
        method:'POST',
        headers:{'Content-Type':'application/json'},
        body:JSON.stringify({
          productId:Number(form.productId),
          movementType:form.movementType,
          quantity,
          reason:form.reason.trim()
        })
      });
      const payload=await response.json().catch(()=>null);
      if(!response.ok){
        const detail=payload?.detail||payload?.message||'Não foi possível registrar a movimentação.';
        throw new Error(detail);
      }
      onSaved();
      onClose();
    }catch(err){
      setError(err instanceof Error?err.message:'Não foi possível registrar a movimentação.');
    }finally{
      setSaving(false);
    }
  }

  return <div className="modal-backdrop" role="presentation" onMouseDown={e=>{if(e.target===e.currentTarget)onClose()}}>
    <section className="movement-modal" role="dialog" aria-modal="true" aria-labelledby="movement-title">
      <div className="modal-head">
        <div><span className="eyebrow">MOVIMENTAÇÃO DE ESTOQUE</span><h2 id="movement-title">Registrar movimentação</h2></div>
        <button type="button" className="modal-close" onClick={onClose}>Fechar</button>
      </div>

      <form onSubmit={submit}>
        <label>Produto
          <select value={form.productId} onChange={e=>setForm({...form,productId:e.target.value})} disabled={loading}>
            <option value="">{loading?'Carregando produtos...':'Selecione um produto'}</option>
            {products.map(p=><option key={p.id??p.sku} value={p.id}>{p.name} · {p.sku} · saldo {p.stock}</option>)}
          </select>
        </label>

        <div className="movement-grid">
          <label>Tipo
            <select value={form.movementType} onChange={e=>setForm({...form,movementType:e.target.value})}>
              <option value="ENTRY">Entrada</option>
              <option value="EXIT">Saída</option>
              <option value="RETURN">Devolução</option>
              <option value="ADJUSTMENT">Ajuste</option>
            </select>
          </label>

          <label>Quantidade
            <input
              type="number"
              step="0.001"
              value={form.quantity}
              onChange={e=>setForm({...form,quantity:e.target.value})}
              placeholder={form.movementType==='ADJUSTMENT'?'Ex.: -3 ou 5':'Ex.: 12'}
            />
          </label>
        </div>

        <label>Motivo
          <textarea
            value={form.reason}
            onChange={e=>setForm({...form,reason:e.target.value})}
            placeholder="Ex.: recebimento do fornecedor, baixa por venda, correção após inventário"
            rows={3}
          />
        </label>

        {form.movementType==='ADJUSTMENT'&&<div className="movement-hint">No ajuste, use valor positivo para aumentar o saldo e negativo para reduzir.</div>}
        {error&&<div className="error">{error}</div>}

        <div className="modal-actions">
          <button type="button" className="secondary-action" onClick={onClose}>Cancelar</button>
          <button className="primary compact" disabled={saving||loading}>{saving?'Registrando...':'Registrar movimentação'}</button>
        </div>
      </form>
    </section>
  </div>;
}

function RecentMovements({refreshKey}:{refreshKey:number}){
  const API_URL=import.meta.env.VITE_API_URL || 'https://nexo-estoque-api.onrender.com';
  const [items,setItems]=useState<MovementView[]>([]);
  const [source,setSource]=useState<'loading'|'api'|'empty'|'offline'>('loading');

  useEffect(()=>{
    (async()=>{
      setSource('loading');
      try{
        const response=await fetch(API_URL+'/api/v1/stock/movements?limit=8');
        if(!response.ok) throw new Error();
        const data=await response.json();
        const rows:Array<any>=Array.isArray(data)?data:[];
        setItems(rows.map(item=>({
          id:Number(item.id),
          productId:Number(item.productId),
          productName:String(item.productName||'Produto'),
          movementType:item.movementType,
          quantity:Number(item.quantity||0),
          balanceBefore:Number(item.balanceBefore||0),
          balanceAfter:Number(item.balanceAfter||0),
          reason:item.reason||'',
          createdAt:item.createdAt||''
        })));
        setSource(rows.length?'api':'empty');
      }catch{
        setItems([]);
        setSource('offline');
      }
    })();
  },[refreshKey]);

  const label=(type:MovementView['movementType'])=>({
    ENTRY:'Entrada',
    EXIT:'Saída',
    RETURN:'Devolução',
    ADJUSTMENT:'Ajuste'
  }[type]);

  return <section className="movements-section">
    <div className="section-head">
      <div><span className="eyebrow">RASTREABILIDADE</span><h2>Movimentações recentes</h2></div>
      <span className={'data-source '+(source==='api'?'api':source==='loading'?'loading':'demo')}>
        {source==='api'?'API + MySQL':source==='loading'?'carregando...':source==='empty'?'sem movimentações':'API indisponível'}
      </span>
    </div>

    {items.length>0
      ? <div className="movement-list">
          {items.map(item=><article className="movement-row" key={item.id}>
            <div className={'movement-kind '+item.movementType.toLowerCase()}>{label(item.movementType)}</div>
            <div className="movement-main">
              <strong>{item.productName}</strong>
              <span>{item.reason||'Sem motivo informado'}</span>
            </div>
            <div className="movement-balance">
              <b>{item.balanceBefore} → {item.balanceAfter}</b>
              <span>{item.quantity} un.</span>
            </div>
            <time>{item.createdAt?new Date(item.createdAt).toLocaleString('pt-BR'):'—'}</time>
          </article>)}
        </div>
      : <div className="movement-empty">
          {source==='offline'?'Conecte a API/MySQL para visualizar o histórico real.':'Nenhuma movimentação registrada ainda.'}
        </div>}
  </section>;
}

function Dashboard({logout,theme,onToggleTheme}:{logout:()=>void;theme:Theme;onToggleTheme:()=>void}){
  const [page,setPage]=useState<'dashboard'|'products'>('dashboard');
  const [showMovement,setShowMovement]=useState(false);
  const [movementRefresh,setMovementRefresh]=useState(0);
  const cards=[
    ['Itens em estoque','18.421',Boxes,'+3,8%'],
    ['Estoque crítico','27',AlertTriangle,'8 urgentes'],
    ['Risco de validade','14',PackageSearch,'R$ 1.840'],
    ['Precisão inventário','98,7%',ClipboardCheck,'+1,2 p.p.']
  ] as const;

  return <div className="app-shell">
    <aside>
      <div className="brand sidebar-brand">
        <BrandImage theme={theme} className="sidebar-logo-full" alt="Nexo" />
        <img src="/nexo-symbol.png" alt="Nexo" className="sidebar-logo-symbol" />
      </div>
      <nav>
        <a className={page==='dashboard'?'active':''} onClick={()=>setPage('dashboard')}><LayoutDashboard size={19}/> Visão geral</a>
        <a className={page==='products'?'active':''} onClick={()=>setPage('products')}><Boxes size={19}/> Produtos</a>
        <a><ClipboardCheck size={19}/> Inventário cego</a>
        <a><TrendingUp size={19}/> Simulador</a>
        <a><BrainCircuit size={19}/> Assistente</a>
      </nav>
      <div className="sidebar-bottom"><ThemeToggle theme={theme} onToggle={onToggleTheme}/><button className="logout" onClick={logout}><LogOut size={18}/> Sair</button></div>
    </aside>
    <main className="workspace">
      {page==='products'
        ? <ProductsPanel/>
        : <>
          <header><div><span className="eyebrow">NEXO ESTOQUE</span><h1>Boa tarde, administrador.</h1><p>O estoque está estável, mas há 8 itens que merecem ação hoje.</p></div><button className="new-action" onClick={()=>setShowMovement(true)}>+ Nova movimentação</button></header>
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
          <RecentMovements refreshKey={movementRefresh}/>
          <Simulator/>
          {showMovement&&<StockMovementModal
            onClose={()=>setShowMovement(false)}
            onSaved={()=>setMovementRefresh(value=>value+1)}
          />}
        </>}
    </main>
  </div>;
}

export default function App(){
  const [auth,setAuth]=useState(sessionStorage.getItem('nexo-auth')==='demo');
  const [theme,setTheme]=useState<Theme>(()=>localStorage.getItem('nexo-theme')==='dark'?'dark':'light');

  useEffect(()=>{
    document.documentElement.dataset.theme=theme;
    localStorage.setItem('nexo-theme',theme);
  },[theme]);

  const toggleTheme=()=>setTheme(current=>current==='light'?'dark':'light');

  return auth
    ? <Dashboard
        theme={theme}
        onToggleTheme={toggleTheme}
        logout={()=>{sessionStorage.removeItem('nexo-auth');setAuth(false)}}
      />
    : <Login onLogin={()=>setAuth(true)} theme={theme} onToggleTheme={toggleTheme}/>;
}
