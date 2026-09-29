import { useEffect, useMemo, useState } from 'react';
import { Activity, AlertTriangle, Boxes, Download, History, Search, ShieldCheck, Truck } from 'lucide-react';
import { apiFetch } from './auth';

type SupplierPerformance={
  supplierId:number;
  supplierName:string;
  orderCount:number;
  receivedOrderCount:number;
  openOrderCount:number;
  delayedOpenOrderCount:number;
  orderedQuantity:number;
  receivedQuantity:number;
  fillRatePercent:number;
  averageLeadTimeDays:number|null;
  onTimeRatePercent:number|null;
  purchasedValue:number;
};

type StockFlow={
  productId:number;
  sku:string;
  productName:string;
  category:string;
  currentStock:number;
  entryQuantity:number;
  exitQuantity:number;
  returnQuantity:number;
  adjustmentQuantity:number;
  netFlow:number;
  movementCount:number;
  lastMovementAt:string|null;
};

type LedgerItem={
  movementId:number;
  source:'LIVE'|'ARCHIVE';
  productId:number;
  sku:string;
  productName:string;
  lots:string|null;
  movementType:string;
  quantity:number;
  balanceBefore:number;
  balanceAfter:number;
  reason:string|null;
  performedBy:string|null;
  createdAt:string|null;
};

type LotEvent={
  eventType:string;
  productId:number;
  sku:string;
  productName:string;
  lotCode:string;
  quantity:number;
  origin:string|null;
  destination:string|null;
  reference:string|null;
  actor:string|null;
  createdAt:string|null;
};

type IntegrityIssue={
  key:string;
  severity:'CRITICAL'|'WARNING';
  type:string;
  title:string;
  detail:string;
};

type IntegrityReport={
  checkedAt:string;
  stockMismatchCount:number;
  negativeProductBalanceCount:number;
  negativeBatchBalanceCount:number;
  inactiveProductWithStockCount:number;
  receiptMismatchCount:number;
  issues:IntegrityIssue[];
};

const money=(value:number)=>new Intl.NumberFormat('pt-BR',{style:'currency',currency:'BRL'}).format(value||0);
const qty=(value:number)=>new Intl.NumberFormat('pt-BR',{maximumFractionDigits:3}).format(value||0);
const dateTime=(value:string|null)=>value?new Date(value).toLocaleString('pt-BR'):'—';

