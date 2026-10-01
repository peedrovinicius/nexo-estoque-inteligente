import { lazy, Suspense, useEffect, useRef, useState } from 'react';
import { AlertTriangle, ArrowRightLeft, Boxes, BrainCircuit, Camera, ChevronRight, ClipboardCheck, Download, LayoutDashboard, LogOut, MapPin, Moon, PackageSearch, ScanLine, ShieldAlert, ShieldCheck, ShoppingCart, Sparkles, Sun, TrendingUp, Truck } from 'lucide-react';
import { apiFetch, clearAuthSession, isReadOnlySession, newIdempotencyKey, readAuthSession, saveAuthSession, type AuthRole, type AuthSession } from './auth';

const InventoryIntelligencePanel=lazy(()=>import('./InventoryIntelligencePanel'));
const TraceabilityPanel=lazy(()=>import('./TraceabilityPanel'));
const ActionCenterPanel=lazy(()=>import('./ActionCenterPanel'));
const DemandPlanningPanel=lazy(()=>import('./DemandPlanningPanel'));
const GovernancePanel=lazy(()=>import('./GovernancePanel'));
const QualityControlPanel=lazy(()=>import('./QualityControlPanel'));

const DEMO_USER='demo';
const DEMO_PASSWORD='Nexo@2026';

function productContent(data:any):any[]{
  if(Array.isArray(data)) return data;
  return Array.isArray(data?.content)?data.content:[];
}

async function apiErrorMessage(response:Response,fallback:string){
  const data=await response.json().catch(()=>null);
  if(data?.fieldErrors && typeof data.fieldErrors==='object'){
    const first=Object.values(data.fieldErrors)[0];
    if(typeof first==='string'&&first) return first;
  }
  return String(data?.message||data?.detail||fallback);
}


type Theme='light'|'dark';

function BarcodeScanner({
  onDetected,
  onClose
}:{onDetected:(value:string)=>void;onClose:()=>void}){
  const videoRef=useRef<HTMLVideoElement|null>(null);
  const [error,setError]=useState('');

  useEffect(()=>{
    let stream:MediaStream|null=null;
    let stopped=false;
    let timer:number|undefined;

    async function start(){
      try{
        const Detector=(window as any).BarcodeDetector;
        if(!Detector){
          setError('Leitura automática não é suportada neste navegador. Use a busca manual pelo código.');
          return;
        }

        stream=await navigator.mediaDevices.getUserMedia({
          video:{facingMode:{ideal:'environment'}},
          audio:false
        });

        const video=videoRef.current;
        if(!video) return;
        video.srcObject=stream;
        await video.play();

        const supported=Detector.getSupportedFormats
          ? await Detector.getSupportedFormats()
          : null;
        const wanted=['ean_13','ean_8','upc_a','upc_e','code_128','qr_code'];
        const formats=Array.isArray(supported)
          ? wanted.filter(format=>supported.includes(format))
          : wanted;
        const detector=formats.length?new Detector({formats}):new Detector();

        const scan=async()=>{
          if(stopped||!videoRef.current) return;
          try{
            const codes=await detector.detect(videoRef.current);
            const value=String(codes?.[0]?.rawValue||'').trim();
            if(value){
              stopped=true;
              onDetected(value);
              onClose();
              return;
            }
          }catch{
            // A câmera pode ainda estar ajustando foco/exposição.
          }
          timer=window.setTimeout(()=>void scan(),320);
        };

        void scan();
      }catch(err){
        setError(err instanceof Error?err.message:'Não foi possível acessar a câmera.');
      }
    }

    void start();

    return ()=>{
      stopped=true;
      if(timer!==undefined) window.clearTimeout(timer);
      stream?.getTracks().forEach(track=>track.stop());
    };
  },[]);

  return <div className="barcode-overlay" role="dialog" aria-modal="true" aria-label="Leitor de código de barras">
    <div className="barcode-card">
      <div className="form-title">
        <div><span className="eyebrow">CÂMERA</span><h2>Ler código de barras</h2></div>
        <button type="button" onClick={onClose}>Fechar</button>
      </div>
      <div className="barcode-camera">
        <video ref={videoRef} playsInline muted/>
        <div className="barcode-guide"><span/></div>
      </div>
      {error
        ? <div className="product-feedback warning" role="alert">{error}</div>
        : <p className="barcode-hint">Aponte a câmera para EAN, UPC ou Code 128. A leitura é automática.</p>}
    </div>
  </div>;
}

function BrandImage({
  theme,
  className,
  alt
}:{theme:Theme;className:string;alt:string}){
  if(theme==='light' && className.includes('login-logo')){
    return <span className="login-logo-wordmark" role="img" aria-label={alt}>
      <img
        src="/nexo-logo-original.png"
        alt=""
        className={className+' login-logo-base'}
        draggable={false}
        decoding="async"
      />
      <img
        src="/nexo-logo-original.png"
        alt=""
        aria-hidden="true"
        className={className+' login-logo-blackword'}
        draggable={false}
        decoding="async"
      />
      <img
        src="/nexo-logo-original.png"
        alt=""
        aria-hidden="true"
        className={className+' login-logo-blacktagline'}
        draggable={false}
        decoding="async"
      />
    </span>;
  }

  return <img
    src="/nexo-logo-original.png"
    alt={alt}
    className={className}
    draggable={false}
    decoding="async"
  />;
}

