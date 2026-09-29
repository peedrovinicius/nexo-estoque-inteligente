import { useEffect, useMemo, useState } from 'react';
import { AlertTriangle, Boxes, RefreshCw, ShieldCheck, TrendingUp } from 'lucide-react';
import { apiFetch } from './auth';

type PlanningItem={
  productId:number;
  sku:string;
  productName:string;
  category:string;
  currentStock:number;
  reservedQuantity:number;
  incomingQuantity:number;
  availableToPromise:number;
  demand7Days:number;
  demand30Days:number;
  demand90Days:number;
  forecastDailyDemand:number;
  dailyDemandStdDev:number;
  supplierLeadTimeDays:number;
  safetyStock:number;
  reorderPoint:number;
  targetStock:number;
  projectedAtLeadTime:number;
  recommendedQuantity:number;
  riskLevel:'CRITICAL'|'HIGH'|'ATTENTION'|'LOW';
  confidenceLevel:'LOW'|'MEDIUM'|'HIGH';
};
type Summary={
  totalProducts:number;
  criticalProducts:number;
  highRiskProducts:number;
  recommendedUnits:number;
  projectedShortageUnits:number;
};
type RuleInfo={version:string;forecast:string;safetyStock:string;target:string};

const qty=(value:number)=>new Intl.NumberFormat('pt-BR',{maximumFractionDigits:3}).format(value||0);

