import { useEffect, useMemo, useState } from 'react';
import { AlertTriangle, Boxes, CheckCircle2, Clock3, PackagePlus, RefreshCw, ShieldCheck, ShoppingCart, XCircle } from 'lucide-react';
import { apiFetch, isReadOnlySession, readAuthSession } from './auth';

type Suggestion={
  productId:number;sku:string;productName:string;category:string;
  currentStock:number;minimumStock:number;reservedQuantity:number;incomingQuantity:number;
  availableToPromise:number;averageDailyDemand:number;supplierId:number|null;supplierName:string|null;
  supplierLeadTimeDays:number;referenceUnitCost:number;recommendedQuantity:number;estimatedCost:number;
  riskLevel:string;supplierRequired:boolean;
};
type DraftOrder={purchaseOrderId:number;supplierId:number;supplierName:string;itemCount:number;totalAmount:number};
type BatchResult={ruleVersion:string;requestedProducts:number;createdOrders:number;orders:DraftOrder[]};
type Approval={
  purchaseOrderId:number;supplierName:string;totalAmount:number;createdBy:string;createdAt:string|null;
  approvalStatus:'NOT_REQUESTED'|'PENDING'|'APPROVED'|'REJECTED';
  requestedBy:string|null;requestedAt:string|null;decidedBy:string|null;decidedAt:string|null;decisionReason:string|null;
};
type Reservation={
  id:number;productId:number;sku:string;productName:string;quantity:number;referenceCode:string;notes:string|null;
  status:string;reservedBy:string;expiresAt:string|null;createdAt:string|null;
  productStock:number;activeReservedQuantity:number;availableToPromise:number;
};
type ProductOption={id:number;sku:string;name:string;currentStock:number};
type DailyAction={key:string;type:string;severity:'CRITICAL'|'WARNING'|'INFO';title:string;description:string;value:string;actionTarget:string};
type DailyQueue={generatedAt:string;criticalCount:number;warningCount:number;infoCount:number;items:DailyAction[]};

const money=(value:number)=>new Intl.NumberFormat('pt-BR',{style:'currency',currency:'BRL'}).format(value||0);
const qty=(value:number)=>new Intl.NumberFormat('pt-BR',{maximumFractionDigits:3}).format(value||0);
const dateTime=(value:string|null)=>value?new Date(value).toLocaleString('pt-BR'):'—';

async function message(response:Response,fallback:string){
  const body=await response.json().catch(()=>null);
  return String(body?.message||body?.detail||fallback);
}