function DeferredPanel({children}:{children:React.ReactNode}){
  return <Suspense fallback={<div className="deferred-panel-loading" role="status" aria-live="polite"><span/> Carregando módulo...</div>}>
    {children}
  </Suspense>;
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


function PublicHome({
  theme,
  onToggleTheme,
  onLogin
}:{theme:Theme;onToggleTheme:()=>void;onLogin:()=>void}){
  return <main className="public-home">
    <header className="public-nav">
      <a className="public-brand-text" href="#inicio" aria-label="Nexo, início">Nexo</a>
      <nav className="public-nav-links" aria-label="Navegação da apresentação">
        <a href="#projeto">Projeto</a>
        <a href="#recursos">Recursos</a>
        <a href="#tecnologia">Tecnologia</a>
      </nav>
      <div className="public-nav-actions">
        <ThemeToggle theme={theme} onToggle={onToggleTheme} compact />
        <button type="button" className="public-login-button" onClick={onLogin}>Login</button>
      </div>
    </header>

    <section id="inicio" className="public-hero">
      <div className="public-hero-copy">
        <span className="public-kicker">GESTÃO DE ESTOQUE · RASTREABILIDADE · DECISÃO</span>
        <h1>Veja o estoque.<br/><span>Entenda o que ele está dizendo.</span></h1>
        <p>O Nexo reúne operação, validade, compras, inventário e rastreabilidade em um sistema único. A proposta é simples: mostrar o estado real do estoque e transformar sinais operacionais em decisões claras.</p>
        <div className="public-hero-actions">
          <button type="button" className="public-primary" onClick={onLogin}>Acessar demonstração <ChevronRight size={17}/></button>
          <a className="public-secondary" href="#projeto">Entender o projeto</a>
        </div>
      </div>

      <div className="public-flow" aria-label="Fluxo operacional do Nexo">
        <div className="public-flow-head"><span>FLUXO OPERACIONAL</span><b>NEXO</b></div>
        <ol>
          <li><span>01</span><div><strong>Receber</strong><small>entrada, lote, custo e posição</small></div></li>
          <li><span>02</span><div><strong>Rastrear</strong><small>saldo, validade e movimentações</small></div></li>
          <li><span>03</span><div><strong>Interpretar</strong><small>ruptura, cobertura e exposição</small></div></li>
          <li><span>04</span><div><strong>Decidir</strong><small>comprar, priorizar, transferir ou conter</small></div></li>
        </ol>
      </div>
    </section>

    <section id="projeto" className="public-story">
      <div className="public-story-index">01</div>
      <div className="public-story-content">
        <span className="public-kicker">O PROJETO</span>
        <h2>Não é um CRUD de produtos. É um sistema para acompanhar o ciclo do estoque.</h2>
        <div className="public-story-columns">
          <p>O Nexo foi desenvolvido para tratar estoque como uma operação contínua. O sistema acompanha recebimento, armazenamento, lotes, validade, transferências, inventário, compras, planejamento e rastreabilidade.</p>
          <p>As regras críticas são calculadas pelo backend e permanecem auditáveis. A camada de IA entra apenas para explicar cenários e apoiar a leitura dos dados, sem substituir a lógica operacional.</p>
        </div>
      </div>
    </section>

    <section id="recursos" className="public-features">
      <div className="public-section-heading">
        <span className="public-kicker">RECURSOS</span>
        <h2>O que existe de verdade no sistema.</h2>
      </div>

      <div className="public-feature-row">
        <span className="public-feature-number">01</span>
        <div className="public-feature-title"><Boxes size={19}/><strong>Operação de estoque</strong></div>
        <p>Produtos, saldo, lotes, validade, FEFO, depósitos, posições físicas, transferências, recebimento parcial e inventário cego.</p>
      </div>

      <div className="public-feature-row">
        <span className="public-feature-number">02</span>
        <div className="public-feature-title"><TrendingUp size={19}/><strong>Inteligência operacional</strong></div>
        <p>Estoque crítico, cobertura, curva ABC, capital imobilizado, risco de validade, previsão de demanda, reposição e central de ações.</p>
      </div>

      <div className="public-feature-row">
        <span className="public-feature-number">03</span>
        <div className="public-feature-title"><ShieldCheck size={19}/><strong>Controle e governança</strong></div>
        <p>Auditoria, rastreabilidade por lote, qualidade, quarentena, recall, exceções operacionais, SLA e histórico de decisões.</p>
      </div>

      <div className="public-feature-row">
        <span className="public-feature-number">04</span>
        <div className="public-feature-title"><BrainCircuit size={19}/><strong>Assistência por IA</strong></div>
        <p>Explicações sobre cenários e resultados já calculados pelo sistema, preservando a separação entre cálculo determinístico e interpretação.</p>
      </div>
    </section>

    <section id="tecnologia" className="public-tech">
      <div className="public-section-heading">
        <span className="public-kicker">TECNOLOGIA</span>
        <h2>Aplicação full stack, banco relacional e entrega contínua.</h2>
      </div>
      <div className="public-tech-grid">
        <div><span>01 / BACKEND</span><strong>Java 21<br/>Spring Boot</strong></div>
        <div><span>02 / FRONTEND</span><strong>React<br/>TypeScript + Vite</strong></div>
        <div><span>03 / DADOS</span><strong>MySQL<br/>SQL + procedures</strong></div>
        <div><span>04 / ENTREGA</span><strong>GitHub Actions<br/>Railway</strong></div>
      </div>
    </section>

    <section className="public-demo">
      <div>
        <span className="public-kicker">DEMONSTRAÇÃO</span>
        <h2>O projeto está online.</h2>
        <p>O acesso público usa um perfil somente leitura. Assim é possível percorrer as telas e entender o produto sem alterar os dados operacionais.</p>
      </div>
      <button type="button" className="public-primary public-demo-button" onClick={onLogin}>Ir para o login <ChevronRight size={17}/></button>
    </section>

    <footer className="public-footer">
      <strong>Nexo</strong>
      <span>Sistema inteligente de gestão de estoque</span>
    </footer>
  </main>;
}

function Login({onLogin,onBack,theme,onToggleTheme}:{onLogin:()=>void;onBack:()=>void;theme:Theme;onToggleTheme:()=>void}) {
  const API_URL=import.meta.env.VITE_API_URL || 'https://nexo-estoque-api-production.up.railway.app';
  const [user,setUser]=useState('');
  const [password,setPassword]=useState('');
  const [error,setError]=useState('');
  const [loading,setLoading]=useState(false);

  async function submit(e:React.FormEvent){
    e.preventDefault();
    setError('');
    setLoading(true);

    try{
      const authorization='Basic '+btoa(user+':'+password);
      const response=await fetch(API_URL+'/api/v1/auth/login',{
        method:'POST',
        headers:{Authorization:authorization}
      });

      if(!response.ok) throw new Error('Usuário ou senha inválidos.');
      const data=await response.json();
      const role=String(data.role||'VIEWER') as AuthRole;

      saveAuthSession({
        username:String(data.username||user),
        role,
        authorization
      } satisfies AuthSession);
      onLogin();
    }catch(err){
      setError(err instanceof Error?err.message:'Não foi possível autenticar.');
    }finally{
      setLoading(false);
    }
  }
  return <main className="login-shell">
    <div className="login-theme-toggle"><ThemeToggle theme={theme} onToggle={onToggleTheme} compact /></div>
    <section className="login-copy">
      <div className="brand official-brand">
        <BrandImage
          theme={theme}
          className="brand-logo-full"
          alt="Nexo — Sistema inteligente de gestão de estoque"
        />
      </div>
      <div className="hero">
        <span className="eyebrow">GESTÃO INTELIGENTE DE ESTOQUE</span>
        <h1>Estoque claro.<br/><span>Decisões melhores.</span></h1>
        <p>Uma visão única para operar estoque, validade, compras e rastreabilidade com precisão, contexto e menos ruído.</p>
        <div className="hero-feature-grid">
          <div><Boxes size={19}/><span>Estoque em tempo real</span></div>
          <div><AlertTriangle size={19}/><span>Alertas operacionais</span></div>
          <div><PackageSearch size={19}/><span>FEFO e validade</span></div>
          <div><BrainCircuit size={19}/><span>Assistência inteligente</span></div>
        </div>
        <div className="trust"><span><ShieldCheck size={18}/> decisões auditáveis</span><span><ScanLine size={18}/> rastreabilidade por lote</span></div>
      </div>
    </section>
    <section className="login-side">
      <form className="login-card" onSubmit={submit}>
        <div className="login-title">
          <BrandImage theme={theme} className="login-logo" alt="Nexo" />
          <div className="login-access-copy">
            <strong>Acesso ao Nexo</strong>
            <small>Demonstração · somente leitura</small>
          </div>
        </div>
        <label>Usuário<input value={user} onChange={e=>setUser(e.target.value)} placeholder="Digite seu usuário" autoFocus/></label>
        <label>Senha<input type="password" value={password} onChange={e=>setPassword(e.target.value)} placeholder="Digite sua senha"/></label>
        {error&&<div className="error" role="alert">{error}</div>}
        <button className="primary" disabled={loading}>{loading?'Entrando...':<>Entrar <ChevronRight size={18}/></>}</button>
        <div className="demo"><span>Demo</span><code>{DEMO_USER}</code><code>{DEMO_PASSWORD}</code></div>
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
        const response=await apiFetch(API_URL+'/api/v1/products?size=200&active=true');
        if(!response.ok) throw new Error();
        const data=await response.json();
        if(!active) return;

        const rows=productContent(data)
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
      const response=await apiFetch(API_URL+'/api/v1/simulations',{
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
      const response=await apiFetch(API_URL+'/api/v1/advisor/explain',{
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
  barcode:string;
  name:string;
  category:string;
  costPrice:number;
  salePrice:number;
  stock:number;
  min:number;
  active:boolean;
  lot:string;
  expiry:string;
};

function ProductsPanel(){
  const API_URL=import.meta.env.VITE_API_URL || 'https://nexo-estoque-api-production.up.railway.app';
  const readOnly=isReadOnlySession();
  const [query,setQuery]=useState('');
  const [statusFilter,setStatusFilter]=useState<'all'|'active'|'inactive'>('active');
  const [sort,setSort]=useState('name');
  const [page,setPage]=useState(0);
  const [totalPages,setTotalPages]=useState(0);
  const [totalElements,setTotalElements]=useState(0);
  const [showForm,setShowForm]=useState(false);
  const [editing,setEditing]=useState<ProductView|null>(null);
  const [products,setProducts]=useState<ProductView[]>([]);
  const [source,setSource]=useState<'loading'|'api'|'offline'>('loading');
  const [saving,setSaving]=useState(false);
  const [feedback,setFeedback]=useState('');
  const [scannerTarget,setScannerTarget]=useState<'search'|'form'|null>(null);
  const [form,setForm]=useState({
    sku:'',
    barcode:'',
    name:'',
    category:'',
    cost:'',
    sale:'',
    min:''
  });

  function normalizeProduct(product:any):ProductView{
    return {
      id:Number(product.id),
      sku:String(product.sku||''),
      barcode:String(product.barcode||''),
      name:String(product.name||''),
      category:String(product.category||'Sem categoria'),
      costPrice:Number(product.costPrice||0),
      salePrice:Number(product.salePrice||0),
      stock:Number(product.currentStock||0),
      min:Number(product.minimumStock||0),
      active:product.active!==false,
      lot:'—',
      expiry:'—'
    };
  }

  async function loadProducts(targetPage=page){
    setSource('loading');
    try{
      const params=new URLSearchParams({
        page:String(targetPage),
        size:'20',
        sort,
        direction:'asc'
      });
      if(query.trim()) params.set('q',query.trim());
      if(statusFilter==='active') params.set('active','true');
      if(statusFilter==='inactive') params.set('active','false');

      const response=await apiFetch(API_URL+'/api/v1/products?'+params.toString());
      if(!response.ok) throw new Error(await apiErrorMessage(response,'API de produtos indisponível'));
      const data=await response.json();
      const rows=productContent(data);
      setProducts(rows.map(normalizeProduct));
      setTotalPages(Number(data?.totalPages||0));
      setTotalElements(Number(data?.totalElements??rows.length));
      setSource('api');
    }catch(err){
      setProducts([]);
      setTotalPages(0);
      setTotalElements(0);
      setSource('offline');
      setFeedback(err instanceof Error?err.message:'API/MySQL indisponível.');
    }
  }

  useEffect(()=>{
    const timer=window.setTimeout(()=>{
      setPage(0);
      void loadProducts(0);
    },250);
    return ()=>window.clearTimeout(timer);
  },[query,statusFilter,sort]);

  useEffect(()=>{
    void loadProducts(page);
  },[page]);

  function resetForm(){
    setForm({sku:'',barcode:'',name:'',category:'',cost:'',sale:'',min:''});
    setEditing(null);
    setShowForm(false);
  }

  function openCreate(){
    setEditing(null);
    setForm({sku:'',barcode:'',name:'',category:'',cost:'',sale:'',min:''});
    setFeedback('');
    setShowForm(true);
  }

  function openEdit(product:ProductView){
    setEditing(product);
    setForm({
      sku:product.sku,
      barcode:product.barcode,
      name:product.name,
      category:product.category,
      cost:String(product.costPrice),
      sale:String(product.salePrice),
      min:String(product.min)
    });
    setFeedback('');
    setShowForm(true);
  }

  function handleBarcodeDetected(value:string){
    if(scannerTarget==='form'){
      setForm(current=>({...current,barcode:value}));
      setFeedback('Código de barras lido e preenchido no cadastro.');
    }else{
      setStatusFilter('all');
      setQuery(value);
      setFeedback('Código de barras lido. Buscando produto correspondente.');
    }
    setScannerTarget(null);
  }

  async function saveProduct(e:React.FormEvent){
    e.preventDefault();
    if(readOnly){setFeedback('Modo demonstração: alterações de cadastro estão bloqueadas.');return;}
    if(!form.sku.trim()||!form.name.trim()||!form.category.trim()){
      setFeedback('Preencha SKU, nome e categoria.');
      return;
    }

    const payload={
      sku:form.sku.trim().toUpperCase(),
      barcode:form.barcode.trim(),
      name:form.name.trim(),
      category:form.category.trim(),
      costPrice:Number(form.cost||0),
      salePrice:Number(form.sale||0),
      minimumStock:Number(form.min||0)
    };

    setSaving(true);
    setFeedback('');
    try{
      const isEdit=Boolean(editing?.id);
      const response=await apiFetch(
        isEdit
          ? API_URL+'/api/v1/products/'+editing!.id
          : API_URL+'/api/v1/products',
        {
          method:isEdit?'PUT':'POST',
          headers:{'Content-Type':'application/json'},
          body:JSON.stringify(isEdit?payload:{...payload,currentStock:0})
        }
      );

      if(!response.ok){
        throw new Error(await apiErrorMessage(response,isEdit?'Não foi possível atualizar o produto':'Não foi possível salvar o produto'));
      }

      resetForm();
      await loadProducts(page);
      setFeedback(isEdit?'Produto atualizado com segurança.':'Produto salvo na base MySQL.');
      setSource('api');
    }catch(err){
      setFeedback(err instanceof Error?err.message:'Não foi possível salvar o produto.');
    }finally{
      setSaving(false);
    }
  }

  async function toggleActive(product:ProductView){
    if(readOnly||!product.id) return;
    const next=!product.active;
    setSaving(true);
    setFeedback('');
    try{
      const response=await apiFetch(API_URL+'/api/v1/products/'+product.id+'/active',{
        method:'PATCH',
        headers:{'Content-Type':'application/json'},
        body:JSON.stringify({active:next})
      });
      if(!response.ok){
        throw new Error(await apiErrorMessage(response,next?'Não foi possível reativar o produto':'Não foi possível inativar o produto'));
      }
      await loadProducts(page);
      setFeedback(next?'Produto reativado.':'Produto inativado sem apagar o histórico.');
      setSource('api');
    }catch(err){
      setFeedback(err instanceof Error?err.message:'Não foi possível alterar o status do produto.');
    }finally{
      setSaving(false);
    }
  }

  return <>
    <header className="page-header">
      <div><span className="eyebrow">CATÁLOGO E SALDOS</span><h1>Produtos</h1><p>Cadastre, edite e controle o ciclo de vida do catálogo sem alterar saldo fora das movimentações.</p></div>
      <button className="new-action" disabled={readOnly} title={readOnly?'Disponível para Admin e Operador':undefined} onClick={openCreate}>+ Novo produto</button>
    </header>

    <section className="product-toolbar">
      <div className="product-search-with-scan">
        <input value={query} onChange={e=>setQuery(e.target.value)} placeholder="Buscar por produto, SKU ou código de barras"/>
        <button type="button" className="ghost compact barcode-trigger" onClick={()=>setScannerTarget('search')}><Camera size={15}/> Ler código</button>
      </div>
      <div className="product-toolbar-filters">
        <select value={statusFilter} onChange={e=>setStatusFilter(e.target.value as 'all'|'active'|'inactive')}>
          <option value="active">Ativos</option>
          <option value="inactive">Inativos</option>
          <option value="all">Todos</option>
        </select>
        <select value={sort} onChange={e=>setSort(e.target.value)}>
          <option value="name">Nome</option>
          <option value="sku">SKU</option>
          <option value="category">Categoria</option>
          <option value="stock">Estoque</option>
          <option value="minimumStock">Estoque mínimo</option>
        </select>
        <span>{totalElements} produto{totalElements===1?'':'s'}</span>
        <span className={'data-source '+source}>
          {source==='loading'?'carregando...':source==='api'?'API + MySQL':'API indisponível'}
        </span>
      </div>
    </section>

    {feedback&&<div className={'product-feedback '+(source==='offline'?'warning':'success')}>{feedback}</div>}

    {showForm&&<form className="product-form" onSubmit={saveProduct}>
      <div className="form-title">
        <div>
          <span className="eyebrow">{editing?'EDIÇÃO SEGURA':'CADASTRO RÁPIDO'}</span>
          <h2>{editing?'Editar produto':'Novo produto'}</h2>
        </div>
        <button type="button" onClick={resetForm}>Fechar</button>
      </div>
      <div className="form-grid">
        <label>SKU<input maxLength={50} value={form.sku} onChange={e=>setForm({...form,sku:e.target.value})} placeholder="Ex.: MED-102"/></label>
        <label>Código de barras
          <div className="field-with-action">
            <input maxLength={32} value={form.barcode} onChange={e=>setForm({...form,barcode:e.target.value})} placeholder="Opcional"/>
            <button type="button" className="ghost compact" onClick={()=>setScannerTarget('form')}><ScanLine size={15}/> Ler</button>
          </div>
        </label>
        <label>Nome<input maxLength={160} value={form.name} onChange={e=>setForm({...form,name:e.target.value})} placeholder="Nome do produto"/></label>
        <label>Categoria<input maxLength={100} value={form.category} onChange={e=>setForm({...form,category:e.target.value})} placeholder="Categoria"/></label>
        <label>Custo unitário<input type="number" min="0" step="0.01" value={form.cost} onChange={e=>setForm({...form,cost:e.target.value})}/></label>
        <label>Preço de venda<input type="number" min="0" step="0.01" value={form.sale} onChange={e=>setForm({...form,sale:e.target.value})}/></label>
        <label>Estoque mínimo<input type="number" min="0" step="0.001" value={form.min} onChange={e=>setForm({...form,min:e.target.value})}/></label>
      </div>
      <div className="form-actions">
        <span>O saldo não é editável aqui. Entradas, saídas e ajustes continuam auditáveis pelas movimentações.</span>
        <button className="primary compact" disabled={saving}>{saving?'Salvando...':editing?'Salvar alterações':'Salvar produto'}</button>
      </div>
    </form>}

    <section className="product-table-wrap">
      <table className="product-table">
        <thead><tr><th>Produto</th><th>Categoria</th><th>Estoque</th><th>Mínimo</th><th>Situação</th><th>Status</th><th>Ações</th></tr></thead>
        <tbody>
          {products.map(product=>{
            const critical=product.active&&product.stock<product.min;
            return <tr key={product.id??product.sku} className={product.active?'':'product-inactive'}>
              <td><strong>{product.name}</strong><small>{product.sku}{product.barcode?' · '+product.barcode:''}</small></td>
              <td>{product.category}</td>
              <td><b>{product.stock}</b></td>
              <td>{product.min}</td>
              <td><span className={'stock-pill '+(!product.active?'neutral':critical?'critical':'healthy')}>{!product.active?'Inativo':critical?'Crítico':'Saudável'}</span></td>
              <td>{product.active?'Ativo':'Inativo'}</td>
              <td>
                <div className="product-row-actions">
                  <button type="button" className="ghost compact" disabled={readOnly||saving} onClick={()=>openEdit(product)}>Editar</button>
                  <button type="button" className="ghost compact" disabled={readOnly||saving} onClick={()=>void toggleActive(product)}>
                    {product.active?'Inativar':'Reativar'}
                  </button>
                </div>
              </td>
            </tr>;
          })}
          {products.length===0&&<tr><td colSpan={7} className="empty-state">Nenhum produto encontrado.</td></tr>}
        </tbody>
      </table>
    </section>

    {scannerTarget&&<BarcodeScanner onDetected={handleBarcodeDetected} onClose={()=>setScannerTarget(null)}/>}

    <div className="product-pagination">
      <button className="ghost compact" disabled={page<=0||source==='loading'} onClick={()=>setPage(current=>Math.max(0,current-1))}>Anterior</button>
      <span>Página {totalPages===0?0:page+1} de {totalPages}</span>
      <button className="ghost compact" disabled={page+1>=totalPages||source==='loading'} onClick={()=>setPage(current=>current+1)}>Próxima</button>
    </div>
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
  const [previewLoading,setPreviewLoading]=useState(false);
  const [fefoPreview,setFefoPreview]=useState<{
    requestedQuantity:number;
    availableQuantity:number;
    sufficient:boolean;
    allocations:Array<{
      batchId:number;
      lotCode:string;
      expiresAt:string|null;
      availableQuantity:number;
      allocatedQuantity:number;
      fefoPosition:number;
    }>;
  }|null>(null);
  const [previewError,setPreviewError]=useState('');
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
        const response=await apiFetch(API_URL+'/api/v1/products?size=200&active=true');
        if(!response.ok) throw new Error();
        const data=await response.json();
        setProducts(productContent(data).map((p:any)=>({
          id:Number(p.id),
          sku:String(p.sku||''),
          barcode:String(p.barcode||''),
          name:String(p.name||''),
          category:String(p.category||''),
          costPrice:Number(p.costPrice||0),
          salePrice:Number(p.salePrice||0),
          stock:Number(p.currentStock||0),
          min:Number(p.minimumStock||0),
          active:p.active!==false,
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
        const response=await apiFetch(API_URL+'/api/v1/stock/batches?productId='+encodeURIComponent(form.productId));
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

  useEffect(()=>{
    if(form.movementType!=='EXIT'||!form.productId){
      setFefoPreview(null);
      setPreviewError('');
      return;
    }

    const quantity=Number(form.quantity);
    if(!Number.isFinite(quantity)||quantity<=0){
      setFefoPreview(null);
      setPreviewError('');
      return;
    }

    let active=true;
    const timer=window.setTimeout(()=>{
      setPreviewLoading(true);
      setPreviewError('');

      (async()=>{
        try{
          const response=await apiFetch(API_URL+'/api/v1/stock/batches/exit-fefo/preview',{
            method:'POST',
            headers:{'Content-Type':'application/json'},
            body:JSON.stringify({
              productId:Number(form.productId),
              quantity,
              reason:''
            })
          });
          const data=await response.json().catch(()=>null);
          if(!response.ok) throw new Error(data?.detail||data?.message||'Não foi possível calcular a prévia FEFO.');
          if(!active) return;

          setFefoPreview({
            requestedQuantity:Number(data.requestedQuantity||quantity),
            availableQuantity:Number(data.availableQuantity||0),
            sufficient:Boolean(data.sufficient),
            allocations:(Array.isArray(data.allocations)?data.allocations:[]).map((item:any)=>({
              batchId:Number(item.batchId),
              lotCode:String(item.lotCode||''),
              expiresAt:item.expiresAt||null,
              availableQuantity:Number(item.availableQuantity||0),
              allocatedQuantity:Number(item.allocatedQuantity||0),
              fefoPosition:Number(item.fefoPosition||0)
            }))
          });
        }catch(err){
          if(active){
            setFefoPreview(null);
            setPreviewError(err instanceof Error?err.message:'Prévia FEFO indisponível.');
          }
        }finally{
          if(active) setPreviewLoading(false);
        }
      })();
    },250);

    return ()=>{
      active=false;
      window.clearTimeout(timer);
    };
  },[form.productId,form.quantity,form.movementType]);

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

    if(form.movementType==='EXIT'&&fefoPreview&&!fefoPreview.sufficient){
      setError('A quantidade solicitada é maior que o saldo disponível por lote.');
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

    payload={...payload,idempotencyKey:newIdempotencyKey()};

    setSaving(true);
    try{
      const response=await apiFetch(endpoint,{
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

        {form.movementType==='EXIT'&&<>
          <div className="movement-hint fefo">
            O Nexo aplicará FEFO automaticamente: primeiro os lotes com validade mais próxima; lotes sem validade ficam por último.
          </div>

          {(previewLoading||fefoPreview||previewError)&&<div className={'fefo-preview '+(fefoPreview&&!fefoPreview.sufficient?'insufficient':'')}>
            <div className="fefo-preview-head">
              <div>
                <span>PRÉVIA DA SAÍDA</span>
                <strong>{previewLoading?'Calculando ordem FEFO...':fefoPreview
                  ? fefoPreview.sufficient?'Lotes que serão consumidos':'Saldo por lote insuficiente'
                  :'Prévia indisponível'}</strong>
              </div>
              {fefoPreview&&<b>{fefoPreview.availableQuantity} un. disponíveis</b>}
            </div>

            {fefoPreview&&<div className="fefo-preview-list">
              {fefoPreview.allocations.map(item=><div key={item.batchId} className="fefo-preview-row">
                <span>#{item.fefoPosition}</span>
                <div>
                  <strong>{item.lotCode}</strong>
                  <small>{item.expiresAt?new Date(item.expiresAt+'T12:00:00').toLocaleDateString('pt-BR'):'sem validade'}</small>
                </div>
                <b>{item.allocatedQuantity} un.</b>
              </div>)}
            </div>}

            {previewError&&<small className="fefo-preview-error">{previewError}</small>}
          </div>}
        </>}

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
          <button className="primary compact" disabled={saving||loading||previewLoading||(form.movementType==='EXIT'&&!!fefoPreview&&!fefoPreview.sufficient)}>{saving?'Registrando...':'Registrar movimentação'}</button>
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
        const response=await apiFetch(API_URL+'/api/v1/stock/movements?limit=8');
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
  const readOnly=isReadOnlySession();
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
    const response=await apiFetch(API_URL+'/api/v1/inventory/blind');
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
    const response=await apiFetch(API_URL+`/api/v1/inventory/blind/${sessionId}/items`);
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
    if(readOnly){setError('Modo demonstração: abertura de inventário está bloqueada.');return;}
    if(!newName.trim()) return;
    setCreating(true);
    setError('');
    setFeedback('');
    try{
      const response=await apiFetch(API_URL+'/api/v1/inventory/blind',{
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
    if(readOnly){setError('Modo demonstração: contagens estão bloqueadas.');return;}
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
      const response=await apiFetch(API_URL+`/api/v1/inventory/blind/${selectedId}/counts`,{
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
    if(readOnly){setError('Modo demonstração: fechamento de inventário está bloqueado.');return;}
    if(!selectedId||!complete) return;
    setClosing(true);
    setError('');
    setFeedback('');
    try{
      const response=await apiFetch(API_URL+`/api/v1/inventory/blind/${selectedId}/close`,{method:'POST'});
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
        <input value={newName} disabled={readOnly} onChange={e=>setNewName(e.target.value)} placeholder="Nome do inventário, ex.: Contagem semanal · Corredor A"/>
        <button className="primary compact" disabled={readOnly||creating||!newName.trim()}>{creating?'Abrindo...':'+ Abrir inventário'}</button>
      </form>
      <div className="blind-shield"><ShieldCheck size={17}/><span>Durante a contagem, o Nexo não envia o saldo do sistema para a tela.</span></div>
    </section>

    {error&&<div className="product-feedback warning" role="alert">{error}</div>}
    {feedback&&<div className="product-feedback success" role="status" aria-live="polite">{feedback}</div>}

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
                {selected.status==='OPEN'&&<button className="close-inventory" disabled={readOnly||!complete||closing} onClick={closeSession}>
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
                              disabled={readOnly}
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
                        <button className="count-save" disabled={readOnly||savingId===item.productId} onClick={()=>saveCount(item.productId)}>
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
  locationId:number;
  warehouseId:number;
  warehouseName:string;
  branchName?:string|null;
  locationCode:string;
  aisle?:string|null;
  shelf?:string|null;
  binCode?:string|null;
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
        const response=await apiFetch(API_URL+'/api/v1/stock/batches');
        if(!response.ok) throw new Error('Não foi possível carregar os lotes.');
        const data=await response.json();
        setItems((Array.isArray(data)?data:[]).map((item:any)=>({
          id:Number(item.id),
          productId:Number(item.productId),
          locationId:Number(item.locationId),
          warehouseId:Number(item.warehouseId),
          warehouseName:String(item.warehouseName||'Depósito'),
          branchName:item.branchName||null,
          locationCode:String(item.locationCode||'GERAL'),
          aisle:item.aisle||null,
          shelf:item.shelf||null,
          binCode:item.binCode||null,
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
    const matchesQuery=(item.productName+' '+item.sku+' '+item.lotCode+' '+item.warehouseName+' '+item.locationCode).toLowerCase().includes(query.toLowerCase());
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
      <input value={query} onChange={e=>setQuery(e.target.value)} placeholder="Buscar produto, SKU, lote, depósito ou posição"/>
      <select value={status} onChange={e=>setStatus(e.target.value as typeof status)}>
        <option value="all">Todas as situações</option>
        <option value="EXPIRED">Vencidos</option>
        <option value="CRITICAL">Até 30 dias</option>
        <option value="ATTENTION">31–90 dias</option>
        <option value="OK">Regulares</option>
        <option value="NO_EXPIRY">Sem validade</option>
      </select>
    </section>

    {error&&<div className="product-feedback warning" role="alert">{error}</div>}

    <section className="batch-table-wrap">
      <table className="batch-table">
        <thead><tr><th>Produto</th><th>Lote</th><th>Local físico</th><th>Validade</th><th>Saldo</th><th>Custo</th><th>FEFO</th><th>Situação</th></tr></thead>
        <tbody>
          {filtered.map(item=><tr key={item.id}>
            <td><strong>{item.productName}</strong><small>{item.sku}</small></td>
            <td><b>{item.lotCode}</b></td>
            <td>
              <strong>{item.warehouseName}</strong>
              <small>{item.branchName?item.branchName+' · ':''}{item.locationCode}{[item.aisle,item.shelf,item.binCode].filter(Boolean).length?' · '+[item.aisle,item.shelf,item.binCode].filter(Boolean).join('/') : ''}</small>
            </td>
            <td>
              <strong>{item.expiresAt?new Date(item.expiresAt+'T12:00:00').toLocaleDateString('pt-BR'):'Sem validade'}</strong>
              <small>{item.daysToExpiry===null?'—':item.daysToExpiry<0?`${Math.abs(item.daysToExpiry)} dias vencido`:`${item.daysToExpiry} dias`}</small>
            </td>
            <td><b>{item.quantity}</b></td>
            <td>R$ {item.unitCost.toFixed(2).replace('.',',')}</td>
            <td>{item.fefoPosition===1?<span className="fefo-next">Próximo</span>:'#'+item.fefoPosition}</td>
            <td><span className={'expiry-pill '+item.expiryStatus.toLowerCase()}>{statusLabel(item.expiryStatus)}</span></td>
          </tr>)}
          {!filtered.length&&!loading&&<tr><td colSpan={8} className="empty-state">Nenhum lote encontrado.</td></tr>}
          {loading&&<tr><td colSpan={8} className="empty-state">Carregando lotes...</td></tr>}
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
      const response=await apiFetch(API_URL+'/api/v1/advisor/chat',{
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
        const response=await apiFetch(API_URL+'/api/v1/system/readiness');
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

  return <div className={'system-readiness '+state} role="status" aria-live="polite" aria-atomic="true">
    <span className="readiness-dot"/>
    <strong>{text}</strong>
    {latency!==null&&state==='ready'&&<small>{latency} ms</small>}
  </div>;
}



type WarehouseView={
  id:number;
  code:string;
  name:string;
  branchName?:string|null;
  address?:string|null;
  active:boolean;
};

type StockLocationView={
  id:number;
  warehouseId:number;
  warehouseCode:string;
  warehouseName:string;
  branchName?:string|null;
  warehouseAddress?:string|null;
  code:string;
  aisle?:string|null;
  shelf?:string|null;
  binCode?:string|null;
  active:boolean;
};

type LocationBatchView={
  id:number;
  productId:number;
  productName:string;
  sku:string;
  lotCode:string;
  locationId:number;
  warehouseName:string;
  branchName?:string|null;
  locationCode:string;
  aisle?:string|null;
  shelf?:string|null;
  binCode?:string|null;
  quantity:number;
};

function LogisticsPanel(){
  const API_URL=import.meta.env.VITE_API_URL || 'https://nexo-estoque-api-production.up.railway.app';
  const readOnly=isReadOnlySession();
  const [warehouses,setWarehouses]=useState<WarehouseView[]>([]);
  const [locations,setLocations]=useState<StockLocationView[]>([]);
  const [batches,setBatches]=useState<LocationBatchView[]>([]);
  const [loading,setLoading]=useState(true);
  const [saving,setSaving]=useState(false);
  const [feedback,setFeedback]=useState('');
  const [showWarehouse,setShowWarehouse]=useState(false);
  const [showLocation,setShowLocation]=useState(false);
  const [showTransfer,setShowTransfer]=useState(false);
  const [warehouseForm,setWarehouseForm]=useState({code:'',name:'',branchName:'',address:''});
  const [locationForm,setLocationForm]=useState({warehouseId:'',code:'',aisle:'',shelf:'',binCode:''});
  const [transfer,setTransfer]=useState({sourceBatchId:'',destinationLocationId:'',quantity:'',reason:''});

  async function load(){
    setLoading(true);
    setFeedback('');
    try{
      const [warehouseResponse,locationResponse,batchResponse]=await Promise.all([
        apiFetch(API_URL+'/api/v1/warehouses'),
        apiFetch(API_URL+'/api/v1/stock/locations'),
        apiFetch(API_URL+'/api/v1/stock/batches')
      ]);

      if(!warehouseResponse.ok||!locationResponse.ok||!batchResponse.ok){
        throw new Error('Não foi possível carregar depósitos e posições.');
      }

      const [warehouseData,locationData,batchData]=await Promise.all([
        warehouseResponse.json(),
        locationResponse.json(),
        batchResponse.json()
      ]);

      setWarehouses(Array.isArray(warehouseData)?warehouseData:[]);
      setLocations(Array.isArray(locationData)?locationData:[]);
      setBatches((Array.isArray(batchData)?batchData:[]).map((item:any)=>({
        id:Number(item.id),
        productId:Number(item.productId),
        productName:String(item.productName||'Produto'),
        sku:String(item.sku||''),
        lotCode:String(item.lotCode||''),
        locationId:Number(item.locationId),
        warehouseName:String(item.warehouseName||'Depósito'),
        branchName:item.branchName||null,
        locationCode:String(item.locationCode||'GERAL'),
        aisle:item.aisle||null,
        shelf:item.shelf||null,
        binCode:item.binCode||null,
        quantity:Number(item.quantity||0)
      })));
    }catch(err){
      setFeedback(err instanceof Error?err.message:'Módulo de locais indisponível.');
    }finally{
      setLoading(false);
    }
  }

  useEffect(()=>{void load()},[]);

  async function createWarehouse(e:React.FormEvent){
    e.preventDefault();
    if(readOnly) return;
    setSaving(true);
    setFeedback('');
    try{
      const response=await apiFetch(API_URL+'/api/v1/warehouses',{
        method:'POST',
        headers:{'Content-Type':'application/json'},
        body:JSON.stringify(warehouseForm)
      });
      if(!response.ok) throw new Error(await apiErrorMessage(response,'Não foi possível criar o depósito.'));
      setWarehouseForm({code:'',name:'',branchName:'',address:''});
      setShowWarehouse(false);
      await load();
      setFeedback('Depósito/filial criado.');
    }catch(err){
      setFeedback(err instanceof Error?err.message:'Não foi possível criar o depósito.');
    }finally{
      setSaving(false);
    }
  }

  async function createLocation(e:React.FormEvent){
    e.preventDefault();
    if(readOnly) return;
    if(!locationForm.warehouseId){
      setFeedback('Selecione o depósito da posição.');
      return;
    }
    setSaving(true);
    setFeedback('');
    try{
      const response=await apiFetch(API_URL+'/api/v1/stock/locations',{
        method:'POST',
        headers:{'Content-Type':'application/json'},
        body:JSON.stringify({
          ...locationForm,
          warehouseId:Number(locationForm.warehouseId)
        })
      });
      if(!response.ok) throw new Error(await apiErrorMessage(response,'Não foi possível criar a posição.'));
      setLocationForm({warehouseId:'',code:'',aisle:'',shelf:'',binCode:''});
      setShowLocation(false);
      await load();
      setFeedback('Posição física criada.');
    }catch(err){
      setFeedback(err instanceof Error?err.message:'Não foi possível criar a posição.');
    }finally{
      setSaving(false);
    }
  }

  async function createTransfer(e:React.FormEvent){
    e.preventDefault();
    if(readOnly) return;
    if(!transfer.sourceBatchId||!transfer.destinationLocationId||Number(transfer.quantity)<=0){
      setFeedback('Selecione lote, destino e quantidade.');
      return;
    }
    setSaving(true);
    setFeedback('');
    try{
      const response=await apiFetch(API_URL+'/api/v1/stock/transfers',{
        method:'POST',
        headers:{'Content-Type':'application/json'},
        body:JSON.stringify({
          sourceBatchId:Number(transfer.sourceBatchId),
          destinationLocationId:Number(transfer.destinationLocationId),
          quantity:Number(transfer.quantity),
          reason:transfer.reason,
          idempotencyKey:newIdempotencyKey()
        })
      });
      if(!response.ok) throw new Error(await apiErrorMessage(response,'Não foi possível transferir o estoque.'));
      setTransfer({sourceBatchId:'',destinationLocationId:'',quantity:'',reason:''});
      setShowTransfer(false);
      await load();
      setFeedback('Transferência concluída. O saldo global do produto foi preservado.');
    }catch(err){
      setFeedback(err instanceof Error?err.message:'Não foi possível transferir o estoque.');
    }finally{
      setSaving(false);
    }
  }

  const selectedBatch=batches.find(item=>String(item.id)===transfer.sourceBatchId);
  const activeLocations=locations.filter(item=>item.active);

  return <>
    <header className="page-header">
      <div>
        <span className="eyebrow">ESTOQUE FÍSICO</span>
        <h1>Locais & filiais</h1>
        <p>Organize depósitos, posições físicas e transferências internas sem alterar o saldo global.</p>
      </div>
      <div className="purchase-header-actions">
        <button className="ghost" disabled={readOnly} onClick={()=>setShowWarehouse(value=>!value)}><Truck size={16}/> Depósito</button>
        <button className="ghost" disabled={readOnly} onClick={()=>setShowLocation(value=>!value)}><MapPin size={16}/> Posição</button>
        <button className="new-action" disabled={readOnly} onClick={()=>setShowTransfer(value=>!value)}><ArrowRightLeft size={16}/> Transferir</button>
      </div>
    </header>

    {feedback&&<div className="product-feedback success" role="status" aria-live="polite">{feedback}</div>}

    {showWarehouse&&<form className="product-form purchase-form" onSubmit={createWarehouse}>
      <div className="form-title"><div><span className="eyebrow">DEPÓSITO / FILIAL</span><h2>Novo local operacional</h2></div><button type="button" onClick={()=>setShowWarehouse(false)}>Fechar</button></div>
      <div className="form-grid">
        <label>Código<input value={warehouseForm.code} onChange={e=>setWarehouseForm({...warehouseForm,code:e.target.value})} placeholder="Ex.: FILIAL-SUL"/></label>
        <label>Nome<input value={warehouseForm.name} onChange={e=>setWarehouseForm({...warehouseForm,name:e.target.value})} placeholder="Depósito Sul"/></label>
        <label>Filial<input value={warehouseForm.branchName} onChange={e=>setWarehouseForm({...warehouseForm,branchName:e.target.value})} placeholder="Ex.: Fortaleza Sul"/></label>
        <label>Endereço<input value={warehouseForm.address} onChange={e=>setWarehouseForm({...warehouseForm,address:e.target.value})} placeholder="Endereço operacional"/></label>
      </div>
      <div className="form-actions"><span>Cada depósito pode ter várias posições físicas.</span><button className="primary compact" disabled={saving}>{saving?'Salvando...':'Criar depósito'}</button></div>
    </form>}

    {showLocation&&<form className="product-form purchase-form" onSubmit={createLocation}>
      <div className="form-title"><div><span className="eyebrow">ENDEREÇAMENTO</span><h2>Nova posição física</h2></div><button type="button" onClick={()=>setShowLocation(false)}>Fechar</button></div>
      <div className="form-grid">
        <label>Depósito<select value={locationForm.warehouseId} onChange={e=>setLocationForm({...locationForm,warehouseId:e.target.value})}><option value="">Selecione</option>{warehouses.filter(w=>w.active).map(w=><option key={w.id} value={w.id}>{w.name}{w.branchName?' · '+w.branchName:''}</option>)}</select></label>
        <label>Código da posição<input value={locationForm.code} onChange={e=>setLocationForm({...locationForm,code:e.target.value})} placeholder="Ex.: A-01-03"/></label>
        <label>Corredor<input value={locationForm.aisle} onChange={e=>setLocationForm({...locationForm,aisle:e.target.value})} placeholder="A"/></label>
        <label>Prateleira<input value={locationForm.shelf} onChange={e=>setLocationForm({...locationForm,shelf:e.target.value})} placeholder="01"/></label>
        <label>Posição / nicho<input value={locationForm.binCode} onChange={e=>setLocationForm({...locationForm,binCode:e.target.value})} placeholder="03"/></label>
      </div>
      <div className="form-actions"><span>O código físico aparece nos lotes e nos recebimentos.</span><button className="primary compact" disabled={saving}>{saving?'Salvando...':'Criar posição'}</button></div>
    </form>}

    {showTransfer&&<form className="product-form purchase-form" onSubmit={createTransfer}>
      <div className="form-title"><div><span className="eyebrow">TRANSFERÊNCIA INTERNA</span><h2>Mover lote entre posições</h2></div><button type="button" onClick={()=>setShowTransfer(false)}>Fechar</button></div>
      <div className="form-grid">
        <label>Lote de origem<select value={transfer.sourceBatchId} onChange={e=>setTransfer({...transfer,sourceBatchId:e.target.value,destinationLocationId:''})}><option value="">Selecione</option>{batches.filter(b=>b.quantity>0).map(b=><option key={b.id} value={b.id}>{b.productName} · {b.lotCode} · {b.warehouseName}/{b.locationCode} · {b.quantity}</option>)}</select></label>
        <label>Destino<select value={transfer.destinationLocationId} onChange={e=>setTransfer({...transfer,destinationLocationId:e.target.value})}><option value="">Selecione</option>{activeLocations.filter(l=>!selectedBatch||l.id!==selectedBatch.locationId).map(l=><option key={l.id} value={l.id}>{l.warehouseName} · {l.code}</option>)}</select></label>
        <label>Quantidade<input type="number" min="0.001" step="0.001" max={selectedBatch?.quantity} value={transfer.quantity} onChange={e=>setTransfer({...transfer,quantity:e.target.value})}/></label>
        <label>Motivo<input value={transfer.reason} onChange={e=>setTransfer({...transfer,reason:e.target.value})} placeholder="Ex.: reposição da filial"/></label>
      </div>
      <div className="form-actions"><span>Transferências mudam somente a posição física; o total do produto não muda.</span><button className="primary compact" disabled={saving}>{saving?'Transferindo...':'Confirmar transferência'}</button></div>
    </form>}

    <section className="location-kpis">
      <article><span>Depósitos</span><strong>{warehouses.filter(w=>w.active).length}</strong><small>ativos</small></article>
      <article><span>Posições</span><strong>{activeLocations.length}</strong><small>endereços físicos</small></article>
      <article><span>Lotes posicionados</span><strong>{batches.length}</strong><small>com saldo</small></article>
    </section>

    <section className="location-grid">
      {warehouses.map(warehouse=><article className="location-card" key={warehouse.id}>
        <div className="location-card-head">
          <div><span>{warehouse.code}</span><strong>{warehouse.name}</strong></div>
          <Truck size={19}/>
        </div>
        <p>{warehouse.branchName||'Sem filial informada'}</p>
        <small>{warehouse.address||'Endereço não informado'}</small>
        <div className="location-list">
          {locations.filter(location=>location.warehouseId===warehouse.id).map(location=><div key={location.id}>
            <MapPin size={14}/>
            <span><b>{location.code}</b>{[location.aisle,location.shelf,location.binCode].filter(Boolean).length?' · '+[location.aisle,location.shelf,location.binCode].filter(Boolean).join(' / '):''}</span>
          </div>)}
          {!locations.some(location=>location.warehouseId===warehouse.id)&&<em>Nenhuma posição cadastrada.</em>}
        </div>
      </article>)}
      {!loading&&warehouses.length===0&&<div className="empty-state">Nenhum depósito cadastrado.</div>}
    </section>
  </>;
}

type SupplierView={
  id:number;
  name:string;
  taxId?:string|null;
  contactName?:string|null;
  email?:string|null;
  phone?:string|null;
  leadTimeDays:number;
  active:boolean;
};

type PurchaseOrderView={
  id:number;
  supplierId:number;
  supplierName:string;
  status:string;
  source:string;
  ruleVersion?:string|null;
  createdBy:string;
  expectedAt?:string|null;
  notes?:string|null;
  itemCount:number;
  totalAmount:number;
  createdAt:string;
};

function PurchasingPanel(){
  const API_URL=import.meta.env.VITE_API_URL || 'https://nexo-estoque-api-production.up.railway.app';
  const readOnly=isReadOnlySession();
  const [suppliers,setSuppliers]=useState<SupplierView[]>([]);
  const [orders,setOrders]=useState<PurchaseOrderView[]>([]);
  const [products,setProducts]=useState<any[]>([]);
  const [locations,setLocations]=useState<StockLocationView[]>([]);
  const [receiptOrder,setReceiptOrder]=useState<any|null>(null);
  const [receipt,setReceipt]=useState({itemId:'',locationId:'',quantity:'',lotCode:'',expiresAt:''});
  const [loading,setLoading]=useState(true);
  const [saving,setSaving]=useState(false);
  const [feedback,setFeedback]=useState('');
  const [showSupplier,setShowSupplier]=useState(false);
  const [showManual,setShowManual]=useState(false);
  const [showSuggestion,setShowSuggestion]=useState(false);

  const [supplierForm,setSupplierForm]=useState({
    name:'',taxId:'',contactName:'',email:'',phone:'',leadTimeDays:'5'
  });
  const [manual,setManual]=useState({
    supplierId:'',productId:'',quantity:'',unitCost:'',expectedAt:'',notes:''
  });
  const [suggestion,setSuggestion]=useState({
    supplierId:'',productId:'',averageDailyDemand:'',demandVariationPercent:'0',
    supplierDelayDays:'0',plannedPurchase:'0'
  });

  async function load(){
    setLoading(true);
    setFeedback('');
    try{
      const [suppliersResponse,ordersResponse,productsResponse,locationsResponse]=await Promise.all([
        apiFetch(API_URL+'/api/v1/suppliers'),
        apiFetch(API_URL+'/api/v1/purchase-orders'),
        apiFetch(API_URL+'/api/v1/products?size=200&active=true'),
        apiFetch(API_URL+'/api/v1/stock/locations?active=true')
      ]);
      if(!suppliersResponse.ok||!ordersResponse.ok||!productsResponse.ok||!locationsResponse.ok){
        throw new Error('Não foi possível carregar o módulo de compras.');
      }

      const [supplierData,orderData,productData,locationData]=await Promise.all([
        suppliersResponse.json(),
        ordersResponse.json(),
        productsResponse.json(),
        locationsResponse.json()
      ]);

      setSuppliers(Array.isArray(supplierData)?supplierData:[]);
      setOrders(Array.isArray(orderData)?orderData:[]);
      setProducts(productContent(productData));
      setLocations(Array.isArray(locationData)?locationData:[]);
    }catch(err){
      setFeedback(err instanceof Error?err.message:'Módulo de compras indisponível.');
    }finally{
      setLoading(false);
    }
  }

  useEffect(()=>{void load()},[]);

  async function createSupplier(e:React.FormEvent){
    e.preventDefault();
    if(readOnly) return;
    setSaving(true);
    setFeedback('');
    try{
      const response=await apiFetch(API_URL+'/api/v1/suppliers',{
        method:'POST',
        headers:{'Content-Type':'application/json'},
        body:JSON.stringify({
          ...supplierForm,
          leadTimeDays:Number(supplierForm.leadTimeDays||0)
        })
      });
      if(!response.ok) throw new Error(await apiErrorMessage(response,'Não foi possível cadastrar o fornecedor.'));
      setSupplierForm({name:'',taxId:'',contactName:'',email:'',phone:'',leadTimeDays:'5'});
      setShowSupplier(false);
      await load();
      setFeedback('Fornecedor cadastrado.');
    }catch(err){
      setFeedback(err instanceof Error?err.message:'Não foi possível cadastrar o fornecedor.');
    }finally{
      setSaving(false);
    }
  }

  function selectedProduct(id:string){
    return products.find((p:any)=>String(p.id)===id);
  }

  async function createManualOrder(e:React.FormEvent){
    e.preventDefault();
    if(readOnly) return;
    const product=selectedProduct(manual.productId);
    if(!product||!manual.supplierId||Number(manual.quantity)<=0){
      setFeedback('Selecione fornecedor, produto e quantidade.');
      return;
    }

    setSaving(true);
    setFeedback('');
    try{
      const response=await apiFetch(API_URL+'/api/v1/purchase-orders',{
        method:'POST',
        headers:{'Content-Type':'application/json'},
        body:JSON.stringify({
          supplierId:Number(manual.supplierId),
          expectedAt:manual.expectedAt||null,
          notes:manual.notes,
          items:[{
            productId:Number(manual.productId),
            quantity:Number(manual.quantity),
            unitCost:Number(manual.unitCost||product.costPrice||0)
          }]
        })
      });
      if(!response.ok) throw new Error(await apiErrorMessage(response,'Não foi possível criar o pedido.'));
      setManual({supplierId:'',productId:'',quantity:'',unitCost:'',expectedAt:'',notes:''});
      setShowManual(false);
      await load();
      setFeedback('Pedido de compra criado como rascunho.');
    }catch(err){
      setFeedback(err instanceof Error?err.message:'Não foi possível criar o pedido.');
    }finally{
      setSaving(false);
    }
  }

  async function createSuggestedOrder(e:React.FormEvent){
    e.preventDefault();
    if(readOnly) return;
    if(!suggestion.supplierId||!suggestion.productId||Number(suggestion.averageDailyDemand)<=0){
      setFeedback('Selecione fornecedor, produto e informe a demanda média diária.');
      return;
    }

    setSaving(true);
    setFeedback('');
    try{
      const response=await apiFetch(API_URL+'/api/v1/purchase-orders/from-recommendation',{
        method:'POST',
        headers:{'Content-Type':'application/json'},
        body:JSON.stringify({
          productId:Number(suggestion.productId),
          supplierId:Number(suggestion.supplierId),
          averageDailyDemand:Number(suggestion.averageDailyDemand),
          demandVariationPercent:Number(suggestion.demandVariationPercent||0),
          supplierDelayDays:Number(suggestion.supplierDelayDays||0),
          plannedPurchase:Number(suggestion.plannedPurchase||0)
        })
      });
      if(!response.ok) throw new Error(await apiErrorMessage(response,'Não foi possível gerar a sugestão.'));
      const data=await response.json();
      setShowSuggestion(false);
      await load();
      setFeedback('Pedido #'+data.purchaseOrderId+' criado com '+data.recommendedQuantity+' unidades recomendadas.');
    }catch(err){
      setFeedback(err instanceof Error?err.message:'Não foi possível gerar a sugestão.');
    }finally{
      setSaving(false);
    }
  }

  async function openReceipt(order:PurchaseOrderView){
    if(readOnly) return;
    setSaving(true);
    setFeedback('');
    try{
      const response=await apiFetch(API_URL+'/api/v1/purchase-orders/'+order.id);
      if(!response.ok) throw new Error(await apiErrorMessage(response,'Não foi possível carregar o pedido.'));
      const details=await response.json();
      const pending=(Array.isArray(details?.items)?details.items:[])
        .filter((item:any)=>Number(item.receivedQuantity||0)<Number(item.quantity||0));
      if(!pending.length){
        setFeedback('Este pedido não possui itens pendentes de recebimento.');
        return;
      }
      const first=pending[0];
      const remaining=Math.max(0,Number(first.quantity||0)-Number(first.receivedQuantity||0));
      setReceiptOrder(details);
      setReceipt({
        itemId:String(first.id),
        locationId:locations[0]?String(locations[0].id):'',
        quantity:String(remaining),
        lotCode:'',
        expiresAt:''
      });
    }catch(err){
      setFeedback(err instanceof Error?err.message:'Não foi possível preparar o recebimento.');
    }finally{
      setSaving(false);
    }
  }

  function changeReceiptItem(itemId:string){
    const item=(Array.isArray(receiptOrder?.items)?receiptOrder.items:[])
      .find((candidate:any)=>String(candidate.id)===itemId);
    const remaining=item
      ? Math.max(0,Number(item.quantity||0)-Number(item.receivedQuantity||0))
      : 0;
    setReceipt(current=>({...current,itemId,quantity:String(remaining)}));
  }

  async function receiveOrder(e:React.FormEvent){
    e.preventDefault();
    if(readOnly||!receiptOrder?.order?.id) return;
    if(!receipt.itemId||!receipt.locationId||!receipt.lotCode.trim()||Number(receipt.quantity)<=0){
      setFeedback('Preencha item, posição, lote e quantidade recebida.');
      return;
    }

    setSaving(true);
    setFeedback('');
    try{
      const response=await apiFetch(
        API_URL+'/api/v1/purchase-orders/'+receiptOrder.order.id+'/receipts',
        {
          method:'POST',
          headers:{'Content-Type':'application/json'},
          body:JSON.stringify({
            purchaseOrderItemId:Number(receipt.itemId),
            locationId:Number(receipt.locationId),
            lotCode:receipt.lotCode.trim(),
            expiresAt:receipt.expiresAt||null,
            quantity:Number(receipt.quantity),
            idempotencyKey:newIdempotencyKey()
          })
        }
      );
      if(!response.ok) throw new Error(await apiErrorMessage(response,'Não foi possível registrar o recebimento.'));
      const data=await response.json();
      setReceiptOrder(null);
      setReceipt({itemId:'',locationId:'',quantity:'',lotCode:'',expiresAt:''});
      await load();
      setFeedback(
        data.orderStatus==='RECEIVED'
          ? 'Pedido recebido por completo e estoque atualizado.'
          : 'Recebimento parcial registrado. O saldo pendente permanece no pedido.'
      );
    }catch(err){
      setFeedback(err instanceof Error?err.message:'Não foi possível registrar o recebimento.');
    }finally{
      setSaving(false);
    }
  }

  async function sendOrder(order:PurchaseOrderView){
    if(readOnly||order.status!=='DRAFT') return;
    setSaving(true);
    setFeedback('');
    try{
      const response=await apiFetch(API_URL+'/api/v1/purchase-orders/'+order.id+'/status',{
        method:'PATCH',
        headers:{'Content-Type':'application/json'},
        body:JSON.stringify({status:'SENT'})
      });
      if(!response.ok) throw new Error(await apiErrorMessage(response,'Não foi possível enviar o pedido.'));
      await load();
      setFeedback('Pedido #'+order.id+' marcado como enviado.');
    }catch(err){
      setFeedback(err instanceof Error?err.message:'Não foi possível atualizar o pedido.');
    }finally{
      setSaving(false);
    }
  }

  const money=(value:number)=>new Intl.NumberFormat('pt-BR',{style:'currency',currency:'BRL'}).format(value||0);

  return <>
    <header className="page-header">
      <div>
        <span className="eyebrow">ABASTECIMENTO</span>
        <h1>Compras</h1>
        <p>Fornecedores, pedidos e reposição sugerida pelo motor determinístico do Nexo.</p>
      </div>
      <div className="purchase-header-actions">
        <button className="ghost" disabled={readOnly} onClick={()=>setShowSupplier(value=>!value)}><Truck size={16}/> Fornecedor</button>
        <button className="ghost" disabled={readOnly} onClick={()=>setShowManual(value=>!value)}><ShoppingCart size={16}/> Pedido manual</button>
        <button className="new-action" disabled={readOnly} onClick={()=>setShowSuggestion(value=>!value)}><TrendingUp size={16}/> Sugerir reposição</button>
      </div>
    </header>

    {feedback&&<div className="product-feedback success" role="status" aria-live="polite">{feedback}</div>}

    {showSupplier&&<form className="product-form purchase-form" onSubmit={createSupplier}>
      <div className="form-title"><div><span className="eyebrow">FORNECEDOR</span><h2>Novo fornecedor</h2></div><button type="button" onClick={()=>setShowSupplier(false)}>Fechar</button></div>
      <div className="form-grid">
        <label>Nome<input value={supplierForm.name} onChange={e=>setSupplierForm({...supplierForm,name:e.target.value})}/></label>
        <label>Documento<input value={supplierForm.taxId} onChange={e=>setSupplierForm({...supplierForm,taxId:e.target.value})}/></label>
        <label>Contato<input value={supplierForm.contactName} onChange={e=>setSupplierForm({...supplierForm,contactName:e.target.value})}/></label>
        <label>E-mail<input type="email" value={supplierForm.email} onChange={e=>setSupplierForm({...supplierForm,email:e.target.value})}/></label>
        <label>Telefone<input value={supplierForm.phone} onChange={e=>setSupplierForm({...supplierForm,phone:e.target.value})}/></label>
        <label>Prazo médio (dias)<input type="number" min="0" value={supplierForm.leadTimeDays} onChange={e=>setSupplierForm({...supplierForm,leadTimeDays:e.target.value})}/></label>
      </div>
      <div className="form-actions"><span>O prazo médio alimenta as recomendações de reposição.</span><button className="primary compact" disabled={saving}>{saving?'Salvando...':'Salvar fornecedor'}</button></div>
    </form>}

    {showManual&&<form className="product-form purchase-form" onSubmit={createManualOrder}>
      <div className="form-title"><div><span className="eyebrow">PEDIDO MANUAL</span><h2>Novo rascunho</h2></div><button type="button" onClick={()=>setShowManual(false)}>Fechar</button></div>
      <div className="form-grid">
        <label>Fornecedor<select value={manual.supplierId} onChange={e=>setManual({...manual,supplierId:e.target.value})}><option value="">Selecione</option>{suppliers.filter(s=>s.active).map(s=><option key={s.id} value={s.id}>{s.name}</option>)}</select></label>
        <label>Produto<select value={manual.productId} onChange={e=>{const p=selectedProduct(e.target.value);setManual({...manual,productId:e.target.value,unitCost:p?String(p.costPrice||0):''})}}><option value="">Selecione</option>{products.map((p:any)=><option key={p.id} value={p.id}>{p.name} · {p.sku}</option>)}</select></label>
        <label>Quantidade<input type="number" min="0.001" step="0.001" value={manual.quantity} onChange={e=>setManual({...manual,quantity:e.target.value})}/></label>
        <label>Custo unitário<input type="number" min="0" step="0.01" value={manual.unitCost} onChange={e=>setManual({...manual,unitCost:e.target.value})}/></label>
        <label>Previsão<input type="date" value={manual.expectedAt} onChange={e=>setManual({...manual,expectedAt:e.target.value})}/></label>
        <label>Observação<input value={manual.notes} onChange={e=>setManual({...manual,notes:e.target.value})}/></label>
      </div>
      <div className="form-actions"><span>O pedido nasce como rascunho e não altera estoque.</span><button className="primary compact" disabled={saving}>{saving?'Criando...':'Criar pedido'}</button></div>
    </form>}

    {receiptOrder&&<form className="product-form purchase-form receipt-form" onSubmit={receiveOrder}>
      <div className="form-title">
        <div><span className="eyebrow">RECEBIMENTO VINCULADO</span><h2>Pedido #{receiptOrder.order?.id}</h2></div>
        <button type="button" onClick={()=>setReceiptOrder(null)}>Fechar</button>
      </div>
      <div className="form-grid">
        <label>Item do pedido
          <select value={receipt.itemId} onChange={e=>changeReceiptItem(e.target.value)}>
            {(Array.isArray(receiptOrder.items)?receiptOrder.items:[])
              .filter((item:any)=>Number(item.receivedQuantity||0)<Number(item.quantity||0))
              .map((item:any)=>{
                const remaining=Math.max(0,Number(item.quantity||0)-Number(item.receivedQuantity||0));
                return <option key={item.id} value={item.id}>{item.productName} · pendente {remaining}</option>;
              })}
          </select>
        </label>
        <label>Posição de recebimento
          <select value={receipt.locationId} onChange={e=>setReceipt({...receipt,locationId:e.target.value})}>
            <option value="">Selecione</option>
            {locations.filter(location=>location.active).map(location=><option key={location.id} value={location.id}>{location.warehouseName} · {location.code}{location.branchName?' · '+location.branchName:''}</option>)}
          </select>
        </label>
        <label>Lote<input value={receipt.lotCode} onChange={e=>setReceipt({...receipt,lotCode:e.target.value})} placeholder="Lote do fornecedor"/></label>
        <label>Validade<input type="date" value={receipt.expiresAt} onChange={e=>setReceipt({...receipt,expiresAt:e.target.value})}/></label>
        <label>Quantidade recebida<input type="number" min="0.001" step="0.001" value={receipt.quantity} onChange={e=>setReceipt({...receipt,quantity:e.target.value})}/></label>
      </div>
      <div className="form-actions">
        <span>O recebimento atualiza item do pedido, lote, posição física e saldo em uma única transação.</span>
        <button className="primary compact" disabled={saving}>{saving?'Recebendo...':'Confirmar recebimento'}</button>
      </div>
    </form>}

    {showSuggestion&&<form className="product-form purchase-form" onSubmit={createSuggestedOrder}>
      <div className="form-title"><div><span className="eyebrow">REPOSIÇÃO ASSISTIDA</span><h2>Gerar pedido sugerido</h2></div><button type="button" onClick={()=>setShowSuggestion(false)}>Fechar</button></div>
      <div className="form-grid">
        <label>Fornecedor<select value={suggestion.supplierId} onChange={e=>setSuggestion({...suggestion,supplierId:e.target.value})}><option value="">Selecione</option>{suppliers.filter(s=>s.active).map(s=><option key={s.id} value={s.id}>{s.name} · {s.leadTimeDays} d</option>)}</select></label>
        <label>Produto<select value={suggestion.productId} onChange={e=>setSuggestion({...suggestion,productId:e.target.value})}><option value="">Selecione</option>{products.map((p:any)=><option key={p.id} value={p.id}>{p.name} · saldo {p.currentStock}</option>)}</select></label>
        <label>Demanda média/dia<input type="number" min="0.01" step="0.01" value={suggestion.averageDailyDemand} onChange={e=>setSuggestion({...suggestion,averageDailyDemand:e.target.value})}/></label>
        <label>Variação de demanda (%)<input type="number" min="0" step="0.1" value={suggestion.demandVariationPercent} onChange={e=>setSuggestion({...suggestion,demandVariationPercent:e.target.value})}/></label>
        <label>Atraso adicional (dias)<input type="number" min="0" value={suggestion.supplierDelayDays} onChange={e=>setSuggestion({...suggestion,supplierDelayDays:e.target.value})}/></label>
        <label>Compra já planejada<input type="number" min="0" step="0.001" value={suggestion.plannedPurchase} onChange={e=>setSuggestion({...suggestion,plannedPurchase:e.target.value})}/></label>
      </div>
      <div className="form-actions"><span>A quantidade é calculada com a regra simulation-v1.0.0 e fica auditada.</span><button className="primary compact" disabled={saving}>{saving?'Calculando...':'Gerar rascunho'}</button></div>
    </form>}

    <section className="purchase-grid">
      <article className="purchase-panel">
        <div className="section-head"><div><span className="eyebrow">FORNECEDORES</span><h2>Base ativa</h2></div><strong>{suppliers.filter(s=>s.active).length}</strong></div>
        <div className="purchase-list">
          {suppliers.map(s=><div className="purchase-list-row" key={s.id}>
            <div><strong>{s.name}</strong><small>{s.contactName||s.email||'Sem contato informado'}</small></div>
            <span>{s.leadTimeDays} d</span>
          </div>)}
          {!loading&&suppliers.length===0&&<div className="empty-state">Nenhum fornecedor cadastrado.</div>}
        </div>
      </article>

      <article className="purchase-panel purchase-orders-panel">
        <div className="section-head"><div><span className="eyebrow">PEDIDOS</span><h2>Fluxo de compras</h2></div><strong>{orders.length}</strong></div>
        <div className="purchase-list">
          {orders.map(order=><div className="purchase-order-row" key={order.id}>
            <div className="purchase-order-main">
              <strong>#{order.id} · {order.supplierName}</strong>
              <small>{order.itemCount} item{order.itemCount===1?'':'s'} · {order.source==='REPLENISHMENT_RECOMMENDATION'?'reposição sugerida':'manual'} · por {order.createdBy}</small>
            </div>
            <div className="purchase-order-meta">
              <b>{money(Number(order.totalAmount||0))}</b>
              <span className={'purchase-status '+order.status.toLowerCase()}>{order.status}</span>
              {order.status==='DRAFT'&&!readOnly&&<button className="ghost compact" disabled={saving} onClick={()=>void sendOrder(order)}>Enviar</button>}
              {(order.status==='SENT'||order.status==='PARTIALLY_RECEIVED')&&!readOnly&&<button className="ghost compact receipt-action" disabled={saving} onClick={()=>void openReceipt(order)}>Receber</button>}
            </div>
          </div>)}
          {!loading&&orders.length===0&&<div className="empty-state">Nenhum pedido de compra ainda.</div>}
        </div>
      </article>
    </section>
  </>;
}

function Dashboard({logout,theme,onToggleTheme}:{logout:()=>void;theme:Theme;onToggleTheme:()=>void}){
  const API_URL=import.meta.env.VITE_API_URL || 'https://nexo-estoque-api-production.up.railway.app';
  const authSession=readAuthSession();
  const readOnly=authSession?.role==='VIEWER';
  const [page,setPage]=useState<'dashboard'|'products'|'batches'|'locations'|'inventory'|'simulator'|'purchasing'|'intelligence'|'actions'|'planning'|'governance'|'quality'|'traceability'|'assistant'>('dashboard');
  const [showMovement,setShowMovement]=useState(false);
  const [movementRefresh,setMovementRefresh]=useState(0);
  const [dashboardLoading,setDashboardLoading]=useState(true);
  const [dashboardError,setDashboardError]=useState('');
  const [dashboard,setDashboard]=useState<{
    totalStock:number;
    stockValue:number;
    activeProducts:number;
    criticalProducts:number;
    outOfStockProducts:number;
    expiryRiskBatches:number;
    expiredBatches:number;
    expiryWarningBatches:number;
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
        const response=await apiFetch(API_URL+'/api/v1/operations/dashboard');
        if(!response.ok){
          throw new Error(await apiErrorMessage(response,'Não foi possível carregar os indicadores operacionais.'));
        }

        const data=await response.json();
        const attention:Array<{
          key:string;
          kind:'danger'|'warning'|'neutral';
          icon:'stock'|'expiry'|'inventory';
          title:string;
          description:string;
          value:string;
        }>=[];

        const critical=data?.topCritical;
        if(critical){
          const stock=Number(critical.currentStock||0);
          const minimum=Number(critical.minimumStock||0);
          const deficit=Number(critical.deficit||0);
          attention.push({
            key:'stock-'+critical.productId,
            kind:'danger',
            icon:'stock',
            title:String(critical.name||'Produto crítico'),
            description:`Saldo ${formatQuantity(stock)} · mínimo ${formatQuantity(minimum)}`,
            value:stock<=0?'sem estoque':`${formatQuantity(deficit)} abaixo`
          });
        }

        const expiry=data?.topExpiry;
        if(expiry){
          const days=Number(expiry.daysToExpiry);
          attention.push({
            key:'expiry-'+expiry.batchId,
            kind:days<0?'danger':'warning',
            icon:'expiry',
            title:String(expiry.productName||'Lote em risco'),
            description:`Lote ${expiry.lotCode||'—'} · ${expiry.warehouseName||'—'}/${expiry.locationCode||'—'} · ${formatQuantity(Number(expiry.quantity||0))} un.`,
            value:days<0?`${Math.abs(days)} d vencido`:days===0?'vence hoje':`${days} dias`
          });
        }

        if(data?.openInventoryId){
          const counted=Number(data.openInventoryCountedItems||0);
          const products=Number(data.activeProducts||0);
          attention.push({
            key:'inventory-'+data.openInventoryId,
            kind:'neutral',
            icon:'inventory',
            title:String(data.openInventoryName||'Inventário aberto'),
            description:`${counted} de ${products} produtos contados`,
            value:`${Math.max(0,products-counted)} pendentes`
          });
        }

        if(active){
          setDashboard({
            totalStock:Number(data.totalStock||0),
            stockValue:Number(data.stockValue||0),
            activeProducts:Number(data.activeProducts||0),
            criticalProducts:Number(data.criticalProducts||0),
            outOfStockProducts:Number(data.outOfStockProducts||0),
            expiryRiskBatches:Number(data.expiryRiskBatches||0),
            expiredBatches:Number(data.expiredBatches||0),
            expiryWarningBatches:Number(data.expiryWarningBatches||0),
            expiryRiskValue:Number(data.expiryRiskValue||0),
            inventoryAccuracy:data.inventoryAccuracy==null?null:Number(data.inventoryAccuracy),
            inventoryDivergences:data.inventoryDivergences==null?null:Number(data.inventoryDivergences),
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

  async function exportStockPosition(){
    try{
      setDashboardError('');
      const response=await apiFetch(API_URL+'/api/v1/operations/stock-position.csv');
      if(!response.ok) throw new Error(await apiErrorMessage(response,'Não foi possível exportar a posição de estoque.'));
      const blob=await response.blob();
      const url=URL.createObjectURL(blob);
      const anchor=document.createElement('a');
      anchor.href=url;
      anchor.download='nexo-posicao-estoque.csv';
      document.body.appendChild(anchor);
      anchor.click();
      anchor.remove();
      URL.revokeObjectURL(url);
    }catch(err){
      setDashboardError(err instanceof Error?err.message:'Não foi possível exportar a posição de estoque.');
    }
  }

  function formatQuantity(value:number){
    return new Intl.NumberFormat('pt-BR',{maximumFractionDigits:3}).format(value);
  }

  function formatMoney(value:number){
    return new Intl.NumberFormat('pt-BR',{style:'currency',currency:'BRL'}).format(value);
  }

  const actionCount=dashboard
    ? dashboard.criticalProducts+dashboard.expiryRiskBatches+dashboard.attention.filter(item=>item.icon==='inventory').length
    : 0;

  const instruments=[
    {
      code:'STK',
      title:'Saldo físico',
      value:dashboard?formatQuantity(dashboard.totalStock):'—',
      Icon:Boxes,
      tone:'blue',
      detail:dashboard?`${dashboard.activeProducts} produtos · ${formatMoney(dashboard.stockValue)}`:(dashboardLoading?'carregando...':'indisponível')
    },
    {
      code:'CRT',
      title:'Pressão de ruptura',
      value:dashboard?String(dashboard.criticalProducts):'—',
      Icon:AlertTriangle,
      tone:'red',
      detail:dashboard?`${dashboard.outOfStockProducts} itens já zerados`:(dashboardLoading?'carregando...':'indisponível')
    },
    {
      code:'EXP',
      title:'Pressão de validade',
      value:dashboard?String(dashboard.expiryRiskBatches):'—',
      Icon:PackageSearch,
      tone:'amber',
      detail:dashboard?`${formatMoney(dashboard.expiryRiskValue)} expostos · ${dashboard.expiredBatches} vencidos`:(dashboardLoading?'carregando...':'indisponível')
    },
    {
      code:'ACC',
      title:'Confiabilidade física',
      value:dashboard?.inventoryAccuracy!=null?`${dashboard.inventoryAccuracy.toFixed(1).replace('.',',')}%`:'—',
      Icon:ClipboardCheck,
      tone:'green',
      detail:dashboard?.inventoryAccuracy!=null
        ? `${dashboard.inventoryDivergences||0} divergências na última conferência`
        : (dashboardLoading?'carregando...':'sem inventário fechado')
    }
  ];

  const attentionIcon=(icon:'stock'|'expiry'|'inventory')=>{
    if(icon==='stock') return <AlertTriangle/>;
    if(icon==='expiry') return <PackageSearch/>;
    return <ClipboardCheck/>;
  };

  return <div className="app-shell">
    <a className="skip-link" href="#main-content">Ir para o conteúdo</a>
    <aside aria-label="Navegação do Nexo">
      <div className="brand sidebar-brand">
        <BrandImage theme={theme} className="sidebar-logo-full" alt="Nexo" />
        <img src="/nexo-symbol.png" alt="Nexo" className="sidebar-logo-symbol" draggable={false} />
      </div>
      <nav className="sidebar-nav" aria-label="Navegação principal">
        <span className="sidebar-section-title">Principal</span>
        <button type="button" className={page==='dashboard'?'active':''} aria-current={page==='dashboard'?'page':undefined} onClick={()=>setPage('dashboard')}><LayoutDashboard size={18}/><span>Visão geral</span></button>

        <span className="sidebar-section-title">Estoque</span>
        <button type="button" className={page==='products'?'active':''} aria-current={page==='products'?'page':undefined} onClick={()=>setPage('products')}><Boxes size={18}/><span>Produtos</span></button>
        <button type="button" className={page==='batches'?'active':''} aria-current={page==='batches'?'page':undefined} onClick={()=>setPage('batches')}><PackageSearch size={18}/><span>Lotes & validade</span></button>
        <button type="button" className={page==='locations'?'active':''} aria-current={page==='locations'?'page':undefined} onClick={()=>setPage('locations')}><MapPin size={18}/><span>Locais</span></button>
        <button type="button" className={page==='inventory'?'active':''} aria-current={page==='inventory'?'page':undefined} onClick={()=>setPage('inventory')}><ClipboardCheck size={18}/><span>Inventário cego</span></button>

        <span className="sidebar-section-title">Operação</span>
        <button type="button" className={page==='purchasing'?'active':''} aria-current={page==='purchasing'?'page':undefined} onClick={()=>setPage('purchasing')}><ShoppingCart size={18}/><span>Compras</span></button>
        <button type="button" className={page==='actions'?'active':''} aria-current={page==='actions'?'page':undefined} onClick={()=>setPage('actions')}><Sparkles size={18}/><span>Central de ação</span></button>
        <button type="button" className={page==='simulator'?'active':''} aria-current={page==='simulator'?'page':undefined} onClick={()=>setPage('simulator')}><TrendingUp size={18}/><span>Simulador</span></button>

        <span className="sidebar-section-title">Análise</span>
        <button type="button" className={page==='intelligence'?'active':''} aria-current={page==='intelligence'?'page':undefined} onClick={()=>setPage('intelligence')}><TrendingUp size={18}/><span>Inteligência</span></button>
        <button type="button" className={page==='planning'?'active':''} aria-current={page==='planning'?'page':undefined} onClick={()=>setPage('planning')}><TrendingUp size={18}/><span>Planejamento</span></button>

        <span className="sidebar-section-title">Controle</span>
        <button type="button" className={page==='governance'?'active':''} aria-current={page==='governance'?'page':undefined} onClick={()=>setPage('governance')}><ShieldCheck size={18}/><span>Governança</span></button>
        <button type="button" className={page==='quality'?'active':''} aria-current={page==='quality'?'page':undefined} onClick={()=>setPage('quality')}><ShieldAlert size={18}/><span>Qualidade</span></button>
        <button type="button" className={page==='traceability'?'active':''} aria-current={page==='traceability'?'page':undefined} onClick={()=>setPage('traceability')}><ScanLine size={18}/><span>Rastreabilidade</span></button>

        <span className="sidebar-section-title">Assistência</span>
        <button type="button" className={page==='assistant'?'active':''} aria-current={page==='assistant'?'page':undefined} onClick={()=>setPage('assistant')}><BrainCircuit size={18}/><span>Assistente</span></button>
      </nav>
      <div className="sidebar-bottom"><ThemeToggle theme={theme} onToggle={onToggleTheme}/><button className="logout" onClick={logout}><LogOut size={18}/> Sair</button></div>
    </aside>
    <main className="workspace" id="main-content" tabIndex={-1}>
      <SystemReadiness/>
      {readOnly&&<div className="demo-readonly-banner" role="note"><ShieldCheck size={16}/><span>Modo demonstração: consultas, FEFO, locais, simulador, compras e assistente liberados. Alterações operacionais estão bloqueadas.</span></div>}
      {page==='products'
        ? <ProductsPanel/>
        : page==='batches'
          ? <BatchesPanel/>
          : page==='locations'
            ? <LogisticsPanel/>
            : page==='inventory'
            ? <BlindInventoryPanel/>
            : page==='simulator'
              ? <Simulator/>
              : page==='purchasing'
                ? <PurchasingPanel/>
                : page==='intelligence'
                ? <DeferredPanel><InventoryIntelligencePanel/></DeferredPanel>
                : page==='actions'
                ? <DeferredPanel><ActionCenterPanel/></DeferredPanel>
                : page==='planning'
                ? <DeferredPanel><DemandPlanningPanel/></DeferredPanel>
                : page==='governance'
                ? <DeferredPanel><GovernancePanel/></DeferredPanel>
                : page==='quality'
                ? <DeferredPanel><QualityControlPanel/></DeferredPanel>
                : page==='traceability'
                ? <DeferredPanel><TraceabilityPanel/></DeferredPanel>
                : page==='assistant'
                ? <AdvisorPanel/>
                : <>
          <header className="control-header">
            <div className="control-heading">
              <div className="control-path"><span>NEXO</span><i/>OPERAÇÃO<i/>TEMPO REAL</div>
              <h1>Estado operacional</h1>
              <p>{dashboard
                ? actionCount>0
                  ? `${actionCount} sinais exigem leitura operacional agora.`
                  : 'Fluxo estável. Nenhum sinal crítico exige intervenção imediata.'
                : dashboardLoading
                  ? 'Sincronizando o estado atual do estoque...'
                  : 'Os sinais operacionais estão temporariamente indisponíveis.'}</p>
            </div>
            <div className="control-actions">
              <button className="ghost dashboard-export" onClick={()=>void exportStockPosition()}><Download size={16}/> Exportar</button>
              <button className="new-action dashboard-primary-action" disabled={readOnly} title={readOnly?'Disponível para Admin e Operador':undefined} onClick={()=>setShowMovement(true)}>Registrar movimento</button>
            </div>
          </header>

          {dashboardError&&<div className="product-feedback warning" role="alert">{dashboardError}</div>}

          <section className="control-deck" aria-label="Pulso operacional">
            <div className="control-stock">
              <div className="control-stock-label"><span className="live-indicator"/> SALDO CONSOLIDADO</div>
              <strong>{dashboard?formatQuantity(dashboard.totalStock):'—'}</strong>
              <div className="control-stock-meta">
                <span>{dashboard?dashboard.activeProducts:'—'} SKUs ativos</span>
                <span>{dashboard?formatMoney(dashboard.stockValue):'—'} imobilizados</span>
              </div>
            </div>
            <div className="control-pressure">
              <div className={'pressure-line '+((dashboard?.criticalProducts||0)>0?'pressure-danger':'pressure-ok')}>
                <span>Ruptura</span>
                <strong>{dashboard?dashboard.criticalProducts:'—'}</strong>
                <small>{dashboard?`${dashboard.outOfStockProducts} zerados`:'—'}</small>
              </div>
              <div className={'pressure-line '+((dashboard?.expiryRiskBatches||0)>0?'pressure-warning':'pressure-ok')}>
                <span>Validade</span>
                <strong>{dashboard?dashboard.expiryRiskBatches:'—'}</strong>
                <small>{dashboard?`${dashboard.expiryWarningBatches} em observação`:'—'}</small>
              </div>
              <div className="pressure-line pressure-neutral">
                <span>Precisão</span>
                <strong>{dashboard?.inventoryAccuracy!=null?`${dashboard.inventoryAccuracy.toFixed(1).replace('.',',')}%`:'—'}</strong>
                <small>{dashboard?.inventoryDivergences!=null?`${dashboard.inventoryDivergences} divergências`:'sem leitura recente'}</small>
              </div>
            </div>
          </section>

          <section className="instrument-strip" aria-label="Instrumentos do estoque">
            {instruments.map(({code,title,value,Icon,detail,tone})=><article className={'instrument instrument-'+tone} key={code}>
              <div className="instrument-code"><span>{code}</span><Icon size={15}/></div>
              <strong>{value}</strong>
              <div><b>{title}</b><small>{detail}</small></div>
            </article>)}
          </section>

          <section className="command-feed">
            <div className="command-feed-head">
              <div><span className="eyebrow">FILA OPERACIONAL</span><h2>Sinais que mudam decisão</h2></div>
              <span className="feed-count">{dashboard?.attention.length||0} ativos</span>
            </div>
            {dashboard?.attention.length
              ? <div className="signal-feed">
                  {dashboard.attention.map((item,index)=><article className={'signal-row '+(item.kind==='danger'?'danger':item.kind==='warning'?'warning':'neutral')} key={item.key}>
                    <span className="signal-index">{String(index+1).padStart(2,'0')}</span>
                    <div className="signal-icon">{attentionIcon(item.icon)}</div>
                    <div className="signal-copy"><strong>{item.title}</strong><span>{item.description}</span></div>
                    <b className="signal-value">{item.value}</b>
                  </article>)}
                </div>
              : <div className="dashboard-empty">
                  {dashboardLoading?'Calculando prioridades...':'Fluxo estável. Nenhum sinal crítico encontrado.'}
                </div>}
          </section>

          <RecentMovements refreshKey={movementRefresh}/>
          {showMovement&&!readOnly&&<StockMovementModal
            onClose={()=>setShowMovement(false)}
            onSaved={()=>setMovementRefresh(value=>value+1)}
          />}
        </>}
    </main>
  </div>;
}

export default function App(){
  const [auth,setAuth]=useState(Boolean(readAuthSession()));
  const [publicView,setPublicView]=useState<'home'|'login'>(()=>window.location.hash==='#login'?'login':'home');
  const [theme,setTheme]=useState<Theme>(()=>localStorage.getItem('nexo-theme')==='dark'?'dark':'light');

  useEffect(()=>{
    document.documentElement.dataset.theme=theme;
    localStorage.setItem('nexo-theme',theme);
  },[theme]);

  const toggleTheme=()=>setTheme(current=>current==='light'?'dark':'light');

  const showLogin=()=>{
    setPublicView('login');
    window.history.replaceState(null,'',window.location.pathname+window.location.search+'#login');
    window.scrollTo({top:0,behavior:'auto'});
  };

  const showHome=()=>{
    setPublicView('home');
    window.history.replaceState(null,'',window.location.pathname+window.location.search);
    window.scrollTo({top:0,behavior:'auto'});
  };

  if(auth){
    return <Dashboard
      theme={theme}
      onToggleTheme={toggleTheme}
      logout={()=>{clearAuthSession();setAuth(false);setPublicView('home')}}
    />;
  }

  return publicView==='login'
    ? <Login
        onLogin={()=>{setAuth(true);window.history.replaceState(null,'',window.location.pathname+window.location.search)}}
        onBack={showHome}
        theme={theme}
        onToggleTheme={toggleTheme}
      />
    : <PublicHome theme={theme} onToggleTheme={toggleTheme} onLogin={showLogin}/>;
}