export default function DemandPlanningPanel(){
  const API_URL=import.meta.env.VITE_API_URL || 'https://nexo-estoque-api-production.up.railway.app';
  const [items,setItems]=useState<PlanningItem[]>([]);
  const [summary,setSummary]=useState<Summary|null>(null);
  const [rule,setRule]=useState<RuleInfo|null>(null);
  const [risk,setRisk]=useState('ALL');
  const [query,setQuery]=useState('');
  const [loading,setLoading]=useState(true);
  const [error,setError]=useState('');
  const [refreshKey,setRefreshKey]=useState(0);

  useEffect(()=>{
    let active=true;
    setLoading(true);
    setError('');

    Promise.all([
      apiFetch(API_URL+'/api/v1/planning?limit=500'),
      apiFetch(API_URL+'/api/v1/planning/summary'),
      apiFetch(API_URL+'/api/v1/planning/rule')
    ]).then(async responses=>{
      if(responses.some(response=>!response.ok)){
        throw new Error('Não foi possível carregar o planejamento de demanda.');
      }
      const [planningData,summaryData,ruleData]=await Promise.all(responses.map(response=>response.json()));
      if(!active) return;
      setItems(Array.isArray(planningData)?planningData:[]);
      setSummary(summaryData||null);
      setRule(ruleData||null);
    }).catch(err=>{
      if(active) setError(err instanceof Error?err.message:'Planejamento indisponível.');
    }).finally(()=>{
      if(active) setLoading(false);
    });

    return ()=>{active=false};
  },[refreshKey]);

  const filtered=useMemo(()=>{
    const term=query.trim().toLowerCase();
    return items.filter(item=>{
      if(risk!=='ALL'&&item.riskLevel!==risk) return false;
      if(!term) return true;
      return [item.productName,item.sku,item.category]
        .some(value=>String(value||'').toLowerCase().includes(term));
    });
  },[items,risk,query]);

  return <>
    <header className="products-head planning-head">
      <div>
        <span className="eyebrow">PLANEJAMENTO DE DEMANDA</span>
        <h1>Antecipe antes de faltar.</h1>
        <p>Previsão ponderada, variabilidade real, estoque de segurança e ponto de reposição calculados a partir do histórico operacional.</p>
      </div>
      <button className="ghost compact planning-refresh" onClick={()=>setRefreshKey(value=>value+1)} disabled={loading}>
        <RefreshCw size={15}/> Atualizar
      </button>
    </header>

    {error&&<div className="product-feedback warning">{error}</div>}

    <section className="planning-kpis">
      <article><AlertTriangle/><span>Risco crítico</span><strong>{loading?'—':summary?.criticalProducts||0}</strong><small>{summary?.highRiskProducts||0} produtos em risco alto</small></article>
      <article><TrendingUp/><span>Compra recomendada</span><strong>{loading?'—':qty(Number(summary?.recommendedUnits||0))}</strong><small>unidades para recompor alvo</small></article>
      <article><Boxes/><span>Déficit projetado</span><strong>{loading?'—':qty(Number(summary?.projectedShortageUnits||0))}</strong><small>unidades no fim do lead time</small></article>
      <article><ShieldCheck/><span>Regra</span><strong>{rule?.version||'—'}</strong><small>{rule?.forecast||'carregando método'}</small></article>
    </section>

    <section className="planning-toolbar">
      <input value={query} onChange={e=>setQuery(e.target.value)} placeholder="Buscar produto, SKU ou categoria"/>
      <select value={risk} onChange={e=>setRisk(e.target.value)}>
        <option value="ALL">Todos os riscos</option>
        <option value="CRITICAL">Crítico</option>
        <option value="HIGH">Alto</option>
        <option value="ATTENTION">Atenção</option>
        <option value="LOW">Baixo</option>
      </select>
      <span>{filtered.length} produto{filtered.length===1?'':'s'}</span>
    </section>

    <section className="planning-table-wrap">
      <table className="planning-table">
        <thead>
          <tr>
            <th>Produto</th>
            <th>Demanda prevista</th>
            <th>Lead time</th>
            <th>Segurança</th>
            <th>Ponto reposição</th>
            <th>Projetado</th>
            <th>Comprar</th>
            <th>Risco</th>
          </tr>
        </thead>
        <tbody>
          {filtered.map(item=><tr key={item.productId}>
            <td><strong>{item.productName}</strong><small>{item.sku} · {item.category}</small><small>Disponível {qty(Number(item.availableToPromise))} · a caminho {qty(Number(item.incomingQuantity))}</small></td>
            <td><strong>{qty(Number(item.forecastDailyDemand))}/dia</strong><small>7d {qty(Number(item.demand7Days))} · 30d {qty(Number(item.demand30Days))} · 90d {qty(Number(item.demand90Days))}</small><small>confiança {item.confidenceLevel.toLowerCase()}</small></td>
            <td><strong>{item.supplierLeadTimeDays} d</strong><small>prazo observado ou cadastrado</small></td>
            <td><strong>{qty(Number(item.safetyStock))}</strong><small>σ {qty(Number(item.dailyDemandStdDev))}</small></td>
            <td><strong>{qty(Number(item.reorderPoint))}</strong><small>alvo {qty(Number(item.targetStock))}</small></td>
            <td><strong className={Number(item.projectedAtLeadTime)<0?'text-danger':''}>{qty(Number(item.projectedAtLeadTime))}</strong><small>após lead time</small></td>
            <td><strong>{qty(Number(item.recommendedQuantity))}</strong><small>unidades</small></td>
            <td><span className={'planning-risk '+item.riskLevel.toLowerCase()}>{item.riskLevel}</span></td>
          </tr>)}
          {!loading&&filtered.length===0&&<tr><td colSpan={8}><div className="empty-state">Nenhum produto encontrado para os filtros atuais.</div></td></tr>}
        </tbody>
      </table>
    </section>

    {rule&&<section className="planning-method">
      <div><strong>56 · Previsão</strong><span>{rule.forecast}</span></div>
      <div><strong>57 · Estoque de segurança</strong><span>{rule.safetyStock}</span></div>
      <div><strong>58–59 · Reposição e ruptura</strong><span>{rule.target}</span></div>
      <div><strong>60 · Planejamento</strong><span>visão única com risco, projeção e compra recomendada</span></div>
    </section>}
  </>;
}