export default function ActionCenterPanel(){
  const API_URL=import.meta.env.VITE_API_URL || 'https://nexo-estoque-api-production.up.railway.app';
  const session=readAuthSession();
  const readOnly=isReadOnlySession();
  const isAdmin=session?.role==='ADMIN';

  const [suggestions,setSuggestions]=useState<Suggestion[]>([]);
  const [approvals,setApprovals]=useState<Approval[]>([]);
  const [reservations,setReservations]=useState<Reservation[]>([]);
  const [products,setProducts]=useState<ProductOption[]>([]);
  const [daily,setDaily]=useState<DailyQueue|null>(null);
  const [selected,setSelected]=useState<number[]>([]);
  const [windowDays,setWindowDays]=useState(30);
  const [variation,setVariation]=useState(0);
  const [loading,setLoading]=useState(true);
  const [saving,setSaving]=useState(false);
  const [error,setError]=useState('');
  const [feedback,setFeedback]=useState('');
  const [refreshKey,setRefreshKey]=useState(0);
  const [reservationForm,setReservationForm]=useState({
    productId:'',
    quantity:'',
    referenceCode:'',
    notes:'',
    expiresAt:''
  });

  useEffect(()=>{
    let active=true;
    setLoading(true);
    setError('');

    const replParams=new URLSearchParams({
      windowDays:String(windowDays),
      demandVariationPercent:String(variation),
      limit:'200'
    });

    Promise.all([
      apiFetch(API_URL+'/api/v1/action-center/replenishment?'+replParams),
      apiFetch(API_URL+'/api/v1/action-center/purchase-approvals'),
      apiFetch(API_URL+'/api/v1/action-center/reservations'),
      apiFetch(API_URL+'/api/v1/action-center/daily-actions'),
      apiFetch(API_URL+'/api/v1/products?page=0&size=200&active=true&sort=name&direction=asc')
    ]).then(async responses=>{
      if(responses.some(response=>!response.ok)) throw new Error('Não foi possível carregar a Central de Ação.');
      const data=await Promise.all(responses.map(response=>response.json()));
      if(!active) return;
      setSuggestions(Array.isArray(data[0])?data[0]:[]);
      setApprovals(Array.isArray(data[1])?data[1]:[]);
      setReservations(Array.isArray(data[2])?data[2]:[]);
      setDaily(data[3]||null);
      const rows=Array.isArray(data[4]?.content)?data[4].content:[];
      setProducts(rows.map((item:any)=>({
        id:Number(item.id),
        sku:String(item.sku||''),
        name:String(item.name||''),
        currentStock:Number(item.currentStock||0)
      })));
      setSelected(current=>current.filter(id=>(Array.isArray(data[0])?data[0]:[]).some((item:any)=>Number(item.productId)===id)));
    }).catch(err=>{
      if(active) setError(err instanceof Error?err.message:'Central de Ação indisponível.');
    }).finally(()=>{
      if(active) setLoading(false);
    });

    return ()=>{active=false};
  },[windowDays,variation,refreshKey]);

  const actionableSuggestions=suggestions.filter(item=>!item.supplierRequired);
  const selectedCost=useMemo(
    ()=>suggestions.filter(item=>selected.includes(item.productId)).reduce((sum,item)=>sum+Number(item.estimatedCost||0),0),
    [suggestions,selected]
  );
  const activeReservations=reservations.filter(item=>item.status==='ACTIVE');

  function toggleSuggestion(productId:number){
    setSelected(current=>current.includes(productId)
      ? current.filter(id=>id!==productId)
      : [...current,productId]);
  }

  async function createBatchDrafts(){
    if(readOnly||selected.length===0) return;
    setSaving(true);setError('');setFeedback('');
    try{
      const response=await apiFetch(API_URL+'/api/v1/action-center/replenishment/batch-drafts',{
        method:'POST',
        headers:{'Content-Type':'application/json'},
        body:JSON.stringify({
          productIds:selected,
          windowDays,
          demandVariationPercent:variation
        })
      });
      if(!response.ok) throw new Error(await message(response,'Não foi possível gerar os rascunhos.'));
      const data=await response.json() as BatchResult;
      setFeedback(data.createdOrders===1
        ? '1 pedido de compra criado em rascunho e aguardando aprovação.'
        : data.createdOrders+' pedidos de compra criados em rascunho e aguardando aprovação.');
      setSelected([]);
      setRefreshKey(value=>value+1);
    }catch(err){
      setError(err instanceof Error?err.message:'Não foi possível gerar os rascunhos.');
    }finally{setSaving(false)}
  }

  async function approvalAction(orderId:number,action:'request'|'approve'|'reject'){
    if(readOnly) return;
    setSaving(true);setError('');setFeedback('');
    try{
      const response=await apiFetch(API_URL+'/api/v1/action-center/purchase-approvals/'+orderId+'/'+action,{
        method:'POST',
        headers:{'Content-Type':'application/json'},
        body:action==='request'?undefined:JSON.stringify({})
      });
      if(!response.ok) throw new Error(await message(response,'Não foi possível atualizar a aprovação.'));
      setFeedback(
        action==='request'?'Pedido enviado para aprovação.'
          :action==='approve'?'Pedido aprovado. Ele já pode ser enviado ao fornecedor.'
          :'Pedido rejeitado e mantido em rascunho.'
      );
      setRefreshKey(value=>value+1);
    }catch(err){
      setError(err instanceof Error?err.message:'Não foi possível atualizar a aprovação.');
    }finally{setSaving(false)}
  }

  async function createReservation(e:React.FormEvent){
    e.preventDefault();
    if(readOnly) return;
    if(!reservationForm.productId||!reservationForm.quantity||!reservationForm.referenceCode.trim()){
      setError('Informe produto, quantidade e referência da reserva.');
      return;
    }
    setSaving(true);setError('');setFeedback('');
    try{
      const response=await apiFetch(API_URL+'/api/v1/action-center/reservations',{
        method:'POST',
        headers:{'Content-Type':'application/json'},
        body:JSON.stringify({
          productId:Number(reservationForm.productId),
          quantity:Number(reservationForm.quantity),
          referenceCode:reservationForm.referenceCode.trim(),
          notes:reservationForm.notes.trim()||null,
          expiresAt:reservationForm.expiresAt||null
        })
      });
      if(!response.ok) throw new Error(await message(response,'Não foi possível criar a reserva.'));
      setReservationForm({productId:'',quantity:'',referenceCode:'',notes:'',expiresAt:''});
      setFeedback('Estoque reservado sem alterar o saldo físico.');
      setRefreshKey(value=>value+1);
    }catch(err){
      setError(err instanceof Error?err.message:'Não foi possível criar a reserva.');
    }finally{setSaving(false)}
  }

  async function cancelReservation(id:number){
    if(readOnly) return;
    setSaving(true);setError('');setFeedback('');
    try{
      const response=await apiFetch(API_URL+'/api/v1/action-center/reservations/'+id+'/cancel',{method:'POST'});
      if(!response.ok) throw new Error(await message(response,'Não foi possível cancelar a reserva.'));
      setFeedback('Reserva cancelada e disponibilidade liberada.');
      setRefreshKey(value=>value+1);
    }catch(err){
      setError(err instanceof Error?err.message:'Não foi possível cancelar a reserva.');
    }finally{setSaving(false)}
  }

  return <>
    <header className="products-head action-center-head">
      <div>
        <span className="eyebrow">CENTRAL DE AÇÃO</span>
        <h1>Decida e execute.</h1>
        <p>Reposição, aprovações e reservas conectadas aos dados reais do estoque, sem alterar as regras críticas de saldo.</p>
      </div>
      <button className="ghost compact action-refresh" onClick={()=>setRefreshKey(value=>value+1)} disabled={loading}>
        <RefreshCw size={15}/> Atualizar
      </button>
    </header>

    {error&&<div className="product-feedback warning">{error}</div>}
    {feedback&&<div className="product-feedback success">{feedback}</div>}

    <section className="action-kpis">
      <article><AlertTriangle/><span>Ações críticas</span><strong>{loading?'—':daily?.criticalCount||0}</strong><small>{daily?.warningCount||0} em atenção</small></article>
      <article><ShoppingCart/><span>Reposições sugeridas</span><strong>{loading?'—':suggestions.length}</strong><small>{actionableSuggestions.length} prontas para pedido</small></article>
      <article><ShieldCheck/><span>Aprovações pendentes</span><strong>{loading?'—':approvals.filter(item=>item.approvalStatus==='PENDING').length}</strong><small>decisão exclusiva de Admin</small></article>
      <article><Boxes/><span>Reservas ativas</span><strong>{loading?'—':activeReservations.length}</strong><small>{qty(activeReservations.reduce((sum,item)=>sum+Number(item.quantity||0),0))} unidades comprometidas</small></article>
    </section>

    <section className="action-grid">
      <article className="action-panel action-wide">
        <div className="section-head">
          <div><span className="eyebrow">55 · FILA DIÁRIA</span><h2>O que precisa de decisão agora</h2></div>
          <Clock3 size={19}/>
        </div>
        <div className="daily-actions">
          {daily?.items.slice(0,16).map(item=><div className={'daily-action '+item.severity.toLowerCase()} key={item.key}>
            <span className={'action-severity '+item.severity.toLowerCase()}>{item.severity==='CRITICAL'?'CRÍTICO':item.severity==='WARNING'?'ATENÇÃO':'INFO'}</span>
            <div><strong>{item.title}</strong><small>{item.description}</small></div>
            <b>{item.value}</b>
          </div>)}
          {!loading&&daily&&daily.items.length===0&&<div className="action-empty"><CheckCircle2 size={18}/> Nenhuma decisão operacional pendente.</div>}
        </div>
      </article>

      <article className="action-panel action-wide">
        <div className="section-head">
          <div><span className="eyebrow">51–52 · REPOSIÇÃO AUTOMÁTICA</span><h2>Sugestões e compra em lote</h2></div>
          <PackagePlus size={19}/>
        </div>

        <div className="action-toolbar">
          <label>Histórico
            <select value={windowDays} onChange={e=>setWindowDays(Number(e.target.value))}>
              <option value={14}>14 dias</option><option value={30}>30 dias</option><option value={60}>60 dias</option><option value={90}>90 dias</option>
            </select>
          </label>
          <label>Variação projetada
            <select value={variation} onChange={e=>setVariation(Number(e.target.value))}>
              <option value={0}>0%</option><option value={10}>+10%</option><option value={20}>+20%</option><option value={30}>+30%</option>
            </select>
          </label>
          <div className="action-batch-summary">
            <span>{selected.length} selecionado{selected.length===1?'':'s'}</span>
            <strong>{money(selectedCost)}</strong>
          </div>
          <button className="primary compact" disabled={readOnly||saving||selected.length===0} onClick={()=>void createBatchDrafts()}>
            Gerar rascunhos
          </button>
        </div>

        <div className="replenishment-list">
          {suggestions.map(item=><label className={'replenishment-row '+(item.supplierRequired?'supplier-missing':'')} key={item.productId}>
            <input type="checkbox" disabled={readOnly||item.supplierRequired} checked={selected.includes(item.productId)} onChange={()=>toggleSuggestion(item.productId)}/>
            <div className="replenishment-main">
              <div><strong>{item.productName}</strong><small>{item.sku} · {item.category}</small></div>
              <div className="replenishment-stock">
                <span>Saldo <b>{qty(Number(item.currentStock))}</b></span>
                <span>Reservado <b>{qty(Number(item.reservedQuantity))}</b></span>
                <span>A caminho <b>{qty(Number(item.incomingQuantity))}</b></span>
                <span>Disponível <b>{qty(Number(item.availableToPromise))}</b></span>
              </div>
            </div>
            <div className="replenishment-result">
              <span className={'risk-tag '+item.riskLevel.toLowerCase()}>{item.riskLevel}</span>
              <strong>{qty(Number(item.recommendedQuantity))} un.</strong>
              <small>{item.supplierRequired?'Fornecedor manual necessário':item.supplierName+' · '+item.supplierLeadTimeDays+' d'}</small>
              <b>{money(Number(item.estimatedCost))}</b>
            </div>
          </label>)}
          {!loading&&suggestions.length===0&&<div className="action-empty"><CheckCircle2 size={18}/> Nenhuma reposição necessária com a janela atual.</div>}
        </div>
      </article>

      <article className="action-panel">
        <div className="section-head"><div><span className="eyebrow">53 · APROVAÇÃO CONTROLADA</span><h2>Pedidos assistidos</h2></div><ShieldCheck size={19}/></div>
        <div className="approval-list">
          {approvals.map(item=><div className="approval-row" key={item.purchaseOrderId}>
            <div>
              <strong>#{item.purchaseOrderId} · {item.supplierName}</strong>
              <small>{money(Number(item.totalAmount))} · criado por {item.createdBy}</small>
              {item.requestedBy&&<small>solicitado por {item.requestedBy} · {dateTime(item.requestedAt)}</small>}
            </div>
            <span className={'approval-status '+item.approvalStatus.toLowerCase().replace('_','-')}>{item.approvalStatus.replace('_',' ')}</span>
            {!readOnly&&(item.approvalStatus==='NOT_REQUESTED'||item.approvalStatus==='REJECTED')&&
              <button className="ghost compact" disabled={saving} onClick={()=>void approvalAction(item.purchaseOrderId,'request')}>Pedir aprovação</button>}
            {!readOnly&&isAdmin&&item.approvalStatus==='PENDING'&&<div className="approval-actions">
              <button className="ghost compact approve" disabled={saving} onClick={()=>void approvalAction(item.purchaseOrderId,'approve')}><CheckCircle2 size={14}/> Aprovar</button>
              <button className="ghost compact reject" disabled={saving} onClick={()=>void approvalAction(item.purchaseOrderId,'reject')}><XCircle size={14}/> Rejeitar</button>
            </div>}
          </div>)}
          {!loading&&approvals.length===0&&<div className="empty-state">Nenhum pedido assistido em rascunho.</div>}
        </div>
      </article>

      <article className="action-panel">
        <div className="section-head"><div><span className="eyebrow">54 · RESERVAS</span><h2>Estoque comprometido</h2></div><Boxes size={19}/></div>

        {!readOnly&&<form className="reservation-form" onSubmit={createReservation}>
          <label>Produto
            <select value={reservationForm.productId} onChange={e=>setReservationForm({...reservationForm,productId:e.target.value})}>
              <option value="">Selecione</option>
              {products.map(item=><option key={item.id} value={item.id}>{item.name} · {item.sku} · saldo {qty(item.currentStock)}</option>)}
            </select>
          </label>
          <div className="reservation-inline">
            <label>Quantidade<input type="number" min="0.001" step="0.001" value={reservationForm.quantity} onChange={e=>setReservationForm({...reservationForm,quantity:e.target.value})}/></label>
            <label>Expira em<input type="datetime-local" value={reservationForm.expiresAt} onChange={e=>setReservationForm({...reservationForm,expiresAt:e.target.value})}/></label>
          </div>
          <label>Referência<input maxLength={120} value={reservationForm.referenceCode} onChange={e=>setReservationForm({...reservationForm,referenceCode:e.target.value})} placeholder="Ex.: PEDIDO-CLIENTE-203"/></label>
          <label>Observação<input maxLength={255} value={reservationForm.notes} onChange={e=>setReservationForm({...reservationForm,notes:e.target.value})} placeholder="Opcional"/></label>
          <button className="primary compact" disabled={saving}>Criar reserva</button>
        </form>}

        <div className="reservation-list">
          {reservations.slice(0,14).map(item=><div className={'reservation-row '+item.status.toLowerCase()} key={item.id}>
            <div><strong>{item.productName}</strong><small>{item.referenceCode} · por {item.reservedBy}</small></div>
            <div><b>{qty(Number(item.quantity))} un.</b><small>disponível {qty(Number(item.availableToPromise))}</small></div>
            <span>{item.status}</span>
            {item.status==='ACTIVE'&&!readOnly&&<button className="ghost compact" disabled={saving} onClick={()=>void cancelReservation(item.id)}>Cancelar</button>}
          </div>)}
          {!loading&&reservations.length===0&&<div className="empty-state">Nenhuma reserva registrada.</div>}
        </div>
      </article>
    </section>
  </>;
}
