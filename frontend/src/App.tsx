import { useEffect, useState } from 'react';
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
  const API_URL=import.meta.env.VITE_API_URL || 'https://nexo-estoque-api-production.up.railway.app';
  const [products,setProducts]=useState<Array<{
    id:number;
    sku:string;
    name:string;
    currentStock:number;
    costPrice:number;
  }>>([]);
  const [selectedId,setSelectedId]=useState('');
  const [averageDailyDemand,setAverageDailyDemand]=useState('14');
  const [supplierLeadTimeDays,setSupplierLeadTimeDays]=useState('5');
  const [plannedPurchase,setPlannedPurchase]=useState('0');
  const [demand,setDemand]=useState(20);
  const [delay,setDelay]=useState(3);
  const [loading,setLoading]=useState(false);
  const [productsLoading,setProductsLoading]=useState(true);
  const [advisorLoading,setAdvisorLoading]=useState(false);
  const [explanation,setExplanation]=useState('');
  const [advisorSource,setAdvisorSource]=useState('');
  const [error,setError]=useState('');
  const [source,setSource]=useState<'idle'|'api'|'offline'>('idle');
  const [result,setResult]=useState<{
    coverage:number;
    recommended:number;
    stockout:string;
    value:number;
    risk:string;
  }|null>(null);

  const selected=products.find(product=>String(product.id)===selectedId)||null;

  useEffect(()=>{
    let active=true;

    (async()=>{
      try{
        const response=await fetch(API_URL+'/api/v1/products');
        if(!response.ok) throw new Error();
        const data=await response.json();
        if(!active) return;

        const rows=(Array.isArray(data)?data:[])
          .filter((item:any)=>item.active!==false)
          .map((item:any)=>({
            id:Number(item.id),
            sku:String(item.sku||''),
            name:String(item.name||'Produto'),
            currentStock:Number(item.currentStock||0),
            costPrice:Number(item.costPrice||0)
          }));

        setProducts(rows);
        if(rows.length) setSelectedId(String(rows[0].id));
        setSource('idle');
      }catch{
        if(active){
          setProducts([]);
          setSelectedId('');
          setSource('offline');
          setError('Não foi possível carregar os produtos reais para simulação.');
        }
      }finally{
        if(active) setProductsLoading(false);
      }
    })();

    return ()=>{active=false};
  },[]);

  function invalidate(){
    setResult(null);
    setExplanation('');
    setAdvisorSource('');
    if(source!=='offline') setSource('idle');
  }

  async function simulate(){
    setError('');
    setExplanation('');

    if(!selected){
      setError('Selecione um produto real da base.');
      return;
    }

    const daily=Number(averageDailyDemand);
    const lead=Number(supplierLeadTimeDays);
    const planned=Number(plannedPurchase||0);

    if(!Number.isFinite(daily)||daily<=0){
      setError('Informe uma demanda média diária maior que zero.');
      return;
    }
    if(!Number.isFinite(lead)||lead<0){
      setError('Informe um lead time válido.');
      return;
    }
    if(!Number.isFinite(planned)||planned<0){
      setError('A compra planejada não pode ser negativa.');
      return;
    }

    setLoading(true);
    try{
      const response=await fetch(API_URL+'/api/v1/simulations',{
        method:'POST',
        headers:{'Content-Type':'application/json'},
        body:JSON.stringify({
          productName:selected.name,
          currentStock:selected.currentStock,
          averageDailyDemand:daily,
          supplierLeadTimeDays:Math.floor(lead),
          demandVariationPercent:demand,
          supplierDelayDays:delay,
          plannedPurchase:planned,
          unitCost:selected.costPrice
        })
      });

      const data=await response.json().catch(()=>null);
      if(!response.ok) throw new Error(data?.detail||data?.message||'API de simulação indisponível');

      setResult({
        coverage:Number(data.coverageDays),
        recommended:Number(data.recommendedPurchase),
        stockout:data.estimatedStockoutDate
          ? new Date(data.estimatedStockoutDate+'T12:00:00').toLocaleDateString('pt-BR')
          : 'Sem previsão',
        value:Number(data.estimatedValueAtRisk),
        risk:String(data.riskLevel)
      });
      setSource('api');
    }catch(err){
      setResult(null);
      setSource('offline');
      setError(err instanceof Error?err.message:'API de simulação indisponível.');
    }finally{
      setLoading(false);
    }
  }

  async function explain(){
    if(!result||!selected) return;

    setAdvisorLoading(true);
    setExplanation('');
    try{
      const response=await fetch(API_URL+'/api/v1/advisor/explain',{
        method:'POST',
        headers:{'Content-Type':'application/json'},
        body:JSON.stringify({
          productName:selected.name,
          riskLevel:String(result.risk),
          coverageDays:result.coverage,
          recommendedPurchase:result.recommended,
          estimatedValueAtRisk:Number(result.value.toFixed(2)),
          supplierLeadTimeDays:Number(supplierLeadTimeDays||0),
          supplierDelayDays:delay
        })
      });
      const data=await response.json().catch(()=>null);
      if(!response.ok) throw new Error(data?.detail||data?.message||'Assistente indisponível');
      setExplanation(String(data.explanation||''));
      setAdvisorSource(data.source==='openai'?'OpenAI '+data.model:'explicação determinística');
    }catch{
      setExplanation('O assistente está temporariamente indisponível. A simulação acima continua válida porque foi calculada pelo backend.');
      setAdvisorSource('assistente indisponível');
    }finally{
      setAdvisorLoading(false);
    }
  }

  return <>
    <header className="page-header">
      <div>
        <span className="eyebrow">LABORATÓRIO DE DECISÃO</span>
        <h1>Simulador de cenários</h1>
        <p>Use o saldo e o custo reais do estoque para testar demanda, atraso do fornecedor e compra planejada.</p>
      </div>
      <span className="audit"><ShieldCheck size={16}/> cálculo reproduzível</span>
    </header>

    <section className="simulator simulator-page">
      <div className="sim-grid">
        <div className="controls">
          <div className="selected simulator-product">
            <div>
              <small>Produto real</small>
              <select
                value={selectedId}
                onChange={e=>{setSelectedId(e.target.value);invalidate()}}
                disabled={productsLoading||!products.length}
              >
                <option value="">{productsLoading?'Carregando produtos...':'Selecione um produto'}</option>
                {products.map(product=><option key={product.id} value={product.id}>
                  {product.name} · {product.sku}
                </option>)}
              </select>
            </div>
            <ScanLine size={22}/>
          </div>

          {selected&&<div className="sim-real-baseline">
            <div><span>Saldo atual</span><strong>{selected.currentStock}</strong></div>
            <div><span>Custo unitário</span><strong>R$ {selected.costPrice.toFixed(2).replace('.',',')}</strong></div>
          </div>}

          <div className="sim-input-grid">
            <label>Demanda média/dia
              <input type="number" min="0.01" step="0.01" value={averageDailyDemand} onChange={e=>{setAverageDailyDemand(e.target.value);invalidate()}}/>
            </label>
            <label>Lead time normal
              <input type="number" min="0" step="1" value={supplierLeadTimeDays} onChange={e=>{setSupplierLeadTimeDays(e.target.value);invalidate()}}/>
            </label>
            <label>Compra planejada
              <input type="number" min="0" step="0.001" value={plannedPurchase} onChange={e=>{setPlannedPurchase(e.target.value);invalidate()}}/>
            </label>
          </div>

          <label>Demanda aumenta <b>{demand}%</b><input type="range" min="0" max="80" value={demand} onChange={e=>{setDemand(+e.target.value);invalidate()}}/></label>
          <label>Atraso do fornecedor <b>{delay} dias</b><input type="range" min="0" max="14" value={delay} onChange={e=>{setDelay(+e.target.value);invalidate()}}/></label>

          <button className="simulate-btn" onClick={simulate} disabled={loading||productsLoading||!selected}>
            {loading?'Calculando...':'Simular cenário'}
          </button>
          <div className="note"><Sparkles size={18}/><p>Saldo e custo vêm do MySQL. Demanda e prazo são premissas do cenário. A IA apenas explica o resultado.</p></div>
        </div>

        <div className="result">
          {result
            ? <>
                <div className="risk-line"><span>Risco projetado</span><strong className={'risk '+String(result.risk).toLowerCase()}>{result.risk}</strong></div>
                <div className="api-status"><span className="status-dot api"></span>Resultado calculado pelo Spring Boot</div>
                <div className="coverage">{result.coverage}<small> dias de cobertura</small></div>
                <div className="result-grid">
                  <div><span>Ruptura estimada</span><b>{result.stockout}</b></div>
                  <div><span>Compra sugerida</span><b>{result.recommended} un.</b></div>
                  <div><span>Valor em risco</span><b>R$ {result.value.toFixed(2).replace('.',',')}</b></div>
                </div>
                <button className="ghost" onClick={explain} disabled={advisorLoading}>
                  <BrainCircuit size={18}/>{advisorLoading?'Analisando...':'Explicar esta decisão'}
                </button>
                {explanation&&<div className="advisor-box"><div><BrainCircuit size={17}/><strong>Assistente Nexo</strong><span>{advisorSource}</span></div><p>{explanation}</p></div>}
              </>
            : <div className="sim-empty-state">
                <TrendingUp size={28}/>
                <strong>{source==='offline'?'Simulação indisponível':'Configure o cenário'}</strong>
                <p>{source==='offline'
                  ? 'A API não respondeu. Nenhum cálculo local foi usado como substituto.'
                  : 'Selecione o produto, informe as premissas e execute a simulação.'}</p>
              </div>}

          {error&&<div className="error">{error}</div>}
        </div>
      </div>
    </section>
  </>;
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


