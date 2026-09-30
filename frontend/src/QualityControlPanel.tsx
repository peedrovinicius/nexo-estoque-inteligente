import { useEffect, useMemo, useState } from 'react';
import { AlertTriangle, Ban, CheckCircle2, ClipboardList, PackageCheck, RefreshCw, Search, ShieldAlert } from 'lucide-react';
import { apiFetch, isReadOnlySession, readAuthSession } from './auth';

type QualitySummary={
  quarantinedBatches:number;blockedBatches:number;openRecalls:number;
  receiptVariances30Days:number;heldQuantity:number;
};
type HeldBatch={
  batchId:number;productId:number;sku:string;productName:string;lotCode:string;
  warehouseName:string;locationCode:string;quantity:number;qualityStatus:'QUARANTINED'|'BLOCKED';
  qualityReason:string|null;qualityUpdatedBy:string|null;qualityUpdatedAt:string|null;
};
type Recall={
  id:number;productId:number;sku:string;productName:string;lotCode:string;reason:string;status:'OPEN'|'CLOSED';
  createdBy:string;createdAt:string;closedBy:string|null;closedAt:string|null;resolution:string|null;
  currentQuantity:number;affectedBatchCount:number;
};
type Impact={
  recall:Recall;currentQuantity:number;receiptQuantity:number;exitedQuantity:number;affectedLocations:number;
  firstReceiptAt:string|null;lastExitAt:string|null;
  batches:{batchId:number;warehouseName:string;locationCode:string;quantitySnapshot:number;currentQuantity:number;currentQualityStatus:string}[];
};
type Variance={
  id:number;purchaseOrderId:number;purchaseOrderItemId:number;purchaseReceiptId:number|null;productId:number;
  sku:string;productName:string;supplierName:string;varianceType:string;quantity:number;reason:string;
  reportedBy:string;createdAt:string;
};
type StockBatch={
  id:number;productId:number;sku:string;productName:string;lotCode:string;quantity:number;
  warehouseName:string;locationCode:string;qualityStatus:string;
};
type OrderSummary={id:number;supplierName:string;status:string;itemCount:number;totalAmount:number};
type OrderItem={id:number;productId:number;sku:string;productName:string;quantity:number;receivedQuantity:number};
type OrderDetails={order:OrderSummary;items:OrderItem[]};

const qty=(value:number)=>new Intl.NumberFormat('pt-BR',{maximumFractionDigits:3}).format(value||0);
const money=(value:number)=>new Intl.NumberFormat('pt-BR',{style:'currency',currency:'BRL'}).format(value||0);
const dateTime=(value:string|null)=>value?new Date(value).toLocaleString('pt-BR'):'—';

async function apiMessage(response:Response,fallback:string){
  const body=await response.json().catch(()=>null);
  return String(body?.message||body?.detail||fallback);
}

