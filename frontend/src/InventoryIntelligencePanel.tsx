import { useEffect, useMemo, useState } from 'react';
import { AlertTriangle, Boxes, Clock, Download, PackageSearch, Settings, ShieldAlert, ShoppingCart, TrendingUp, WalletCards } from 'lucide-react';
import { apiFetch, readAuthSession } from './auth';

type AbcItem={productId:number;sku:string;productName:string;category:string;stockQuantity:number;stockValue:number;participationPercent:number;cumulativePercent:number;abcClass:'A'|'B'|'C'};
type SlowItem={productId:number;sku:string;productName:string;currentStock:number;stockValue:number;lastExitAt:string|null;daysSinceLastExit:number|null};
type CoverageItem={productId:number;sku:string;productName:string;currentStock:number;exitQuantity:number;averageDailyConsumption:number;coverageDays:number|null;coverageLevel:'CRITICAL'|'ATTENTION'|'HEALTHY'|'NO_CONSUMPTION'};
type PurchaseAging={orderId:number;supplierId:number;supplierName:string;status:string;expectedAt:string|null;daysOpen:number;overdueDays:number;pendingQuantity:number;pendingValue:number};
type ExpiryExposure={horizonDays:number;expiredBatches:number;expiredQuantity:number;expiredValue:number;criticalBatches:number;criticalQuantity:number;criticalValue:number;warningBatches:number;warningQuantity:number;warningValue:number};
type CapitalItem={dimension:'category'|'warehouse';key:string;label:string;stockQuantity:number;stockValue:number;participationPercent:number};
type OperationalAlert={key:string;type:'STOCK'|'EXPIRY'|'COVERAGE'|'SLOW_MOVING'|'PURCHASE';severity:'CRITICAL'|'WARNING'|'INFO';title:string;description:string;value:string};
type AlertSettings={expiryWarningDays:number;lowCoverageDays:number;slowMovingDays:number;purchaseOverdueDays:number;coverageWindowDays:number};

const money=(value:number)=>new Intl.NumberFormat('pt-BR',{style:'currency',currency:'BRL'}).format(value||0);
const qty=(value:number)=>new Intl.NumberFormat('pt-BR',{maximumFractionDigits:3}).format(value||0);