function ProductsPanel(){
  const API_URL=import.meta.env.VITE_API_URL || 'https://nexo-estoque-api-production.up.railway.app';
  const [query,setQuery]=useState('');
  const [showForm,setShowForm]=useState(false);
  const [products,setProducts]=useState<ProductView[]>([]);
  const [source,setSource]=useState<'loading'|'api'|'offline'>('loading');
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
      setProducts([]);
      setSource('offline');
      setFeedback('API/MySQL indisponível. Nenhum dado local foi usado como substituto.');
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
      setSource('offline');
      setFeedback('API/MySQL indisponível. O produto não foi salvo.');
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
          {source==='loading'?'conectando...':source==='api'?'API + MySQL':'API indisponível'}
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
        <span>{source==='api'?'Cadastro será persistido pela Procedure MySQL.':'Aguarde a API voltar para salvar dados reais.'}</span>
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
  const API_URL=import.meta.env.VITE_API_URL || 'https://nexo-estoque-api-production.up.railway.app';
  const [products,setProducts]=useState<ProductView[]>([]);
  const [loading,setLoading]=useState(true);
  const [saving,setSaving]=useState(false);
  const [error,setError]=useState('');
  const [batchOptions,setBatchOptions]=useState<Array<{
    id:number;
    lotCode:string;
    expiresAt:string|null;
    quantity:number;
  }>>([]);
  const [loadingBatches,setLoadingBatches]=useState(false);
  const [form,setForm]=useState({
    productId:'',
    movementType:'ENTRY',
    quantity:'',
    reason:'',
    lotCode:'',
    batchId:'',
    expiresAt:'',
    unitCost:''
  });

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

  useEffect(()=>{
    const needsBatch=form.movementType==='RETURN'||form.movementType==='ADJUSTMENT';

    if(!needsBatch||!form.productId){
      setBatchOptions([]);
      return;
    }

    let active=true;
    setLoadingBatches(true);

    (async()=>{
      try{
        const response=await fetch(API_URL+'/api/v1/stock/batches?productId='+encodeURIComponent(form.productId));
        if(!response.ok) throw new Error();
        const data=await response.json();
        if(!active) return;
        setBatchOptions((Array.isArray(data)?data:[]).map((item:any)=>({
          id:Number(item.id),
          lotCode:String(item.lotCode||''),
          expiresAt:item.expiresAt||null,
          quantity:Number(item.quantity||0)
        })));
      }catch{
        if(active){
          setBatchOptions([]);
          setError('Não foi possível carregar os lotes deste produto.');
        }
      }finally{
        if(active) setLoadingBatches(false);
      }
    })();

    return ()=>{active=false};
  },[form.productId,form.movementType]);

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

    if(form.movementType==='ENTRY'&&!form.lotCode.trim()){
      setError('Informe o lote para registrar a entrada.');
      return;
    }

    if((form.movementType==='RETURN'||form.movementType==='ADJUSTMENT')&&!form.batchId){
      setError('Selecione o lote da movimentação.');
      return;
    }

    const quantity=form.movementType==='ADJUSTMENT'
      ? rawQuantity
      : Math.abs(rawQuantity);

    let endpoint=API_URL+'/api/v1/stock/movements';
    let payload:any={
      productId:Number(form.productId),
      movementType:form.movementType,
      quantity,
      reason:form.reason.trim()
    };

    if(form.movementType==='ENTRY'){
      endpoint=API_URL+'/api/v1/stock/batches/entry';
      payload={
        productId:Number(form.productId),
        lotCode:form.lotCode.trim(),
        expiresAt:form.expiresAt||null,
        quantity:Math.abs(rawQuantity),
        unitCost:Number(form.unitCost||0),
        reason:form.reason.trim()
      };
    }else if(form.movementType==='EXIT'){
      endpoint=API_URL+'/api/v1/stock/batches/exit-fefo';
      payload={
        productId:Number(form.productId),
        quantity:Math.abs(rawQuantity),
        reason:form.reason.trim()
      };
    }else if(form.movementType==='RETURN'){
      endpoint=API_URL+'/api/v1/stock/batches/return';
      payload={
        productId:Number(form.productId),
        batchId:Number(form.batchId),
        quantity:Math.abs(rawQuantity),
        reason:form.reason.trim()
      };
    }else if(form.movementType==='ADJUSTMENT'){
      endpoint=API_URL+'/api/v1/stock/batches/adjustment';
      payload={
        productId:Number(form.productId),
        batchId:Number(form.batchId),
        quantityDelta:rawQuantity,
        reason:form.reason.trim()
      };
    }

    setSaving(true);
    try{
      const response=await fetch(endpoint,{
        method:'POST',
        headers:{'Content-Type':'application/json'},
        body:JSON.stringify(payload)
      });
      const responsePayload=await response.json().catch(()=>null);
      if(!response.ok){
        const detail=responsePayload?.detail||responsePayload?.message||'Não foi possível registrar a movimentação.';
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
          <select value={form.productId} onChange={e=>setForm({...form,productId:e.target.value,batchId:''})} disabled={loading}>
            <option value="">{loading?'Carregando produtos...':'Selecione um produto'}</option>
            {products.map(p=><option key={p.id??p.sku} value={p.id}>{p.name} · {p.sku} · saldo {p.stock}</option>)}
          </select>
        </label>

        <div className="movement-grid">
          <label>Tipo
            <select value={form.movementType} onChange={e=>setForm({...form,movementType:e.target.value,batchId:''})}>
              <option value="ENTRY">Entrada por lote</option>
              <option value="EXIT">Saída FEFO</option>
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

        {form.movementType==='ENTRY'&&<>
          <div className="movement-grid">
            <label>Lote
              <input value={form.lotCode} onChange={e=>setForm({...form,lotCode:e.target.value})} placeholder="Ex.: DIP2609A"/>
            </label>
            <label>Validade
              <input type="date" value={form.expiresAt} onChange={e=>setForm({...form,expiresAt:e.target.value})}/>
            </label>
          </div>
          <label>Custo unitário
            <input type="number" min="0" step="0.01" value={form.unitCost} onChange={e=>setForm({...form,unitCost:e.target.value})} placeholder="Ex.: 6,85"/>
          </label>
          <div className="movement-hint fefo">A entrada atualiza o lote existente ou cria um novo lote, preservando validade, custo e rastreabilidade.</div>
        </>}

        {form.movementType==='EXIT'&&<div className="movement-hint fefo">
          O Nexo aplicará FEFO automaticamente: primeiro os lotes com validade mais próxima; lotes sem validade ficam por último.
        </div>}

        {(form.movementType==='RETURN'||form.movementType==='ADJUSTMENT')&&<>
          <label>Lote
            <select
              value={form.batchId}
              onChange={e=>setForm({...form,batchId:e.target.value})}
              disabled={!form.productId||loadingBatches}
            >
              <option value="">{loadingBatches?'Carregando lotes...':'Selecione o lote'}</option>
              {batchOptions.map(batch=><option key={batch.id} value={batch.id}>
                {batch.lotCode} · saldo {batch.quantity} · {batch.expiresAt?new Date(batch.expiresAt+'T12:00:00').toLocaleDateString('pt-BR'):'sem validade'}
              </option>)}
            </select>
          </label>
          <div className="movement-hint fefo">
            {form.movementType==='RETURN'
              ? 'A devolução retorna a quantidade ao lote selecionado e atualiza o saldo total na mesma transação.'
              : 'O ajuste altera o lote e o saldo total juntos. Valores negativos reduzem; positivos aumentam.'}
          </div>
        </>}

        <label>Motivo
          <textarea
            value={form.reason}
            onChange={e=>setForm({...form,reason:e.target.value})}
            placeholder="Ex.: recebimento do fornecedor, baixa por venda, correção após inventário"
            rows={3}
          />
        </label>

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
  const API_URL=import.meta.env.VITE_API_URL || 'https://nexo-estoque-api-production.up.railway.app';
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


type InventorySessionView={
  id:number;
  name:string;
  status:'OPEN'|'CLOSED'|'CANCELLED';
  startedAt?:string;
  closedAt?:string;
  countedItems:number;
  divergentItems:number;
};

type InventoryItemView={
  productId:number;
  sku:string;
  productName:string;
  countedQuantity:number|null;
  systemQuantitySnapshot:number|null;
  differenceQuantity:number|null;
  countedAt?:string;
  revealed:boolean;
};

function BlindInventoryPanel(){
  const API_URL=import.meta.env.VITE_API_URL || 'https://nexo-estoque-api-production.up.railway.app';
  const [sessions,setSessions]=useState<InventorySessionView[]>([]);
  const [selectedId,setSelectedId]=useState<number|null>(null);
  const [items,setItems]=useState<InventoryItemView[]>([]);
  const [drafts,setDrafts]=useState<Record<number,string>>({});
  const [newName,setNewName]=useState('');
  const [loading,setLoading]=useState(true);
  const [savingId,setSavingId]=useState<number|null>(null);
  const [creating,setCreating]=useState(false);
  const [closing,setClosing]=useState(false);
  const [error,setError]=useState('');
  const [feedback,setFeedback]=useState('');

  const selected=sessions.find(session=>session.id===selectedId)??null;
  const counted=items.filter(item=>item.countedQuantity!==null).length;
  const complete=items.length>0&&counted===items.length;

  async function readError(response:Response,fallback:string){
    const payload=await response.json().catch(()=>null);
    return payload?.detail||payload?.message||fallback;
  }

  async function loadSessions(preferredId?:number){
    const response=await fetch(API_URL+'/api/v1/inventory/blind');
    if(!response.ok) throw new Error(await readError(response,'Não foi possível carregar os inventários.'));
    const data=await response.json();
    const rows:InventorySessionView[]=(Array.isArray(data)?data:[]).map((session:any)=>({
      id:Number(session.id),
      name:String(session.name||'Inventário'),
      status:session.status,
      startedAt:session.startedAt||'',
      closedAt:session.closedAt||'',
      countedItems:Number(session.countedItems||0),
      divergentItems:Number(session.divergentItems||0)
    }));
    setSessions(rows);
    const desired=preferredId??selectedId??rows.find(row=>row.status==='OPEN')?.id??rows[0]?.id??null;
    if(desired!==null&&rows.some(row=>row.id===desired)) setSelectedId(desired);
    else setSelectedId(rows[0]?.id??null);
    return desired;
  }

  async function loadItems(sessionId:number){
    const response=await fetch(API_URL+`/api/v1/inventory/blind/${sessionId}/items`);
    if(!response.ok) throw new Error(await readError(response,'Não foi possível carregar os itens do inventário.'));
    const data=await response.json();
    const rows:InventoryItemView[]=(Array.isArray(data)?data:[]).map((item:any)=>({
      productId:Number(item.productId),
      sku:String(item.sku||''),
      productName:String(item.productName||'Produto'),
      countedQuantity:item.countedQuantity===null||item.countedQuantity===undefined?null:Number(item.countedQuantity),
      systemQuantitySnapshot:item.systemQuantitySnapshot===null||item.systemQuantitySnapshot===undefined?null:Number(item.systemQuantitySnapshot),
      differenceQuantity:item.differenceQuantity===null||item.differenceQuantity===undefined?null:Number(item.differenceQuantity),
      countedAt:item.countedAt||'',
      revealed:Boolean(item.revealed)
    }));
    setItems(rows);
    const next:Record<number,string>={};
    rows.forEach(item=>{next[item.productId]=item.countedQuantity===null?'':String(item.countedQuantity)});
    setDrafts(next);
  }

  useEffect(()=>{
    (async()=>{
      setLoading(true);
      setError('');
      try{
        const desired=await loadSessions();
        if(desired) await loadItems(desired);
      }catch(err){
        setError(err instanceof Error?err.message:'A API de inventário está indisponível.');
      }finally{
        setLoading(false);
      }
    })();
  },[]);

  useEffect(()=>{
    if(selectedId===null) {
      setItems([]);
      return;
    }
    (async()=>{
      setLoading(true);
      setError('');
      try{await loadItems(selectedId)}
      catch(err){setError(err instanceof Error?err.message:'Não foi possível abrir o inventário.')}
      finally{setLoading(false)}
    })();
  },[selectedId]);

  async function createSession(e:React.FormEvent){
    e.preventDefault();
    if(!newName.trim()) return;
    setCreating(true);
    setError('');
    setFeedback('');
    try{
      const response=await fetch(API_URL+'/api/v1/inventory/blind',{
        method:'POST',
        headers:{'Content-Type':'application/json'},
        body:JSON.stringify({name:newName.trim()})
      });
      if(!response.ok) throw new Error(await readError(response,'Não foi possível criar o inventário.'));
      const created=await response.json();
      setNewName('');
      await loadSessions(Number(created.id));
      setSelectedId(Number(created.id));
      setFeedback('Inventário aberto. O saldo do sistema ficará oculto até o fechamento.');
    }catch(err){
      setError(err instanceof Error?err.message:'Não foi possível criar o inventário.');
    }finally{
      setCreating(false);
    }
  }

  async function saveCount(productId:number){
    const raw=drafts[productId];
    const quantity=Number(raw);
    if(raw===''||!Number.isFinite(quantity)||quantity<0){
      setError('Informe uma contagem válida, igual ou maior que zero.');
      return;
    }
    if(!selectedId) return;

    setSavingId(productId);
    setError('');
    setFeedback('');
    try{
      const response=await fetch(API_URL+`/api/v1/inventory/blind/${selectedId}/counts`,{
        method:'PUT',
        headers:{'Content-Type':'application/json'},
        body:JSON.stringify({productId,countedQuantity:quantity})
      });
      if(!response.ok) throw new Error(await readError(response,'Não foi possível salvar a contagem.'));
      await Promise.all([loadItems(selectedId),loadSessions(selectedId)]);
      setFeedback('Contagem salva sem revelar o saldo do sistema.');
    }catch(err){
      setError(err instanceof Error?err.message:'Não foi possível salvar a contagem.');
    }finally{
      setSavingId(null);
    }
  }

  async function closeSession(){
    if(!selectedId||!complete) return;
    setClosing(true);
    setError('');
    setFeedback('');
    try{
      const response=await fetch(API_URL+`/api/v1/inventory/blind/${selectedId}/close`,{method:'POST'});
      if(!response.ok) throw new Error(await readError(response,'Não foi possível fechar o inventário.'));
      await loadSessions(selectedId);
      await loadItems(selectedId);
      setFeedback('Inventário fechado. As divergências foram reveladas para conferência.');
    }catch(err){
      setError(err instanceof Error?err.message:'Não foi possível fechar o inventário.');
    }finally{
      setClosing(false);
    }
  }

  return <>
    <header className="page-header">
      <div>
        <span className="eyebrow">CONFERÊNCIA SEM VIÉS</span>
        <h1>Inventário cego</h1>
        <p>Conte fisicamente sem visualizar o saldo esperado. A comparação aparece somente depois do fechamento.</p>
      </div>
    </header>

    <section className="blind-create">
      <form onSubmit={createSession}>
        <input value={newName} onChange={e=>setNewName(e.target.value)} placeholder="Nome do inventário, ex.: Contagem semanal · Corredor A"/>
        <button className="primary compact" disabled={creating||!newName.trim()}>{creating?'Abrindo...':'+ Abrir inventário'}</button>
      </form>
      <div className="blind-shield"><ShieldCheck size={17}/><span>Durante a contagem, o Nexo não envia o saldo do sistema para a tela.</span></div>
    </section>

    {error&&<div className="product-feedback warning">{error}</div>}
    {feedback&&<div className="product-feedback success">{feedback}</div>}

    <section className="blind-layout">
      <aside className="inventory-sessions">
        <div className="inventory-side-head"><strong>Sessões</strong><span>{sessions.length}</span></div>
        {sessions.map(session=><button
          type="button"
          key={session.id}
          className={session.id===selectedId?'inventory-session active':'inventory-session'}
          onClick={()=>setSelectedId(session.id)}
        >
          <div><strong>{session.name}</strong><small>{session.startedAt?new Date(session.startedAt).toLocaleString('pt-BR'):'—'}</small></div>
          <span className={'inventory-status '+session.status.toLowerCase()}>{session.status==='OPEN'?'Aberto':session.status==='CLOSED'?'Fechado':'Cancelado'}</span>
        </button>)}
        {!sessions.length&&!loading&&<div className="inventory-empty-small">Nenhum inventário criado.</div>}
      </aside>

      <div className="inventory-work">
        {selected
          ? <>
              <div className="inventory-work-head">
                <div>
                  <span className="eyebrow">{selected.status==='OPEN'?'CONTAGEM EM ANDAMENTO':'RESULTADO AUDITÁVEL'}</span>
                  <h2>{selected.name}</h2>
                  <p>{selected.status==='OPEN'
                    ? `${counted} de ${items.length} produtos contados`
                    : `${selected.countedItems} itens conferidos · ${selected.divergentItems} divergências`}</p>
                </div>
                {selected.status==='OPEN'&&<button className="close-inventory" disabled={!complete||closing} onClick={closeSession}>
                  {closing?'Fechando...':'Fechar e revelar diferenças'}
                </button>}
              </div>

              {selected.status==='OPEN'&&<div className="inventory-progress"><span style={{width:(items.length?counted/items.length*100:0)+'%'}}/></div>}

              <div className="blind-table-wrap">
                <table className="blind-table">
                  <thead>
                    <tr>
                      <th>Produto</th>
                      <th>Contado</th>
                      {selected.status==='CLOSED'&&<><th>Sistema</th><th>Diferença</th></>}
                      {selected.status==='OPEN'&&<th>Ação</th>}
                    </tr>
                  </thead>
                  <tbody>
                    {items.map(item=><tr key={item.productId}>
                      <td><strong>{item.productName}</strong><small>{item.sku}</small></td>
                      <td>
                        {selected.status==='OPEN'
                          ? <input
                              type="number"
                              min="0"
                              step="0.001"
                              value={drafts[item.productId]??''}
                              onChange={e=>setDrafts(current=>({...current,[item.productId]:e.target.value}))}
                              placeholder="Quantidade física"
                            />
                          : <b>{item.countedQuantity??'—'}</b>}
                      </td>
                      {selected.status==='CLOSED'&&<>
                        <td><b>{item.systemQuantitySnapshot??'—'}</b></td>
                        <td><span className={'difference '+((item.differenceQuantity??0)===0?'zero':(item.differenceQuantity??0)>0?'positive':'negative')}>
                          {(item.differenceQuantity??0)>0?'+':''}{item.differenceQuantity??'—'}
                        </span></td>
                      </>}
                      {selected.status==='OPEN'&&<td>
                        <button className="count-save" disabled={savingId===item.productId} onClick={()=>saveCount(item.productId)}>
                          {savingId===item.productId?'Salvando...':item.countedQuantity===null?'Salvar':'Atualizar'}
                        </button>
                      </td>}
                    </tr>)}
                    {!items.length&&!loading&&<tr><td colSpan={selected.status==='CLOSED'?4:3} className="empty-state">Nenhum produto ativo para contar.</td></tr>}
                  </tbody>
                </table>
              </div>
            </>
          : <div className="inventory-empty">
              <ClipboardCheck size={30}/>
              <strong>Abra um inventário para iniciar a contagem</strong>
              <span>O saldo esperado continuará oculto até o encerramento.</span>
            </div>}
      </div>
    </section>
  </>;
}


type BatchView={
  id:number;
  productId:number;
  sku:string;
  productName:string;
  lotCode:string;
  expiresAt:string|null;
  quantity:number;
  unitCost:number;
  receivedAt?:string;
  daysToExpiry:number|null;
  expiryStatus:'NO_EXPIRY'|'EXPIRED'|'CRITICAL'|'ATTENTION'|'OK';
  fefoPosition:number;
};

function BatchesPanel(){
  const API_URL=import.meta.env.VITE_API_URL || 'https://nexo-estoque-api-production.up.railway.app';
  const [items,setItems]=useState<BatchView[]>([]);
  const [query,setQuery]=useState('');
  const [status,setStatus]=useState<'all'|BatchView['expiryStatus']>('all');
  const [loading,setLoading]=useState(true);
  const [error,setError]=useState('');

  useEffect(()=>{
    (async()=>{
      setLoading(true);
      setError('');
      try{
        const response=await fetch(API_URL+'/api/v1/stock/batches');
        if(!response.ok) throw new Error('Não foi possível carregar os lotes.');
        const data=await response.json();
        setItems((Array.isArray(data)?data:[]).map((item:any)=>({
          id:Number(item.id),
          productId:Number(item.productId),
          sku:String(item.sku||''),
          productName:String(item.productName||'Produto'),
          lotCode:String(item.lotCode||''),
          expiresAt:item.expiresAt||null,
          quantity:Number(item.quantity||0),
          unitCost:Number(item.unitCost||0),
          receivedAt:item.receivedAt||'',
          daysToExpiry:item.daysToExpiry===null||item.daysToExpiry===undefined?null:Number(item.daysToExpiry),
          expiryStatus:item.expiryStatus,
          fefoPosition:Number(item.fefoPosition||0)
        })));
      }catch(err){
        setError(err instanceof Error?err.message:'A API de lotes está indisponível.');
      }finally{
        setLoading(false);
      }
    })();
  },[]);

  const filtered=items.filter(item=>{
    const matchesQuery=(item.productName+' '+item.sku+' '+item.lotCode).toLowerCase().includes(query.toLowerCase());
    const matchesStatus=status==='all'||item.expiryStatus===status;
    return matchesQuery&&matchesStatus;
  });

  const critical=items.filter(item=>item.expiryStatus==='CRITICAL'||item.expiryStatus==='EXPIRED');
  const expiringUnits=critical.reduce((sum,item)=>sum+item.quantity,0);
  const productsInFefo=new Set(items.map(item=>item.productId)).size;

  const statusLabel=(value:BatchView['expiryStatus'])=>({
    NO_EXPIRY:'Sem validade',
    EXPIRED:'Vencido',
    CRITICAL:'Até 30 dias',
    ATTENTION:'31–90 dias',
    OK:'Regular'
  }[value]);

  return <>
    <header className="page-header">
      <div>
        <span className="eyebrow">RASTREABILIDADE FEFO</span>
        <h1>Lotes & validade</h1>
        <p>Visualize a ordem real de consumo, vencimentos próximos e saldo disponível por lote.</p>
      </div>
    </header>

    <section className="batch-kpis">
      <article><span>Lotes ativos</span><strong>{items.length}</strong><small>com saldo disponível</small></article>
      <article><span>Produtos com lote</span><strong>{productsInFefo}</strong><small>gerenciados por FEFO</small></article>
      <article><span>Lotes críticos</span><strong>{critical.length}</strong><small>vencidos ou até 30 dias</small></article>
      <article><span>Unidades em atenção</span><strong>{expiringUnits.toFixed(3).replace(/\.000$/,'')}</strong><small>nos lotes críticos</small></article>
    </section>

    <section className="batch-toolbar">
      <input value={query} onChange={e=>setQuery(e.target.value)} placeholder="Buscar produto, SKU ou lote"/>
      <select value={status} onChange={e=>setStatus(e.target.value as typeof status)}>
        <option value="all">Todas as situações</option>
        <option value="EXPIRED">Vencidos</option>
        <option value="CRITICAL">Até 30 dias</option>
        <option value="ATTENTION">31–90 dias</option>
        <option value="OK">Regulares</option>
        <option value="NO_EXPIRY">Sem validade</option>
      </select>
    </section>

    {error&&<div className="product-feedback warning">{error}</div>}

    <section className="batch-table-wrap">
      <table className="batch-table">
        <thead><tr><th>Produto</th><th>Lote</th><th>Validade</th><th>Saldo</th><th>Custo</th><th>FEFO</th><th>Situação</th></tr></thead>
        <tbody>
          {filtered.map(item=><tr key={item.id}>
            <td><strong>{item.productName}</strong><small>{item.sku}</small></td>
            <td><b>{item.lotCode}</b></td>
            <td>
              <strong>{item.expiresAt?new Date(item.expiresAt+'T12:00:00').toLocaleDateString('pt-BR'):'Sem validade'}</strong>
              <small>{item.daysToExpiry===null?'—':item.daysToExpiry<0?`${Math.abs(item.daysToExpiry)} dias vencido`:`${item.daysToExpiry} dias`}</small>
            </td>
            <td><b>{item.quantity}</b></td>
            <td>R$ {item.unitCost.toFixed(2).replace('.',',')}</td>
            <td>{item.fefoPosition===1?<span className="fefo-next">Próximo</span>:'#'+item.fefoPosition}</td>
            <td><span className={'expiry-pill '+item.expiryStatus.toLowerCase()}>{statusLabel(item.expiryStatus)}</span></td>
          </tr>)}
          {!filtered.length&&!loading&&<tr><td colSpan={7} className="empty-state">Nenhum lote encontrado.</td></tr>}
          {loading&&<tr><td colSpan={7} className="empty-state">Carregando lotes...</td></tr>}
        </tbody>
      </table>
    </section>
  </>;
}



type AdvisorMessage={
  id:number;
  role:'user'|'assistant';
  text:string;
  source?:string;
};

function AdvisorPanel(){
  const API_URL=import.meta.env.VITE_API_URL || 'https://nexo-estoque-api-production.up.railway.app';
  const [question,setQuestion]=useState('');
  const [loading,setLoading]=useState(false);
  const [messages,setMessages]=useState<AdvisorMessage[]>([
    {
      id:1,
      role:'assistant',
      text:'Consulte o estoque em linguagem natural. Posso analisar saldos, itens abaixo do mínimo, lotes, validade, FEFO e inventários usando a base operacional do Nexo.',
      source:'contexto operacional'
    }
  ]);

  const suggestions=[
    'Quais produtos estão abaixo do estoque mínimo?',
    'Quais lotes vencem em até 30 dias?',
    'Como está o inventário?',
    'Faça um resumo do estoque.'
  ];

  async function ask(rawQuestion:string){
    const clean=rawQuestion.trim();
    if(!clean||loading) return;

    setMessages(current=>[
      ...current,
      {id:Date.now(),role:'user',text:clean}
    ]);
    setQuestion('');
    setLoading(true);

    try{
      const response=await fetch(API_URL+'/api/v1/advisor/chat',{
        method:'POST',
        headers:{'Content-Type':'application/json'},
        body:JSON.stringify({question:clean})
      });
      const data=await response.json().catch(()=>null);
      if(!response.ok) throw new Error(data?.message||'Assistente indisponível');

      const source=data?.source==='openai'
        ? 'OpenAI · '+String(data?.model||'modelo configurado')
        : data?.source==='rules'
          ? 'motor determinístico · MySQL'
          : 'base operacional indisponível';

      setMessages(current=>[
        ...current,
        {
          id:Date.now()+1,
          role:'assistant',
          text:String(data?.answer||'Não foi possível gerar uma resposta.'),
          source
        }
      ]);
    }catch{
      setMessages(current=>[
        ...current,
        {
          id:Date.now()+1,
          role:'assistant',
          text:'Não consegui consultar o contexto operacional agora. Confirme o indicador API + MySQL e tente novamente.',
          source:'falha de conexão'
        }
      ]);
    }finally{
      setLoading(false);
    }
  }

  function submit(e:React.FormEvent){
    e.preventDefault();
    void ask(question);
  }

  return <>
    <header className="page-header advisor-page-header">
      <div>
        <span className="eyebrow">ASSISTÊNCIA OPERACIONAL</span>
        <h1>Assistente Nexo</h1>
        <p>Pergunte sobre a operação. As respostas usam o snapshot atual do estoque e não executam movimentações.</p>
      </div>
      <span className="audit"><ShieldCheck size={16}/> somente leitura</span>
    </header>

    <section className="advisor-workspace">
      <section className="advisor-context">
        <div>
          <BrainCircuit size={22}/>
          <span className="eyebrow">CONTEXTO DISPONÍVEL</span>
          <h2>O que o assistente consulta</h2>
          <p>Produtos, saldos, estoque mínimo, lotes ativos, validade, posição FEFO e sessões de inventário.</p>
        </div>
        <div className="advisor-boundaries">
          <strong>Limites operacionais</strong>
          <span>Não cria entradas ou saídas.</span>
          <span>Não altera quantidades.</span>
          <span>Não inventa fornecedores ou prazos.</span>
        </div>
      </section>

      <div className="advisor-chat">
        <div className="advisor-messages">
          {messages.map(message=><article key={message.id} className={'advisor-message '+message.role}>
            <div className="advisor-message-role">
              {message.role==='assistant'?<BrainCircuit size={16}/>:<span>Você</span>}
              {message.role==='assistant'&&<strong>Nexo</strong>}
            </div>
            <p>{message.text}</p>
            {message.source&&<small>{message.source}</small>}
          </article>)}
          {loading&&<article className="advisor-message assistant loading">
            <div className="advisor-message-role"><BrainCircuit size={16}/><strong>Nexo</strong></div>
            <p>Consultando o estoque atual...</p>
          </article>}
        </div>

        <div className="advisor-suggestions">
          {suggestions.map(item=><button key={item} type="button" onClick={()=>void ask(item)} disabled={loading}>{item}</button>)}
        </div>

        <form className="advisor-composer" onSubmit={submit}>
          <textarea
            value={question}
            onChange={e=>setQuestion(e.target.value)}
            placeholder="Ex.: Qual item precisa de reposição primeiro?"
            maxLength={600}
            rows={3}
            disabled={loading}
          />
          <div>
            <small>{question.length}/600</small>
            <button className="primary compact" disabled={loading||!question.trim()}>
              {loading?'Consultando...':'Perguntar'}
            </button>
          </div>
        </form>
      </div>
    </section>
  </>;
}

function SystemReadiness(){
  const API_URL=import.meta.env.VITE_API_URL || 'https://nexo-estoque-api-production.up.railway.app';
  const [state,setState]=useState<'loading'|'ready'|'degraded'|'offline'>('loading');
  const [latency,setLatency]=useState<number|null>(null);

  useEffect(()=>{
    let active=true;

    async function check(){
      try{
        const response=await fetch(API_URL+'/api/v1/system/readiness');
        if(!response.ok) throw new Error();
        const data=await response.json();
        if(!active) return;
        setState(data.database==='ready'?'ready':'degraded');
        setLatency(Number.isFinite(Number(data.databaseLatencyMs))?Number(data.databaseLatencyMs):null);
      }catch{
        if(active){
          setState('offline');
          setLatency(null);
        }
      }
    }

    void check();
    const timer=window.setInterval(check,30000);
    return ()=>{
      active=false;
      window.clearInterval(timer);
    };
  },[]);

  const text=state==='ready'
    ? 'API + MySQL operacionais'
    : state==='degraded'
      ? 'API online · MySQL desconectado'
      : state==='offline'
        ? 'API indisponível'
        : 'Verificando infraestrutura...';

  return <div className={'system-readiness '+state}>
    <span className="readiness-dot"/>
    <strong>{text}</strong>
    {latency!==null&&state==='ready'&&<small>{latency} ms</small>}
  </div>;
}

function Dashboard({logout,theme,onToggleTheme}:{logout:()=>void;theme:Theme;onToggleTheme:()=>void}){
  const API_URL=import.meta.env.VITE_API_URL || 'https://nexo-estoque-api-production.up.railway.app';
  const [page,setPage]=useState<'dashboard'|'products'|'batches'|'inventory'|'simulator'|'assistant'>('dashboard');
  const [showMovement,setShowMovement]=useState(false);
  const [movementRefresh,setMovementRefresh]=useState(0);
  const [dashboardLoading,setDashboardLoading]=useState(true);
  const [dashboardError,setDashboardError]=useState('');
  const [dashboard,setDashboard]=useState<{
    totalStock:number;
    activeProducts:number;
    criticalProducts:number;
    outOfStockProducts:number;
    expiryRiskBatches:number;
    expiryRiskValue:number;
    inventoryAccuracy:number|null;
    inventoryDivergences:number|null;
    attention:Array<{
      key:string;
      kind:'danger'|'warning'|'neutral';
      icon:'stock'|'expiry'|'inventory';
      title:string;
      description:string;
      value:string;
    }>;
  }|null>(null);

  useEffect(()=>{
    if(page!=='dashboard') return;

    let active=true;

    async function loadDashboard(){
      setDashboardLoading(true);
      setDashboardError('');

      try{
        const [productsResponse,batchesResponse,inventoryResponse]=await Promise.all([
          fetch(API_URL+'/api/v1/products'),
          fetch(API_URL+'/api/v1/stock/batches'),
          fetch(API_URL+'/api/v1/inventory/blind')
        ]);

        if(!productsResponse.ok||!batchesResponse.ok||!inventoryResponse.ok){
          throw new Error('Não foi possível carregar os indicadores operacionais.');
        }

        const [productsRaw,batchesRaw,sessionsRaw]=await Promise.all([
          productsResponse.json(),
          batchesResponse.json(),
          inventoryResponse.json()
        ]);

        const products=(Array.isArray(productsRaw)?productsRaw:[]).filter((item:any)=>item.active!==false);
        const batches=(Array.isArray(batchesRaw)?batchesRaw:[]).filter((item:any)=>Number(item.quantity||0)>0);
        const sessions=Array.isArray(sessionsRaw)?sessionsRaw:[];

        const totalStock=products.reduce((sum:number,item:any)=>sum+Number(item.currentStock||0),0);
        const critical=products
          .filter((item:any)=>Number(item.currentStock||0)<Number(item.minimumStock||0))
          .sort((a:any,b:any)=>{
            const deficitA=Number(a.minimumStock||0)-Number(a.currentStock||0);
            const deficitB=Number(b.minimumStock||0)-Number(b.currentStock||0);
            return deficitB-deficitA;
          });
        const outOfStock=products.filter((item:any)=>Number(item.currentStock||0)<=0);

        const expiryRisk=batches
          .filter((item:any)=>item.expiresAt&&Number(item.daysToExpiry)<=30)
          .sort((a:any,b:any)=>Number(a.daysToExpiry)-Number(b.daysToExpiry));
        const expiryRiskValue=expiryRisk.reduce(
          (sum:number,item:any)=>sum+(Number(item.quantity||0)*Number(item.unitCost||0)),
          0
        );

        const latestClosed=sessions
          .filter((item:any)=>item.status==='CLOSED')
          .sort((a:any,b:any)=>new Date(b.closedAt||b.startedAt||0).getTime()-new Date(a.closedAt||a.startedAt||0).getTime())[0];

        const countedItems=Number(latestClosed?.countedItems||0);
        const divergentItems=Number(latestClosed?.divergentItems||0);
        const inventoryAccuracy=countedItems>0
          ? Math.max(0,((countedItems-divergentItems)/countedItems)*100)
          : null;

        const openInventory=sessions
          .filter((item:any)=>item.status==='OPEN')
          .sort((a:any,b:any)=>new Date(a.startedAt||0).getTime()-new Date(b.startedAt||0).getTime())[0];

        const attention:Array<{
          key:string;
          kind:'danger'|'warning'|'neutral';
          icon:'stock'|'expiry'|'inventory';
          title:string;
          description:string;
          value:string;
        }>=[];

        if(critical[0]){
          const product=critical[0];
          const stock=Number(product.currentStock||0);
          const minimum=Number(product.minimumStock||0);
          const deficit=Math.max(0,minimum-stock);
          attention.push({
            key:'stock-'+product.id,
            kind:'danger',
            icon:'stock',
            title:String(product.name||'Produto crítico'),
            description:`Saldo ${formatQuantity(stock)} · mínimo ${formatQuantity(minimum)}`,
            value:stock<=0?'sem estoque':`${formatQuantity(deficit)} abaixo`
          });
        }

        if(expiryRisk[0]){
          const batch=expiryRisk[0];
          const days=Number(batch.daysToExpiry);
          attention.push({
            key:'expiry-'+batch.id,
            kind:days<0?'danger':'warning',
            icon:'expiry',
            title:String(batch.productName||'Lote em risco'),
            description:`Lote ${batch.lotCode||'—'} · ${formatQuantity(Number(batch.quantity||0))} un.`,
            value:days<0?`${Math.abs(days)} d vencido`:days===0?'vence hoje':`${days} dias`
          });
        }

        if(openInventory){
          const counted=Number(openInventory.countedItems||0);
          const pending=Math.max(0,products.length-counted);
          attention.push({
            key:'inventory-'+openInventory.id,
            kind:'neutral',
            icon:'inventory',
            title:String(openInventory.name||'Inventário aberto'),
            description:`${counted} de ${products.length} produtos contados`,
            value:`${pending} pendentes`
          });
        }

        if(active){
          setDashboard({
            totalStock,
            activeProducts:products.length,
            criticalProducts:critical.length,
            outOfStockProducts:outOfStock.length,
            expiryRiskBatches:expiryRisk.length,
            expiryRiskValue,
            inventoryAccuracy,
            inventoryDivergences:latestClosed?divergentItems:null,
            attention
          });
        }
      }catch(err){
        if(active){
          setDashboard(null);
          setDashboardError(err instanceof Error?err.message:'Indicadores indisponíveis.');
        }
      }finally{
        if(active) setDashboardLoading(false);
      }
    }

    void loadDashboard();
    return ()=>{active=false};
  },[page,movementRefresh]);

  function formatQuantity(value:number){
    return new Intl.NumberFormat('pt-BR',{maximumFractionDigits:3}).format(value);
  }

  function formatMoney(value:number){
    return new Intl.NumberFormat('pt-BR',{style:'currency',currency:'BRL'}).format(value);
  }

  const actionCount=dashboard
    ? dashboard.criticalProducts+dashboard.expiryRiskBatches+dashboard.attention.filter(item=>item.icon==='inventory').length
    : 0;

  const cards=[
    {
      title:'Unidades em estoque',
      value:dashboard?formatQuantity(dashboard.totalStock):'—',
      Icon:Boxes,
      detail:dashboard?`${dashboard.activeProducts} produtos ativos`:(dashboardLoading?'carregando...':'indisponível')
    },
    {
      title:'Estoque crítico',
      value:dashboard?String(dashboard.criticalProducts):'—',
      Icon:AlertTriangle,
      detail:dashboard?`${dashboard.outOfStockProducts} sem estoque`:(dashboardLoading?'carregando...':'indisponível')
    },
    {
      title:'Risco de validade',
      value:dashboard?String(dashboard.expiryRiskBatches):'—',
      Icon:PackageSearch,
      detail:dashboard?formatMoney(dashboard.expiryRiskValue):(dashboardLoading?'carregando...':'indisponível')
    },
    {
      title:'Precisão inventário',
      value:dashboard?.inventoryAccuracy!=null?`${dashboard.inventoryAccuracy.toFixed(1).replace('.',',')}%`:'—',
      Icon:ClipboardCheck,
      detail:dashboard?.inventoryAccuracy!=null
        ? `${dashboard.inventoryDivergences||0} divergências no último inventário`
        : (dashboardLoading?'carregando...':'sem inventário fechado')
    }
  ];

  const attentionIcon=(icon:'stock'|'expiry'|'inventory')=>{
    if(icon==='stock') return <AlertTriangle/>;
    if(icon==='expiry') return <PackageSearch/>;
    return <ClipboardCheck/>;
  };

  return <div className="app-shell">
    <aside>
      <div className="brand sidebar-brand">
        <BrandImage theme={theme} className="sidebar-logo-full" alt="Nexo" />
        <img src="/nexo-symbol.png" alt="Nexo" className="sidebar-logo-symbol" />
      </div>
      <nav>
        <a className={page==='dashboard'?'active':''} onClick={()=>setPage('dashboard')}><LayoutDashboard size={19}/> Visão geral</a>
        <a className={page==='products'?'active':''} onClick={()=>setPage('products')}><Boxes size={19}/> Produtos</a>
        <a className={page==='batches'?'active':''} onClick={()=>setPage('batches')}><PackageSearch size={19}/> Lotes & validade</a>
        <a className={page==='inventory'?'active':''} onClick={()=>setPage('inventory')}><ClipboardCheck size={19}/> Inventário cego</a>
        <a className={page==='simulator'?'active':''} onClick={()=>setPage('simulator')}><TrendingUp size={19}/> Simulador</a>
        <a className={page==='assistant'?'active':''} onClick={()=>setPage('assistant')}><BrainCircuit size={19}/> Assistente</a>
      </nav>
      <div className="sidebar-bottom"><ThemeToggle theme={theme} onToggle={onToggleTheme}/><button className="logout" onClick={logout}><LogOut size={18}/> Sair</button></div>
    </aside>
    <main className="workspace">
      <SystemReadiness/>
      {page==='products'
        ? <ProductsPanel/>
        : page==='batches'
          ? <BatchesPanel/>
          : page==='inventory'
            ? <BlindInventoryPanel/>
            : page==='simulator'
              ? <Simulator/>
              : page==='assistant'
                ? <AdvisorPanel/>
                : <>
          <header>
            <div>
              <span className="eyebrow">NEXO ESTOQUE</span>
              <h1>Boa tarde, administrador.</h1>
              <p>{dashboard
                ? actionCount>0
                  ? `Há ${actionCount} alertas operacionais calculados com dados atuais do estoque.`
                  : 'Nenhum alerta operacional exige ação imediata neste momento.'
                : dashboardLoading
                  ? 'Carregando a situação real do estoque...'
                  : 'Os indicadores operacionais estão temporariamente indisponíveis.'}</p>
            </div>
            <button className="new-action" onClick={()=>setShowMovement(true)}>+ Nova movimentação</button>
          </header>

          {dashboardError&&<div className="product-feedback warning">{dashboardError}</div>}

          <section className="cards">
            {cards.map(({title,value,Icon,detail})=><article className="metric" key={title}><div className="metric-top"><span>{title}</span><Icon size={20}/></div><strong>{value}</strong><small>{detail}</small></article>)}
          </section>

          <section className="attention">
            <div className="section-head"><div><span className="eyebrow">PRIORIDADE DO DIA</span><h2>O que precisa da sua atenção</h2></div></div>
            {dashboard?.attention.length
              ? <div className="attention-grid">
                  {dashboard.attention.map(item=><article className={'action-card '+(item.kind==='danger'?'danger':item.kind==='warning'?'warning':'')} key={item.key}>
                    <div className="icon">{attentionIcon(item.icon)}</div>
                    <div><strong>{item.title}</strong><span>{item.description}</span></div>
                    <b>{item.value}</b>
                  </article>)}
                </div>
              : <div className="dashboard-empty">
                  {dashboardLoading?'Calculando prioridades...':'Nenhuma prioridade crítica encontrada com os dados atuais.'}
                </div>}
          </section>

          <RecentMovements refreshKey={movementRefresh}/>
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
