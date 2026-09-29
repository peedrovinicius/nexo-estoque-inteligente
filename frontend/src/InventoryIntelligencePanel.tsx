import { useEffect, useMemo, useState } from 'react';
import { AlertTriangle, Boxes, Clock, PackageSearch, ShoppingCart, TrendingUp } from 'lucide-react';
import { apiFetch } from './auth';

type AbcItem={productId:number;sku:string;productName:string;category:string;stockQuantity:number;stockValue:number;participationPercent:number;cumulativePercent:number;abcClass:'A'|'B'|'C'};
type SlowItem={productId:number;sku:string;productName:string;currentStock:number;stockValue:number;lastExitAt:string|null;daysSinceLastExit:number|null};
type CoverageItem={productId:number;sku:string;productName:string;currentStock:number;exitQuantity:number;averageDailyConsumption:number;coverageDays:number|null;coverageLevel:'CRITICAL'|'ATTENTION'|'HEALTHY'|'NO_CONSUMPTION'};
type PurchaseAging={orderId:number;supplierId:number;supplierName:string;status:string;expectedAt:string|null;daysOpen:number;overdueDays:number;pendingQuantity:number;pendingValue:number};

const money=(value:number)=>new Intl.NumberFormat('pt-BR',{style:'currency',currency:'BRL'}).format(value||0);
const qty=(value:number)=>new Intl.NumberFormat('pt-BR',{maximumFractionDigits:3}).format(value||0);

export default function InventoryIntelligencePanel(){
  const API_URL=import.meta.env.VITE_API_URL || 'https://nexo-estoque-api-production.up.railway.app';
  const [abc,setAbc]=useState<AbcItem[]>([]);
  const [slow,setSlow]=useState<SlowItem[]>([]);
  const [coverage,setCoverage]=useState<CoverageItem[]>([]);
  const [purchases,setPurchases]=useState<PurchaseAging[]>([]);
  const [loading,setLoading]=useState(true);
  const [error,setError]=useState('');
  const [slowDays,setSlowDays]=useState(90);
  const [windowDays,setWindowDays]=useState(30);

  useEffect(()=>{
    let active=true;
    setLoading(true);
    setError('');

    Promise.all([
      apiFetch(API_URL+'/api/v1/operations/abc?limit=200'),
      apiFetch(API_URL+`/api/v1/operations/slow-moving?days=${slowDays}&limit=200`),
      apiFetch(API_URL+`/api/v1/operations/coverage?windowDays=${windowDays}&limit=200`),
      apiFetch(API_URL+'/api/v1/operations/open-purchases?limit=200')
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
    }).catch(err=>{
      if(active) setError(err instanceof Error?err.message:'Inteligência operacional indisponível.');
    }).finally(()=>{
      if(active) setLoading(false);
    });

    return ()=>{active=false};
  },[slowDays,windowDays]);

  const classACapital=useMemo(
    ()=>abc.filter(item=>item.abcClass==='A').reduce((sum,item)=>sum+Number(item.stockValue||0),0),
    [abc]
  );
  const criticalCoverage=coverage.filter(item=>item.coverageLevel==='CRITICAL').length;
  const overduePurchases=purchases.filter(item=>Number(item.overdueDays)>0);
  const idleValue=slow.reduce((sum,item)=>sum+Number(item.stockValue||0),0);

  return <>
    <header className="products-head intelligence-head">
      <div>
        <span className="eyebrow">INTELIGÊNCIA DE ESTOQUE</span>
        <h1>Capital, giro e cobertura.</h1>
        <p>Leitura determinística para priorizar compra, reduzir capital parado e antecipar ruptura.</p>
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
      </div>
    </header>

    {error&&<div className="product-feedback warning">{error}</div>}

    <section className="intelligence-kpis">
      <article><TrendingUp/><span>Capital classe A</span><strong>{loading?'—':money(classACapital)}</strong><small>itens de maior participação</small></article>
      <article><Clock/><span>Capital sem giro</span><strong>{loading?'—':money(idleValue)}</strong><small>{slow.length} produtos no critério</small></article>
      <article><AlertTriangle/><span>Cobertura crítica</span><strong>{loading?'—':criticalCoverage}</strong><small>menos de 7 dias</small></article>
      <article><ShoppingCart/><span>Compras atrasadas</span><strong>{loading?'—':overduePurchases.length}</strong><small>{money(overduePurchases.reduce((sum,item)=>sum+Number(item.pendingValue||0),0))} pendentes</small></article>
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
    </section>
  </>;
}