export default function QualityControlPanel(){
  const API_URL=import.meta.env.VITE_API_URL || 'https://nexo-estoque-api-production.up.railway.app';
  const session=readAuthSession();
  const readOnly=isReadOnlySession();
  const isAdmin=session?.role==='ADMIN';

  const [summary,setSummary]=useState<QualitySummary|null>(null);
  const [held,setHeld]=useState<HeldBatch[]>([]);
  const [recalls,setRecalls]=useState<Recall[]>([]);
  const [variances,setVariances]=useState<Variance[]>([]);
  const [batches,setBatches]=useState<StockBatch[]>([]);
  const [orders,setOrders]=useState<OrderSummary[]>([]);
  const [orderDetails,setOrderDetails]=useState<OrderDetails|null>(null);
  const [impact,setImpact]=useState<Impact|null>(null);
  const [loading,setLoading]=useState(true);
  const [saving,setSaving]=useState(false);
  const [error,setError]=useState('');
  const [feedback,setFeedback]=useState('');
  const [refreshKey,setRefreshKey]=useState(0);
  const [quarantine,setQuarantine]=useState({batchId:'',reason:''});
  const [recallForm,setRecallForm]=useState({lotKey:'',reason:''});
  const [varianceForm,setVarianceForm]=useState({
    orderId:'',itemId:'',receiptId:'',varianceType:'DAMAGED',quantity:'',reason:''
  });

  useEffect(()=>{
    let active=true;
    setLoading(true);setError('');
    Promise.all([
      apiFetch(API_URL+'/api/v1/quality/summary'),
      apiFetch(API_URL+'/api/v1/quality/batches'),
      apiFetch(API_URL+'/api/v1/quality/recalls'),
      apiFetch(API_URL+'/api/v1/quality/receipt-variances?limit=100'),
      apiFetch(API_URL+'/api/v1/stock/batches'),
      apiFetch(API_URL+'/api/v1/purchase-orders')
    ]).then(async responses=>{
      if(responses.some(response=>!response.ok)) throw new Error('Não foi possível carregar o controle de qualidade.');
      const data=await Promise.all(responses.map(response=>response.json()));
      if(!active) return;
      setSummary(data[0]||null);
      setHeld(Array.isArray(data[1])?data[1]:[]);
      setRecalls(Array.isArray(data[2])?data[2]:[]);
      setVariances(Array.isArray(data[3])?data[3]:[]);
      const loadedBatches=Array.isArray(data[4])?data[4]:[];
      setBatches(loadedBatches);
      const loadedOrders=Array.isArray(data[5])?data[5]:[];
      setOrders(loadedOrders);
      setQuarantine(current=>current.batchId?current:{
        ...current,
        batchId:String(loadedBatches.find((item:any)=>item.qualityStatus==='AVAILABLE')?.id||'')
      });
      const firstLot=loadedBatches[0];
      setRecallForm(current=>current.lotKey?current:{
        ...current,
        lotKey:firstLot?firstLot.productId+'::'+firstLot.lotCode:''
      });
    }).catch(err=>{
      if(active) setError(err instanceof Error?err.message:'Controle de qualidade indisponível.');
    }).finally(()=>{if(active)setLoading(false)});
    return()=>{active=false};
  },[refreshKey]);

  useEffect(()=>{
    let active=true;
    setOrderDetails(null);
    setVarianceForm(current=>({...current,itemId:''}));
    if(!varianceForm.orderId) return()=>{active=false};
    apiFetch(API_URL+'/api/v1/purchase-orders/'+varianceForm.orderId)
      .then(async response=>{
        if(!response.ok) throw new Error(await apiMessage(response,'Pedido indisponível.'));
        return response.json();
      })
      .then(data=>{
        if(active){
          setOrderDetails(data);
          const first=Array.isArray(data?.items)?data.items[0]:null;
          if(first) setVarianceForm(current=>({...current,itemId:String(first.id)}));
        }
      })
      .catch(err=>{if(active)setError(err instanceof Error?err.message:'Pedido indisponível.')});
    return()=>{active=false};
  },[varianceForm.orderId]);

  const availableBatches=useMemo(
    ()=>batches.filter(item=>item.qualityStatus==='AVAILABLE'&&Number(item.quantity)>0),
    [batches]
  );

  const lotOptions=useMemo(()=>{
    const map=new Map<string,StockBatch>();
    for(const item of batches){
      const key=item.productId+'::'+item.lotCode;
      if(!map.has(key)) map.set(key,item);
    }
    return Array.from(map.values()).sort((a,b)=>a.productName.localeCompare(b.productName,'pt-BR'));
  },[batches]);

  async function quarantineBatch(e:React.FormEvent){
    e.preventDefault();
    if(readOnly||!quarantine.batchId||!quarantine.reason.trim()) return;
    setSaving(true);setError('');setFeedback('');
    try{
      const response=await apiFetch(API_URL+'/api/v1/quality/batches/'+quarantine.batchId+'/quarantine',{
        method:'POST',headers:{'Content-Type':'application/json'},
        body:JSON.stringify({reason:quarantine.reason.trim()})
      });
      if(!response.ok) throw new Error(await apiMessage(response,'Não foi possível colocar o lote em quarentena.'));
      setQuarantine({batchId:'',reason:''});
      setFeedback('Lote colocado em quarentena e retirado do FEFO.');
      setRefreshKey(value=>value+1);
    }catch(err){setError(err instanceof Error?err.message:'Não foi possível colocar o lote em quarentena.')}
    finally{setSaving(false)}
  }

  async function releaseBatch(item:HeldBatch){
    if(!isAdmin||item.qualityStatus!=='QUARANTINED') return;
    const reason=window.prompt('Motivo da liberação do lote:','Inspeção concluída');
    if(!reason?.trim()) return;
    setSaving(true);setError('');setFeedback('');
    try{
      const response=await apiFetch(API_URL+'/api/v1/quality/batches/'+item.batchId+'/release',{
        method:'POST',headers:{'Content-Type':'application/json'},
        body:JSON.stringify({reason:reason.trim()})
      });
      if(!response.ok) throw new Error(await apiMessage(response,'Não foi possível liberar o lote.'));
      setFeedback('Lote liberado para operação.');
      setRefreshKey(value=>value+1);
    }catch(err){setError(err instanceof Error?err.message:'Não foi possível liberar o lote.')}
    finally{setSaving(false)}
  }

  async function createRecall(e:React.FormEvent){
    e.preventDefault();
    if(readOnly||!recallForm.lotKey||!recallForm.reason.trim()) return;
    const [productId,...lotParts]=recallForm.lotKey.split('::');
    const lotCode=lotParts.join('::');
    setSaving(true);setError('');setFeedback('');
    try{
      const response=await apiFetch(API_URL+'/api/v1/quality/recalls',{
        method:'POST',headers:{'Content-Type':'application/json'},
        body:JSON.stringify({productId:Number(productId),lotCode,reason:recallForm.reason.trim()})
      });
      if(!response.ok) throw new Error(await apiMessage(response,'Não foi possível abrir o recall.'));
      setRecallForm(current=>({...current,reason:''}));
      setFeedback('Recall aberto. Todos os lotes correspondentes foram bloqueados.');
      setRefreshKey(value=>value+1);
    }catch(err){setError(err instanceof Error?err.message:'Não foi possível abrir o recall.')}
    finally{setSaving(false)}
  }

  async function closeRecall(item:Recall){
    if(!isAdmin||item.status!=='OPEN') return;
    const resolution=window.prompt('Resolução para encerrar o recall:','Análise concluída e lote liberado');
    if(!resolution?.trim()) return;
    setSaving(true);setError('');setFeedback('');
    try{
      const response=await apiFetch(API_URL+'/api/v1/quality/recalls/'+item.id+'/close',{
        method:'POST',headers:{'Content-Type':'application/json'},
        body:JSON.stringify({resolution:resolution.trim()})
      });
      if(!response.ok) throw new Error(await apiMessage(response,'Não foi possível encerrar o recall.'));
      setImpact(null);
      setFeedback('Recall encerrado e estados anteriores dos lotes restaurados.');
      setRefreshKey(value=>value+1);
    }catch(err){setError(err instanceof Error?err.message:'Não foi possível encerrar o recall.')}
    finally{setSaving(false)}
  }

  async function loadImpact(id:number){
    setError('');
    try{
      const response=await apiFetch(API_URL+'/api/v1/quality/recalls/'+id+'/impact');
      if(!response.ok) throw new Error(await apiMessage(response,'Impacto indisponível.'));
      setImpact(await response.json());
    }catch(err){setError(err instanceof Error?err.message:'Impacto indisponível.')}
  }

  async function createVariance(e:React.FormEvent){
    e.preventDefault();
    if(readOnly||!varianceForm.orderId||!varianceForm.itemId||!varianceForm.quantity||!varianceForm.reason.trim()) return;
    setSaving(true);setError('');setFeedback('');
    try{
      const response=await apiFetch(API_URL+'/api/v1/quality/receipt-variances',{
        method:'POST',headers:{'Content-Type':'application/json'},
        body:JSON.stringify({
          purchaseOrderId:Number(varianceForm.orderId),
          purchaseOrderItemId:Number(varianceForm.itemId),
          purchaseReceiptId:varianceForm.receiptId?Number(varianceForm.receiptId):null,
          varianceType:varianceForm.varianceType,
          quantity:Number(varianceForm.quantity),
          reason:varianceForm.reason.trim()
        })
      });
      if(!response.ok) throw new Error(await apiMessage(response,'Não foi possível registrar a divergência.'));
      setVarianceForm(current=>({...current,receiptId:'',quantity:'',reason:''}));
      setFeedback('Divergência de recebimento registrada.');
      setRefreshKey(value=>value+1);
    }catch(err){setError(err instanceof Error?err.message:'Não foi possível registrar a divergência.')}
    finally{setSaving(false)}
  }

  return <>
    <header className="products-head quality-head">
      <div>
        <span className="eyebrow">QUALIDADE & RECALL</span>
        <h1>Controle o lote antes que ele vire problema.</h1>
        <p>Quarentena, recall, bloqueio operacional e divergências de recebimento conectados ao estoque real e à rastreabilidade.</p>
      </div>
      <button className="ghost compact quality-refresh" onClick={()=>setRefreshKey(value=>value+1)} disabled={loading}>
        <RefreshCw size={15}/> Atualizar
      </button>
    </header>

    {error&&<div className="product-feedback warning">{error}</div>}
    {feedback&&<div className="product-feedback success">{feedback}</div>}

    <section className="quality-kpis">
      <article><PackageCheck/><span>Em quarentena</span><strong>{loading?'—':summary?.quarantinedBatches||0}</strong><small>lotes aguardando liberação</small></article>
      <article><Ban/><span>Bloqueados</span><strong>{loading?'—':summary?.blockedBatches||0}</strong><small>{qty(Number(summary?.heldQuantity||0))} unidades retidas</small></article>
      <article><ShieldAlert/><span>Recalls abertos</span><strong>{loading?'—':summary?.openRecalls||0}</strong><small>bloqueio automático de operação</small></article>
      <article><ClipboardList/><span>Divergências 30d</span><strong>{loading?'—':summary?.receiptVariances30Days||0}</strong><small>recebimentos com ocorrência</small></article>
    </section>

    <section className="quality-grid">
      <article className="quality-panel">
        <div className="section-head"><div><span className="eyebrow">66 · QUARENTENA</span><h2>Reter lote</h2></div><PackageCheck size={19}/></div>
        {!readOnly&&<form className="quality-form" onSubmit={quarantineBatch}>
          <label>Lote
            <select value={quarantine.batchId} onChange={e=>setQuarantine({...quarantine,batchId:e.target.value})}>
              <option value="">Selecione</option>
              {availableBatches.map(item=><option key={item.id} value={item.id}>{item.productName} · {item.lotCode} · {item.warehouseName}/{item.locationCode}</option>)}
            </select>
          </label>
          <label>Motivo<input maxLength={255} value={quarantine.reason} onChange={e=>setQuarantine({...quarantine,reason:e.target.value})} placeholder="Ex.: inspeção por avaria"/></label>
          <button className="primary compact" disabled={saving||!quarantine.batchId||!quarantine.reason.trim()}>Colocar em quarentena</button>
        </form>}
        <div className="quality-held-list">
          {held.map(item=><div className="quality-held" key={item.batchId}>
            <div><strong>{item.productName}</strong><small>{item.lotCode} · {item.warehouseName}/{item.locationCode}</small><small>{item.qualityReason||'Sem motivo'} · {qty(Number(item.quantity))} un.</small></div>
            <span className={'quality-status '+item.qualityStatus.toLowerCase()}>{item.qualityStatus}</span>
            {isAdmin&&item.qualityStatus==='QUARANTINED'&&<button className="ghost compact" disabled={saving} onClick={()=>void releaseBatch(item)}>Liberar</button>}
          </div>)}
          {!loading&&held.length===0&&<div className="quality-empty"><CheckCircle2 size={18}/> Nenhum lote retido.</div>}
        </div>
      </article>

      <article className="quality-panel">
        <div className="section-head"><div><span className="eyebrow">67–68 · RECALL</span><h2>Bloqueio e impacto</h2></div><ShieldAlert size={19}/></div>
        {!readOnly&&<form className="quality-form" onSubmit={createRecall}>
          <label>Produto e lote
            <select value={recallForm.lotKey} onChange={e=>setRecallForm({...recallForm,lotKey:e.target.value})}>
              <option value="">Selecione</option>
              {lotOptions.map(item=><option key={item.productId+'::'+item.lotCode} value={item.productId+'::'+item.lotCode}>{item.productName} · {item.lotCode}</option>)}
            </select>
          </label>
          <label>Motivo<input maxLength={255} value={recallForm.reason} onChange={e=>setRecallForm({...recallForm,reason:e.target.value})} placeholder="Motivo do recall"/></label>
          <button className="primary compact" disabled={saving||!recallForm.lotKey||!recallForm.reason.trim()}>Abrir recall</button>
        </form>}
        <div className="quality-recall-list">
          {recalls.slice(0,16).map(item=><div className={'quality-recall '+item.status.toLowerCase()} key={item.id}>
            <div><strong>#{item.id} · {item.productName}</strong><small>Lote {item.lotCode} · {item.affectedBatchCount} posição(ões) · {qty(Number(item.currentQuantity))} un.</small><small>{item.reason} · por {item.createdBy}</small></div>
            <span>{item.status}</span>
            <div className="quality-actions">
              <button className="ghost compact" onClick={()=>void loadImpact(item.id)}><Search size={14}/> Impacto</button>
              {isAdmin&&item.status==='OPEN'&&<button className="ghost compact" disabled={saving} onClick={()=>void closeRecall(item)}>Encerrar</button>}
            </div>
          </div>)}
        </div>
      </article>

      {impact&&<article className="quality-panel quality-wide">
        <div className="section-head"><div><span className="eyebrow">67 · IMPACTO DO RECALL</span><h2>{impact.recall.productName} · {impact.recall.lotCode}</h2></div><AlertTriangle size={19}/></div>
        <div className="impact-kpis">
          <div><span>Saldo atual</span><strong>{qty(Number(impact.currentQuantity))}</strong></div>
          <div><span>Recebido</span><strong>{qty(Number(impact.receiptQuantity))}</strong></div>
          <div><span>Já saiu</span><strong>{qty(Number(impact.exitedQuantity))}</strong></div>
          <div><span>Locais afetados</span><strong>{impact.affectedLocations}</strong></div>
        </div>
        <div className="impact-batches">
          {impact.batches.map(item=><div key={item.batchId}><strong>{item.warehouseName} / {item.locationCode}</strong><span>Atual {qty(Number(item.currentQuantity))} · no recall {qty(Number(item.quantitySnapshot))}</span><b>{item.currentQualityStatus}</b></div>)}
        </div>
        <small className="quality-caption">Primeiro recebimento: {dateTime(impact.firstReceiptAt)} · última saída: {dateTime(impact.lastExitAt)}</small>
      </article>}

      <article className="quality-panel quality-wide">
        <div className="section-head"><div><span className="eyebrow">69 · DIVERGÊNCIA DE RECEBIMENTO</span><h2>Ocorrências do fornecedor</h2></div><ClipboardList size={19}/></div>
        {!readOnly&&<form className="quality-variance-form" onSubmit={createVariance}>
          <label>Pedido
            <select value={varianceForm.orderId} onChange={e=>setVarianceForm({...varianceForm,orderId:e.target.value})}>
              <option value="">Selecione</option>
              {orders.map(item=><option key={item.id} value={item.id}>#{item.id} · {item.supplierName} · {item.status}</option>)}
            </select>
          </label>
          <label>Item
            <select value={varianceForm.itemId} onChange={e=>setVarianceForm({...varianceForm,itemId:e.target.value})} disabled={!orderDetails}>
              <option value="">Selecione</option>
              {orderDetails?.items?.map(item=><option key={item.id} value={item.id}>{item.productName} · {item.sku}</option>)}
            </select>
          </label>
          <label>Tipo<select value={varianceForm.varianceType} onChange={e=>setVarianceForm({...varianceForm,varianceType:e.target.value})}>
            <option value="SHORT">Falta</option><option value="EXCESS">Excesso</option><option value="DAMAGED">Avariado</option><option value="REJECTED">Rejeitado</option><option value="OTHER">Outro</option>
          </select></label>
          <label>Quantidade<input type="number" min="0.001" step="0.001" value={varianceForm.quantity} onChange={e=>setVarianceForm({...varianceForm,quantity:e.target.value})}/></label>
          <label>Recebimento #<input type="number" min="1" value={varianceForm.receiptId} onChange={e=>setVarianceForm({...varianceForm,receiptId:e.target.value})} placeholder="Opcional"/></label>
          <label className="quality-reason">Motivo<input maxLength={255} value={varianceForm.reason} onChange={e=>setVarianceForm({...varianceForm,reason:e.target.value})}/></label>
          <button className="primary compact" disabled={saving||!varianceForm.orderId||!varianceForm.itemId||!varianceForm.quantity||!varianceForm.reason.trim()}>Registrar</button>
        </form>}
        <div className="variance-table-wrap">
          <table className="variance-table">
            <thead><tr><th>Pedido</th><th>Produto</th><th>Fornecedor</th><th>Tipo</th><th>Qtd.</th><th>Motivo</th><th>Registro</th></tr></thead>
            <tbody>
              {variances.map(item=><tr key={item.id}>
                <td>#{item.purchaseOrderId}</td>
                <td><strong>{item.productName}</strong><small>{item.sku}</small></td>
                <td>{item.supplierName}</td>
                <td><span className={'variance-type '+item.varianceType.toLowerCase()}>{item.varianceType}</span></td>
                <td>{qty(Number(item.quantity))}</td>
                <td>{item.reason}</td>
                <td><small>{item.reportedBy}<br/>{dateTime(item.createdAt)}</small></td>
              </tr>)}
              {!loading&&variances.length===0&&<tr><td colSpan={7}><div className="empty-state">Nenhuma divergência registrada.</div></td></tr>}
            </tbody>
          </table>
        </div>
      </article>
    </section>

    <section className="quality-footer-note">
      <strong>70 · CONTROLE DE QUALIDADE</strong>
      <span>FEFO, transferência e recebimento respeitam automaticamente quarentena e recall. Liberação e encerramento são restritos a Admin.</span>
    </section>
  </>;
}