export default function TraceabilityPanel(){
  const API_URL=import.meta.env.VITE_API_URL || 'https://nexo-estoque-api-production.up.railway.app';
  const [suppliers,setSuppliers]=useState<SupplierPerformance[]>([]);
  const [flow,setFlow]=useState<StockFlow[]>([]);
  const [ledger,setLedger]=useState<LedgerItem[]>([]);
  const [integrity,setIntegrity]=useState<IntegrityReport|null>(null);
  const [timeline,setTimeline]=useState<LotEvent[]>([]);
  const [loading,setLoading]=useState(true);
  const [timelineLoading,setTimelineLoading]=useState(false);
  const [error,setError]=useState('');

  const [flowDays,setFlowDays]=useState(30);
  const [flowQuery,setFlowQuery]=useState('');
  const [flowCategory,setFlowCategory]=useState('');
  const [draftFlowQuery,setDraftFlowQuery]=useState('');
  const [draftFlowCategory,setDraftFlowCategory]=useState('');

  const [movementQuery,setMovementQuery]=useState('');
  const [movementType,setMovementType]=useState('');
  const [movementActor,setMovementActor]=useState('');
  const [movementFrom,setMovementFrom]=useState('');
  const [movementTo,setMovementTo]=useState('');
  const [appliedMovement,setAppliedMovement]=useState({query:'',movementType:'',actor:'',from:'',to:''});

  const [lotCode,setLotCode]=useState('');
  const [searchedLot,setSearchedLot]=useState('');

  useEffect(()=>{
    let active=true;
    setLoading(true);
    setError('');

    const flowParams=new URLSearchParams({days:String(flowDays),limit:'200'});
    if(flowQuery) flowParams.set('query',flowQuery);
    if(flowCategory) flowParams.set('category',flowCategory);

    const movementParams=new URLSearchParams({limit:'500'});
    if(appliedMovement.query) movementParams.set('query',appliedMovement.query);
    if(appliedMovement.movementType) movementParams.set('movementType',appliedMovement.movementType);
    if(appliedMovement.actor) movementParams.set('actor',appliedMovement.actor);
    if(appliedMovement.from) movementParams.set('from',appliedMovement.from);
    if(appliedMovement.to) movementParams.set('to',appliedMovement.to);

    Promise.all([
      apiFetch(API_URL+'/api/v1/traceability/suppliers?limit=100'),
      apiFetch(API_URL+'/api/v1/traceability/stock-flow?'+flowParams.toString()),
      apiFetch(API_URL+'/api/v1/traceability/movements?'+movementParams.toString()),
      apiFetch(API_URL+'/api/v1/traceability/integrity?issueLimit=200')
    ]).then(async responses=>{
      if(responses.some(response=>!response.ok)){
        throw new Error('Não foi possível carregar a rastreabilidade operacional.');
      }
      const data=await Promise.all(responses.map(response=>response.json()));
      if(!active) return;
      setSuppliers(Array.isArray(data[0])?data[0]:[]);
      setFlow(Array.isArray(data[1])?data[1]:[]);
      setLedger(Array.isArray(data[2])?data[2]:[]);
      setIntegrity(data[3]||null);
    }).catch(err=>{
      if(active) setError(err instanceof Error?err.message:'Rastreabilidade indisponível.');
    }).finally(()=>{
      if(active) setLoading(false);
    });

    return ()=>{active=false};
  },[flowDays,flowQuery,flowCategory,appliedMovement]);

  const delayedOrders=useMemo(
    ()=>suppliers.reduce((sum,item)=>sum+Number(item.delayedOpenOrderCount||0),0),
    [suppliers]
  );
  const totalExit=useMemo(
    ()=>flow.reduce((sum,item)=>sum+Number(item.exitQuantity||0),0),
    [flow]
  );
  const integrityProblems=integrity
    ? integrity.stockMismatchCount+integrity.negativeProductBalanceCount+integrity.negativeBatchBalanceCount
      +integrity.inactiveProductWithStockCount+integrity.receiptMismatchCount
    : 0;

  function applyFlowFilters(e:React.FormEvent){
    e.preventDefault();
    setFlowQuery(draftFlowQuery.trim());
    setFlowCategory(draftFlowCategory.trim());
  }

  function applyMovementFilters(e:React.FormEvent){
    e.preventDefault();
    setAppliedMovement({
      query:movementQuery.trim(),
      movementType,
      actor:movementActor.trim(),
      from:movementFrom,
      to:movementTo
    });
  }

  function movementParams(){
    const params=new URLSearchParams();
    if(appliedMovement.query) params.set('query',appliedMovement.query);
    if(appliedMovement.movementType) params.set('movementType',appliedMovement.movementType);
    if(appliedMovement.actor) params.set('actor',appliedMovement.actor);
    if(appliedMovement.from) params.set('from',appliedMovement.from);
    if(appliedMovement.to) params.set('to',appliedMovement.to);
    return params.toString();
  }

  async function downloadMovements(){
    try{
      setError('');
      const query=movementParams();
      const response=await apiFetch(API_URL+'/api/v1/traceability/movements.csv'+(query?'?'+query:''));
      if(!response.ok) throw new Error('Não foi possível exportar o livro de movimentações.');
      const blob=await response.blob();
      const url=URL.createObjectURL(blob);
      const anchor=document.createElement('a');
      anchor.href=url;
      anchor.download='nexo-livro-movimentacoes.csv';
      document.body.appendChild(anchor);
      anchor.click();
      anchor.remove();
      URL.revokeObjectURL(url);
    }catch(err){
      setError(err instanceof Error?err.message:'Não foi possível exportar o livro de movimentações.');
    }
  }

  async function searchLot(e:React.FormEvent){
    e.preventDefault();
    const normalized=lotCode.trim();
    if(!normalized) return;
    setTimelineLoading(true);
    setError('');
    try{
      const response=await apiFetch(API_URL+'/api/v1/traceability/lots/'+encodeURIComponent(normalized)+'?limit=500');
      if(!response.ok) throw new Error('Não foi possível rastrear o lote informado.');
      const data=await response.json();
      setTimeline(Array.isArray(data)?data:[]);
      setSearchedLot(normalized);
    }catch(err){
      setError(err instanceof Error?err.message:'Não foi possível rastrear o lote.');
    }finally{
      setTimelineLoading(false);
    }
  }

  return <>
    <header className="products-head traceability-head">
      <div>
        <span className="eyebrow">RASTREABILIDADE OPERACIONAL</span>
        <h1>Do fornecedor ao lote.</h1>
        <p>Histórico consolidado para acompanhar compras, movimentações, origem de lotes e integridade dos saldos.</p>
      </div>
    </header>

    {error&&<div className="product-feedback warning">{error}</div>}

    <section className="trace-kpis">
      <article><Truck/><span>Fornecedores monitorados</span><strong>{loading?'—':suppliers.length}</strong><small>{delayedOrders} pedidos atrasados</small></article>
      <article><Activity/><span>Saídas no período</span><strong>{loading?'—':qty(totalExit)}</strong><small>janela de {flowDays} dias</small></article>
      <article><History/><span>Movimentos localizados</span><strong>{loading?'—':ledger.length}</strong><small>histórico ativo + arquivo</small></article>
      <article><ShieldCheck/><span>Inconsistências</span><strong>{loading?'—':integrityProblems}</strong><small>reconciliação automática</small></article>
    </section>

    <section className="trace-grid">
      <article className="trace-panel">
        <div className="section-head"><div><span className="eyebrow">46 · FORNECEDORES</span><h2>Desempenho de entrega</h2></div><Truck size={19}/></div>
        <div className="trace-list">
          {suppliers.slice(0,10).map(item=><div className="trace-row" key={item.supplierId}>
            <div>
              <strong>{item.supplierName}</strong>
              <small>{item.orderCount} pedidos · {item.openOrderCount} abertos · {item.delayedOpenOrderCount} atrasados</small>
            </div>
            <div className="trace-metrics">
              <span><b>{Number(item.fillRatePercent||0).toFixed(1).replace('.',',')}%</b> atendimento</span>
              <span><b>{item.onTimeRatePercent==null?'—':Number(item.onTimeRatePercent).toFixed(1).replace('.',',')+'%'}</b> no prazo</span>
              <span><b>{money(Number(item.purchasedValue||0))}</b> comprado</span>
            </div>
          </div>)}
          {!loading&&suppliers.length===0&&<div className="empty-state">Sem fornecedores para analisar.</div>}
        </div>
      </article>

      <article className="trace-panel">
        <div className="section-head"><div><span className="eyebrow">47 · FLUXO DE ESTOQUE</span><h2>Entrada, saída e giro operacional</h2></div><Activity size={19}/></div>
        <form className="trace-filters compact-grid" onSubmit={applyFlowFilters}>
          <label>Janela
            <select value={flowDays} onChange={e=>setFlowDays(Number(e.target.value))}>
              <option value={7}>7 dias</option>
              <option value={30}>30 dias</option>
              <option value={60}>60 dias</option>
              <option value={90}>90 dias</option>
            </select>
          </label>
          <label>Produto/SKU<input value={draftFlowQuery} onChange={e=>setDraftFlowQuery(e.target.value)} placeholder="Buscar"/></label>
          <label>Categoria<input value={draftFlowCategory} onChange={e=>setDraftFlowCategory(e.target.value)} placeholder="Categoria"/></label>
          <button className="primary compact">Aplicar</button>
        </form>
        <div className="trace-list">
          {flow.slice(0,12).map(item=><div className="trace-row flow-row" key={item.productId}>
            <div>
              <strong>{item.productName}</strong>
              <small>{item.sku} · saldo {qty(Number(item.currentStock))} · {item.movementCount} movimentos</small>
            </div>
            <div className="flow-values">
              <span>Entrada <b>{qty(Number(item.entryQuantity))}</b></span>
              <span>Saída <b>{qty(Number(item.exitQuantity))}</b></span>
              <span>Fluxo líquido <b>{qty(Number(item.netFlow))}</b></span>
            </div>
          </div>)}
          {!loading&&flow.length===0&&<div className="empty-state">Nenhum fluxo encontrado para os filtros.</div>}
        </div>
      </article>

      <article className="trace-panel trace-wide">
        <div className="section-head">
          <div><span className="eyebrow">48 · LIVRO DE MOVIMENTAÇÕES</span><h2>Pesquisa histórica unificada</h2></div>
          <button className="ghost compact trace-export" onClick={()=>void downloadMovements()}><Download size={15}/> Exportar CSV</button>
        </div>
        <form className="trace-filters ledger-filters" onSubmit={applyMovementFilters}>
          <label>Produto, SKU ou lote<input value={movementQuery} onChange={e=>setMovementQuery(e.target.value)} placeholder="Buscar"/></label>
          <label>Tipo
            <select value={movementType} onChange={e=>setMovementType(e.target.value)}>
              <option value="">Todos</option>
              <option value="ENTRY">Entrada</option>
              <option value="EXIT">Saída</option>
              <option value="ADJUSTMENT">Ajuste</option>
              <option value="RETURN">Devolução</option>
            </select>
          </label>
          <label>Usuário<input value={movementActor} onChange={e=>setMovementActor(e.target.value)} placeholder="Operador"/></label>
          <label>De<input type="date" value={movementFrom} onChange={e=>setMovementFrom(e.target.value)}/></label>
          <label>Até<input type="date" value={movementTo} onChange={e=>setMovementTo(e.target.value)}/></label>
          <button className="primary compact"><Search size={15}/> Pesquisar</button>
        </form>
        <div className="ledger-table-wrap">
          <table className="ledger-table">
            <thead><tr><th>Data</th><th>Produto</th><th>Lote</th><th>Tipo</th><th>Qtd.</th><th>Saldo</th><th>Usuário</th><th>Origem</th></tr></thead>
            <tbody>
              {ledger.slice(0,100).map(item=><tr key={item.source+'-'+item.movementId}>
                <td>{dateTime(item.createdAt)}</td>
                <td><strong>{item.productName}</strong><small>{item.sku}</small></td>
                <td>{item.lots||'—'}</td>
                <td><span className={'movement-type '+item.movementType.toLowerCase()}>{item.movementType}</span></td>
                <td>{qty(Number(item.quantity))}</td>
                <td>{qty(Number(item.balanceBefore))} → {qty(Number(item.balanceAfter))}</td>
                <td>{item.performedBy||'—'}</td>
                <td>{item.source==='ARCHIVE'?'Arquivo':'Ativo'}</td>
              </tr>)}
            </tbody>
          </table>
          {!loading&&ledger.length===0&&<div className="empty-state">Nenhuma movimentação encontrada.</div>}
        </div>
      </article>

      <article className="trace-panel">
        <div className="section-head"><div><span className="eyebrow">49 · TRILHA DE LOTE</span><h2>Origem e caminho físico</h2></div><Boxes size={19}/></div>
        <form className="lot-search" onSubmit={searchLot}>
          <input value={lotCode} onChange={e=>setLotCode(e.target.value)} placeholder="Informe o código exato do lote"/>
          <button className="primary compact" disabled={timelineLoading}><Search size={15}/>{timelineLoading?'Buscando...':'Rastrear'}</button>
        </form>
        {searchedLot&&<div className="lot-caption">Linha do tempo do lote <strong>{searchedLot}</strong></div>}
        <div className="timeline">
          {timeline.map((item,index)=><div className="timeline-event" key={item.eventType+'-'+item.createdAt+'-'+index}>
            <span className="timeline-dot"/>
            <div>
              <strong>{item.eventType.replaceAll('_',' ')}</strong>
              <small>{item.productName} · {item.sku} · {qty(Number(item.quantity))} un.</small>
              {(item.origin||item.destination)&&<small>{item.origin||'—'} {item.destination?'→ '+item.destination:''}</small>}
              <small>{item.reference||'—'} · {item.actor||'—'} · {dateTime(item.createdAt)}</small>
            </div>
          </div>)}
          {!timelineLoading&&searchedLot&&timeline.length===0&&<div className="empty-state">Nenhum evento encontrado para esse lote.</div>}
        </div>
      </article>

      <article className="trace-panel">
        <div className="section-head"><div><span className="eyebrow">50 · INTEGRIDADE</span><h2>Reconciliação automática</h2></div><ShieldCheck size={19}/></div>
        <div className="integrity-summary">
          <div><span>Saldo x lotes</span><strong>{integrity?.stockMismatchCount??'—'}</strong></div>
          <div><span>Saldo negativo</span><strong>{(integrity?.negativeProductBalanceCount||0)+(integrity?.negativeBatchBalanceCount||0)}</strong></div>
          <div><span>Inativo com estoque</span><strong>{integrity?.inactiveProductWithStockCount??'—'}</strong></div>
          <div><span>Recebimentos</span><strong>{integrity?.receiptMismatchCount??'—'}</strong></div>
        </div>
        <div className="trace-list integrity-list">
          {integrity?.issues.slice(0,12).map(item=><div className="trace-row" key={item.key}>
            <AlertTriangle size={16}/>
            <div><strong>{item.title}</strong><small>{item.detail}</small></div>
            <span className={'integrity-badge '+item.severity.toLowerCase()}>{item.severity==='CRITICAL'?'CRÍTICO':'ATENÇÃO'}</span>
          </div>)}
          {!loading&&integrity&&integrity.issues.length===0&&<div className="integrity-ok"><ShieldCheck size={18}/> Nenhuma inconsistência encontrada.</div>}
        </div>
      </article>
    </section>
  </>;
}