export default function InventoryIntelligencePanel(){
  const API_URL=import.meta.env.VITE_API_URL || 'https://nexo-estoque-api-production.up.railway.app';
  const session=readAuthSession();
  const canConfigure=session?.role==='ADMIN';

  const [abc,setAbc]=useState<AbcItem[]>([]);
  const [slow,setSlow]=useState<SlowItem[]>([]);
  const [coverage,setCoverage]=useState<CoverageItem[]>([]);
  const [purchases,setPurchases]=useState<PurchaseAging[]>([]);
  const [exposure,setExposure]=useState<ExpiryExposure|null>(null);
  const [capital,setCapital]=useState<CapitalItem[]>([]);
  const [alerts,setAlerts]=useState<OperationalAlert[]>([]);
  const [settings,setSettings]=useState<AlertSettings|null>(null);
  const [settingsDraft,setSettingsDraft]=useState<AlertSettings|null>(null);

  const [loading,setLoading]=useState(true);
  const [savingSettings,setSavingSettings]=useState(false);
  const [error,setError]=useState('');
  const [feedback,setFeedback]=useState('');
  const [slowDays,setSlowDays]=useState(90);
  const [windowDays,setWindowDays]=useState(30);
  const [horizonDays,setHorizonDays]=useState(90);
  const [capitalDimension,setCapitalDimension]=useState<'category'|'warehouse'>('category');
  const [draftQuery,setDraftQuery]=useState('');
  const [draftCategory,setDraftCategory]=useState('');
  const [filters,setFilters]=useState({query:'',category:''});
  const [refreshKey,setRefreshKey]=useState(0);

  useEffect(()=>{
    let active=true;
    setLoading(true);
    setError('');
    setFeedback('');

    const filteredParams=(extra:Record<string,string|number>={})=>{
      const params=new URLSearchParams();
      Object.entries(extra).forEach(([key,value])=>params.set(key,String(value)));
      if(filters.query) params.set('query',filters.query);
      if(filters.category) params.set('category',filters.category);
      return params.toString();
    };

    Promise.all([
      apiFetch(API_URL+'/api/v1/operations/abc?'+filteredParams({limit:200})),
      apiFetch(API_URL+'/api/v1/operations/slow-moving?'+filteredParams({days:slowDays,limit:200})),
      apiFetch(API_URL+'/api/v1/operations/coverage?'+filteredParams({windowDays,limit:200})),
      apiFetch(API_URL+'/api/v1/operations/open-purchases?limit=200'),
      apiFetch(API_URL+'/api/v1/operations/expiry-exposure?'+filteredParams({horizonDays})),
      apiFetch(API_URL+'/api/v1/operations/capital?'+filteredParams({dimension:capitalDimension,limit:100})),
      apiFetch(API_URL+'/api/v1/operations/alerts'),
      apiFetch(API_URL+'/api/v1/operations/alerts/config')
    ]).then(async responses=>{
      if(responses.some(response=>!response.ok)){
        throw new Error('Não foi possível carregar a inteligência operacional.');
      }
      const data=await Promise.all(responses.map(response=>response.json()));
      if(!active) return;
      setAbc(Array.isArray(data[0])?data[0]:[]);
      setSlow(Array.isArray(data[1])?data[1]:[]);
      setCoverage(Array.isArray(data[2])?data[2]:[]);
      setPurchases(Array.isArray(data[3])?data[3]:[]);
      setExposure(data[4]||null);
      setCapital(Array.isArray(data[5])?data[5]:[]);
      setAlerts(Array.isArray(data[6])?data[6]:[]);
      setSettings(data[7]||null);
      setSettingsDraft(data[7]||null);
    }).catch(err=>{
      if(active) setError(err instanceof Error?err.message:'Inteligência operacional indisponível.');
    }).finally(()=>{
      if(active) setLoading(false);
    });

    return ()=>{active=false};
  },[slowDays,windowDays,horizonDays,capitalDimension,filters,refreshKey]);

  const classACapital=useMemo(
    ()=>abc.filter(item=>item.abcClass==='A').reduce((sum,item)=>sum+Number(item.stockValue||0),0),
    [abc]
  );
  const criticalCoverage=coverage.filter(item=>item.coverageLevel==='CRITICAL').length;
  const overduePurchases=purchases.filter(item=>Number(item.overdueDays)>0);
  const idleValue=slow.reduce((sum,item)=>sum+Number(item.stockValue||0),0);
  const criticalAlerts=alerts.filter(item=>item.severity==='CRITICAL').length;

  function applyFilters(e:React.FormEvent){
    e.preventDefault();
    setFilters({query:draftQuery.trim(),category:draftCategory.trim()});
  }

  function clearFilters(){
    setDraftQuery('');
    setDraftCategory('');
    setFilters({query:'',category:''});
  }

  function filterQuery(extra:Record<string,string|number>={}){
    const params=new URLSearchParams();
    Object.entries(extra).forEach(([key,value])=>params.set(key,String(value)));
    if(filters.query) params.set('query',filters.query);
    if(filters.category) params.set('category',filters.category);
    return params.toString();
  }

  async function downloadCsv(path:string,filename:string){
    try{
      setError('');
      const response=await apiFetch(API_URL+path);
      if(!response.ok) throw new Error('Não foi possível gerar o relatório.');
      const blob=await response.blob();
      const url=URL.createObjectURL(blob);
      const anchor=document.createElement('a');
      anchor.href=url;
      anchor.download=filename;
      document.body.appendChild(anchor);
      anchor.click();
      anchor.remove();
      URL.revokeObjectURL(url);
    }catch(err){
      setError(err instanceof Error?err.message:'Não foi possível gerar o relatório.');
    }
  }

  async function saveAlertSettings(e:React.FormEvent){
    e.preventDefault();
    if(!settingsDraft||!canConfigure) return;
    setSavingSettings(true);
    setError('');
    setFeedback('');
    try{
      const response=await apiFetch(API_URL+'/api/v1/operations/alerts/config',{
        method:'PUT',
        headers:{'Content-Type':'application/json'},
        body:JSON.stringify(settingsDraft)
      });
      if(!response.ok){
        const body=await response.json().catch(()=>null);
        throw new Error(String(body?.message||body?.detail||'Não foi possível salvar a configuração.'));
      }
      const updated=await response.json();
      setSettings(updated);
      setSettingsDraft(updated);
      setFeedback('Limiar de alertas atualizado e aplicado à central.');
      setRefreshKey(value=>value+1);
    }catch(err){
      setError(err instanceof Error?err.message:'Não foi possível salvar a configuração.');
    }finally{
      setSavingSettings(false);
    }
  }

  return <>
    <header className="products-head intelligence-head">
      <div>
        <span className="eyebrow">INTELIGÊNCIA DE ESTOQUE</span>
        <h1>Capital, risco e cobertura.</h1>
        <p>Leitura determinística para reduzir capital parado, exposição por validade e risco de ruptura.</p>
      </div>
      <div className="intelligence-filters">
        <label>Sem giro há
          <select value={slowDays} onChange={e=>setSlowDays(Number(e.target.value))}>
            <option value={30}>30 dias</option>
            <option value={60}>60 dias</option>
            <option value={90}>90 dias</option>
            <option value={180}>180 dias</option>
          </select>
        </label>
        <label>Cobertura por
          <select value={windowDays} onChange={e=>setWindowDays(Number(e.target.value))}>
            <option value={14}>14 dias</option>
            <option value={30}>30 dias</option>
            <option value={60}>60 dias</option>
            <option value={90}>90 dias</option>
          </select>
        </label>
        <label>Janela de validade
          <select value={horizonDays} onChange={e=>setHorizonDays(Number(e.target.value))}>
            <option value={30}>30 dias</option>
            <option value={60}>60 dias</option>
            <option value={90}>90 dias</option>
            <option value={180}>180 dias</option>
          </select>
        </label>
      </div>
    </header>

    <form className="intelligence-search" onSubmit={applyFilters}>
      <label>Produto ou SKU
        <input value={draftQuery} onChange={e=>setDraftQuery(e.target.value)} placeholder="Buscar na inteligência"/>
      </label>
      <label>Categoria
        <input value={draftCategory} onChange={e=>setDraftCategory(e.target.value)} placeholder="Ex.: Medicamentos"/>
      </label>
      <button className="primary compact">Aplicar filtros</button>
      <button type="button" className="ghost compact" onClick={clearFilters}>Limpar</button>
    </form>

    <div className="intelligence-toolbar">
      <span>{filters.query||filters.category?'Filtros ativos':'Visão completa do estoque'}</span>
      <div>
        <button className="ghost compact" onClick={()=>void downloadCsv('/api/v1/operations/abc.csv?'+filterQuery(),'nexo-curva-abc.csv')}><Download size={15}/> ABC</button>
        <button className="ghost compact" onClick={()=>void downloadCsv('/api/v1/operations/slow-moving.csv?'+filterQuery({days:slowDays}),'nexo-sem-giro.csv')}><Download size={15}/> Sem giro</button>
        <button className="ghost compact" onClick={()=>void downloadCsv('/api/v1/operations/coverage.csv?'+filterQuery({windowDays}),'nexo-cobertura.csv')}><Download size={15}/> Cobertura</button>
      </div>
    </div>

    {error&&<div className="product-feedback warning">{error}</div>}
    {feedback&&<div className="product-feedback success">{feedback}</div>}

    <section className="intelligence-kpis">
      <article><TrendingUp/><span>Capital classe A</span><strong>{loading?'—':money(classACapital)}</strong><small>itens de maior participação</small></article>
      <article><Clock/><span>Capital sem giro</span><strong>{loading?'—':money(idleValue)}</strong><small>{slow.length} produtos no critério</small></article>
      <article><ShieldAlert/><span>Valor vencido</span><strong>{loading?'—':money(Number(exposure?.expiredValue||0))}</strong><small>{exposure?.expiredBatches||0} lotes vencidos</small></article>
      <article><AlertTriangle/><span>Alertas críticos</span><strong>{loading?'—':criticalAlerts}</strong><small>{alerts.length} alertas operacionais</small></article>
    </section>

    <section className="intelligence-grid">
      <article className="intelligence-panel">
        <div className="section-head"><div><span className="eyebrow">36 · CURVA ABC</span><h2>Concentração de capital</h2></div><Boxes size={19}/></div>
        <div className="intelligence-list">
          {abc.slice(0,8).map(item=><div className="intelligence-row" key={item.productId}>
            <span className={'abc-badge abc-'+item.abcClass.toLowerCase()}>{item.abcClass}</span>
            <div><strong>{item.productName}</strong><small>{item.sku} · {Number(item.participationPercent).toFixed(1).replace('.',',')}% do valor</small></div>
            <b>{money(Number(item.stockValue))}</b>
          </div>)}
          {!loading&&abc.length===0&&<div className="empty-state">Sem estoque valorizado para classificar.</div>}
        </div>
      </article>

      <article className="intelligence-panel">
        <div className="section-head"><div><span className="eyebrow">37 · SEM GIRO</span><h2>Capital parado</h2></div><Clock size={19}/></div>
        <div className="intelligence-list">
          {slow.slice(0,8).map(item=><div className="intelligence-row" key={item.productId}>
            <div><strong>{item.productName}</strong><small>{item.daysSinceLastExit==null?'sem saída registrada':item.daysSinceLastExit+' dias desde a última saída'} · saldo {qty(Number(item.currentStock))}</small></div>
            <b>{money(Number(item.stockValue))}</b>
          </div>)}
          {!loading&&slow.length===0&&<div className="empty-state">Nenhum item enquadrado como sem giro.</div>}
        </div>
      </article>

      <article className="intelligence-panel">
        <div className="section-head"><div><span className="eyebrow">38 · COBERTURA</span><h2>Dias até ruptura</h2></div><PackageSearch size={19}/></div>
        <div className="intelligence-list">
          {coverage.slice(0,8).map(item=><div className="intelligence-row" key={item.productId}>
            <span className={'coverage-dot '+item.coverageLevel.toLowerCase()}/>
            <div><strong>{item.productName}</strong><small>consumo médio {qty(Number(item.averageDailyConsumption))}/dia · saldo {qty(Number(item.currentStock))}</small></div>
            <b>{item.coverageDays==null?'sem consumo':Number(item.coverageDays).toFixed(1).replace('.',',')+' d'}</b>
          </div>)}
          {!loading&&coverage.length===0&&<div className="empty-state">Sem dados para estimar cobertura.</div>}
        </div>
      </article>

      <article className="intelligence-panel">
        <div className="section-head"><div><span className="eyebrow">39 · COMPRAS ABERTAS</span><h2>Pedidos envelhecidos</h2></div><ShoppingCart size={19}/></div>
        <div className="intelligence-list">
          {purchases.slice(0,8).map(item=><div className="intelligence-row" key={item.orderId}>
            <div><strong>#{item.orderId} · {item.supplierName}</strong><small>{item.daysOpen} dias aberto · pendente {qty(Number(item.pendingQuantity))}{item.expectedAt?' · previsto '+new Date(item.expectedAt+'T00:00:00').toLocaleDateString('pt-BR'):''}</small></div>
            <b className={item.overdueDays>0?'text-danger':''}>{item.overdueDays>0?item.overdueDays+' d atraso':money(Number(item.pendingValue))}</b>
          </div>)}
          {!loading&&purchases.length===0&&<div className="empty-state">Nenhum pedido enviado está pendente.</div>}
        </div>
      </article>

      <article className="intelligence-panel">
        <div className="section-head"><div><span className="eyebrow">41 · VALIDADE</span><h2>Exposição a perdas</h2></div><ShieldAlert size={19}/></div>
        <div className="exposure-summary">
          <div><span>Vencido</span><strong>{money(Number(exposure?.expiredValue||0))}</strong><small>{qty(Number(exposure?.expiredQuantity||0))} un. · {exposure?.expiredBatches||0} lotes</small></div>
          <div><span>Até 30 dias</span><strong>{money(Number(exposure?.criticalValue||0))}</strong><small>{qty(Number(exposure?.criticalQuantity||0))} un. · {exposure?.criticalBatches||0} lotes</small></div>
          <div><span>31–{horizonDays} dias</span><strong>{money(Number(exposure?.warningValue||0))}</strong><small>{qty(Number(exposure?.warningQuantity||0))} un. · {exposure?.warningBatches||0} lotes</small></div>
        </div>
      </article>

      <article className="intelligence-panel">
        <div className="section-head">
          <div><span className="eyebrow">42 · CAPITAL IMOBILIZADO</span><h2>Onde o dinheiro está</h2></div>
          <WalletCards size={19}/>
        </div>
        <div className="capital-switch">
          <button className={capitalDimension==='category'?'active':''} onClick={()=>setCapitalDimension('category')}>Categoria</button>
          <button className={capitalDimension==='warehouse'?'active':''} onClick={()=>setCapitalDimension('warehouse')}>Depósito</button>
        </div>
        <div className="intelligence-list">
          {capital.slice(0,8).map(item=><div className="intelligence-row" key={item.dimension+'-'+item.key}>
            <div><strong>{item.label}</strong><small>{qty(Number(item.stockQuantity))} un. · {Number(item.participationPercent).toFixed(1).replace('.',',')}% do capital</small></div>
            <b>{money(Number(item.stockValue))}</b>
          </div>)}
          {!loading&&capital.length===0&&<div className="empty-state">Sem capital imobilizado no filtro atual.</div>}
        </div>
      </article>

      <article className="intelligence-panel intelligence-alerts">
        <div className="section-head"><div><span className="eyebrow">45 · CENTRAL DE ALERTAS</span><h2>Prioridades configuráveis</h2></div><AlertTriangle size={19}/></div>
        <div className="alerts-layout">
          <div className="intelligence-list">
            {alerts.slice(0,12).map(item=><div className={'intelligence-row alert-row '+item.severity.toLowerCase()} key={item.key}>
              <span className={'alert-severity '+item.severity.toLowerCase()}>{item.severity==='CRITICAL'?'CRÍTICO':item.severity==='WARNING'?'ATENÇÃO':'INFO'}</span>
              <div><strong>{item.title}</strong><small>{item.description}</small></div>
              <b>{item.value}</b>
            </div>)}
            {!loading&&alerts.length===0&&<div className="empty-state">Nenhum alerta operacional ativo.</div>}
          </div>

          <form className="alert-settings" onSubmit={saveAlertSettings}>
            <div className="alert-settings-title"><Settings size={17}/><div><strong>Limiar dos alertas</strong><small>{canConfigure?'Edição exclusiva de Admin':'Somente leitura neste perfil'}</small></div></div>
            {settingsDraft&&<>
              <label>Validade em atenção (dias)<input type="number" min="1" max="365" disabled={!canConfigure} value={settingsDraft.expiryWarningDays} onChange={e=>setSettingsDraft({...settingsDraft,expiryWarningDays:Number(e.target.value)})}/></label>
              <label>Cobertura crítica abaixo de (dias)<input type="number" min="1" max="365" disabled={!canConfigure} value={settingsDraft.lowCoverageDays} onChange={e=>setSettingsDraft({...settingsDraft,lowCoverageDays:Number(e.target.value)})}/></label>
              <label>Sem giro após (dias)<input type="number" min="1" max="3650" disabled={!canConfigure} value={settingsDraft.slowMovingDays} onChange={e=>setSettingsDraft({...settingsDraft,slowMovingDays:Number(e.target.value)})}/></label>
              <label>Compra atrasada após (dias)<input type="number" min="1" max="365" disabled={!canConfigure} value={settingsDraft.purchaseOverdueDays} onChange={e=>setSettingsDraft({...settingsDraft,purchaseOverdueDays:Number(e.target.value)})}/></label>
              <label>Janela para consumo (dias)<input type="number" min="7" max="365" disabled={!canConfigure} value={settingsDraft.coverageWindowDays} onChange={e=>setSettingsDraft({...settingsDraft,coverageWindowDays:Number(e.target.value)})}/></label>
              {canConfigure&&<button className="primary compact" disabled={savingSettings}>{savingSettings?'Salvando...':'Salvar limiares'}</button>}
            </>}
            {settings&&<small className="settings-footnote">Configuração atual aplicada a estoque, validade, cobertura, giro e compras.</small>}
          </form>
        </div>
      </article>
    </section>
  </>;
}
