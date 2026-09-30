import { useEffect, useMemo, useState } from 'react';
import { AlertTriangle, CheckCircle2, ClipboardCheck, Clock3, RefreshCw, Settings2, ShieldCheck, XCircle } from 'lucide-react';
import { apiFetch, isReadOnlySession, readAuthSession } from './auth';

type Policy={
  productId:number;sku:string;productName:string;enabled:boolean;targetCoverageDays:number;
  safetyStockMultiplier:number;minimumOrderQuantity:number;orderMultiple:number;
  preferredSupplierId:number|null;preferredSupplierName:string|null;updatedBy:string|null;updatedAt:string|null;
};
type ExceptionItem={
  id:number;productId:number;sku:string;productName:string;exceptionType:string;reason:string;status:string;
  startsAt:string;expiresAt:string;createdBy:string;cancelledBy:string|null;cancelledAt:string|null;createdAt:string;
};
type CycleCount={
  productId:number;sku:string;productName:string;category:string;abcClass:string;stockValue:number;
  lastCountedAt:string|null;lastDifferenceQuantity:number|null;frequencyDays:number;daysSinceLastCount:number|null;
  daysOverdue:number|null;priority:string;paused:boolean;
};
type GovernedAction={
  key:string;type:string;severity:string;title:string;description:string;value:string;actionTarget:string;status:string;
  firstSeenAt:string;lastSeenAt:string;acknowledgedAt:string|null;acknowledgedBy:string|null;
  acknowledgementNote:string|null;ageHours:number|null;slaHours:number|null;slaBreached:boolean;
};
type Summary={
  openActions:number;acknowledgedActions:number;slaBreaches:number;activeExceptions:number;dueCycleCounts:number;actions:GovernedAction[];
};
type Supplier={id:number;name:string;active:boolean};

const dateTime=(value:string|null)=>value?new Date(value).toLocaleString('pt-BR'):'—';
const money=(value:number)=>new Intl.NumberFormat('pt-BR',{style:'currency',currency:'BRL'}).format(value||0);

async function apiMessage(response:Response,fallback:string){
  const body=await response.json().catch(()=>null);
  return String(body?.message||body?.detail||fallback);
}

export default function GovernancePanel(){
  const API_URL=import.meta.env.VITE_API_URL || 'https://nexo-estoque-api-production.up.railway.app';
  const session=readAuthSession();
  const readOnly=isReadOnlySession();
  const isAdmin=session?.role==='ADMIN';

  const [policies,setPolicies]=useState<Policy[]>([]);
  const [exceptions,setExceptions]=useState<ExceptionItem[]>([]);
  const [cycleCounts,setCycleCounts]=useState<CycleCount[]>([]);
  const [actions,setActions]=useState<GovernedAction[]>([]);
  const [summary,setSummary]=useState<Summary|null>(null);
  const [suppliers,setSuppliers]=useState<Supplier[]>([]);
  const [loading,setLoading]=useState(true);
  const [saving,setSaving]=useState(false);
  const [error,setError]=useState('');
  const [feedback,setFeedback]=useState('');
  const [refreshKey,setRefreshKey]=useState(0);
  const [policyProductId,setPolicyProductId]=useState('');
  const [policyForm,setPolicyForm]=useState({
    enabled:true,targetCoverageDays:'14',safetyStockMultiplier:'1',minimumOrderQuantity:'1',orderMultiple:'1',preferredSupplierId:''
  });
  const [exceptionForm,setExceptionForm]=useState({
    productId:'',exceptionType:'REPLENISHMENT_PAUSE',reason:'',expiresAt:''
  });

  useEffect(()=>{
    let active=true;
    setLoading(true);setError('');
    Promise.all([
      apiFetch(API_URL+'/api/v1/governance/policies'),
      apiFetch(API_URL+'/api/v1/governance/exceptions?status=ACTIVE'),
      apiFetch(API_URL+'/api/v1/governance/cycle-counts?limit=200'),
      apiFetch(API_URL+'/api/v1/governance/actions?includeResolved=false'),
      apiFetch(API_URL+'/api/v1/governance/summary'),
      apiFetch(API_URL+'/api/v1/suppliers')
    ]).then(async responses=>{
      if(responses.some(response=>!response.ok)) throw new Error('Não foi possível carregar a governança operacional.');
      const data=await Promise.all(responses.map(response=>response.json()));
      if(!active) return;
      const loadedPolicies=Array.isArray(data[0])?data[0]:[];
      setPolicies(loadedPolicies);
      setExceptions(Array.isArray(data[1])?data[1]:[]);
      setCycleCounts(Array.isArray(data[2])?data[2]:[]);
      setActions(Array.isArray(data[3])?data[3]:[]);
      setSummary(data[4]||null);
      setSuppliers(Array.isArray(data[5])?data[5]:[]);
      if(!policyProductId&&loadedPolicies.length){
        selectPolicy(String(loadedPolicies[0].productId),loadedPolicies);
      }
      if(!exceptionForm.productId&&loadedPolicies.length){
        setExceptionForm(current=>({...current,productId:String(loadedPolicies[0].productId)}));
      }
    }).catch(err=>{
      if(active) setError(err instanceof Error?err.message:'Governança indisponível.');
    }).finally(()=>{if(active)setLoading(false)});
    return()=>{active=false};
  },[refreshKey]);

  const dueCounts=useMemo(
    ()=>cycleCounts.filter(item=>!item.paused&&Number(item.daysOverdue||0)>0),
    [cycleCounts]
  );

  function selectPolicy(value:string,source=policies){
    setPolicyProductId(value);
    const found=source.find(item=>String(item.productId)===value);
    if(!found) return;
    setPolicyForm({
      enabled:found.enabled,
      targetCoverageDays:String(found.targetCoverageDays),
      safetyStockMultiplier:String(found.safetyStockMultiplier),
      minimumOrderQuantity:String(found.minimumOrderQuantity),
      orderMultiple:String(found.orderMultiple),
      preferredSupplierId:found.preferredSupplierId==null?'':String(found.preferredSupplierId)
    });
  }

  async function savePolicy(e:React.FormEvent){
    e.preventDefault();
    if(!isAdmin||!policyProductId) return;
    setSaving(true);setError('');setFeedback('');
    try{
      const response=await apiFetch(API_URL+'/api/v1/governance/policies/'+policyProductId,{
        method:'PUT',
        headers:{'Content-Type':'application/json'},
        body:JSON.stringify({
          enabled:policyForm.enabled,
          targetCoverageDays:Number(policyForm.targetCoverageDays),
          safetyStockMultiplier:Number(policyForm.safetyStockMultiplier),
          minimumOrderQuantity:Number(policyForm.minimumOrderQuantity),
          orderMultiple:Number(policyForm.orderMultiple),
          preferredSupplierId:policyForm.preferredSupplierId?Number(policyForm.preferredSupplierId):null
        })
      });
      if(!response.ok) throw new Error(await apiMessage(response,'Não foi possível salvar a política.'));
      setFeedback('Política de reposição atualizada.');
      setRefreshKey(value=>value+1);
    }catch(err){
      setError(err instanceof Error?err.message:'Não foi possível salvar a política.');
    }finally{setSaving(false)}
  }

  async function createException(e:React.FormEvent){
    e.preventDefault();
    if(readOnly) return;
    if(!exceptionForm.productId||!exceptionForm.reason.trim()||!exceptionForm.expiresAt){
      setError('Informe produto, motivo e validade da exceção.');
      return;
    }
    setSaving(true);setError('');setFeedback('');
    try{
      const response=await apiFetch(API_URL+'/api/v1/governance/exceptions',{
        method:'POST',
        headers:{'Content-Type':'application/json'},
        body:JSON.stringify({
          productId:Number(exceptionForm.productId),
          exceptionType:exceptionForm.exceptionType,
          reason:exceptionForm.reason.trim(),
          expiresAt:exceptionForm.expiresAt
        })
      });
      if(!response.ok) throw new Error(await apiMessage(response,'Não foi possível criar a exceção.'));
      setExceptionForm(current=>({...current,reason:'',expiresAt:''}));
      setFeedback('Exceção operacional registrada com validade definida.');
      setRefreshKey(value=>value+1);
    }catch(err){
      setError(err instanceof Error?err.message:'Não foi possível criar a exceção.');
    }finally{setSaving(false)}
  }

  async function cancelException(id:number){
    if(readOnly) return;
    setSaving(true);setError('');setFeedback('');
    try{
      const response=await apiFetch(API_URL+'/api/v1/governance/exceptions/'+id+'/cancel',{method:'POST'});
      if(!response.ok) throw new Error(await apiMessage(response,'Não foi possível cancelar a exceção.'));
      setFeedback('Exceção cancelada.');
      setRefreshKey(value=>value+1);
    }catch(err){
      setError(err instanceof Error?err.message:'Não foi possível cancelar a exceção.');
    }finally{setSaving(false)}
  }

  async function acknowledge(action:GovernedAction){
    if(readOnly) return;
    const note=window.prompt('Observação para o reconhecimento da ação (opcional):','')??null;
    if(note===null) return;
    setSaving(true);setError('');setFeedback('');
    try{
      const response=await apiFetch(API_URL+'/api/v1/governance/actions/'+encodeURIComponent(action.key)+'/acknowledge',{
        method:'POST',
        headers:{'Content-Type':'application/json'},
        body:JSON.stringify({note:note.trim()||null})
      });
      if(!response.ok) throw new Error(await apiMessage(response,'Não foi possível reconhecer a ação.'));
      setFeedback('Ação reconhecida e autoria registrada.');
      setRefreshKey(value=>value+1);
    }catch(err){
      setError(err instanceof Error?err.message:'Não foi possível reconhecer a ação.');
    }finally{setSaving(false)}
  }

  return <>
    <header className="products-head governance-head">
      <div>
        <span className="eyebrow">GOVERNANÇA OPERACIONAL</span>
        <h1>Regra, exceção e responsabilidade.</h1>
        <p>Políticas de reposição, pausas controladas, contagem cíclica e SLA operacional com autoria e histórico.</p>
      </div>
      <button className="ghost compact governance-refresh" onClick={()=>setRefreshKey(value=>value+1)} disabled={loading}>
        <RefreshCw size={15}/> Atualizar
      </button>
    </header>

    {error&&<div className="product-feedback warning">{error}</div>}
    {feedback&&<div className="product-feedback success">{feedback}</div>}

    <section className="governance-kpis">
      <article><AlertTriangle/><span>Ações abertas</span><strong>{loading?'—':summary?.openActions||0}</strong><small>{summary?.slaBreaches||0} fora do SLA</small></article>
      <article><CheckCircle2/><span>Reconhecidas</span><strong>{loading?'—':summary?.acknowledgedActions||0}</strong><small>com autoria registrada</small></article>
      <article><Clock3/><span>Exceções ativas</span><strong>{loading?'—':summary?.activeExceptions||0}</strong><small>pausas temporárias controladas</small></article>
      <article><ClipboardCheck/><span>Contagens vencidas</span><strong>{loading?'—':summary?.dueCycleCounts||0}</strong><small>{dueCounts.length} exigem inventário</small></article>
    </section>

    <section className="governance-grid">
      <article className="governance-panel governance-wide">
        <div className="section-head"><div><span className="eyebrow">64 · SLA E RESPONSABILIDADE</span><h2>Ações governadas</h2></div><ShieldCheck size={19}/></div>
        <div className="governance-action-list">
          {actions.slice(0,18).map(item=><div className={'governance-action '+(item.slaBreached?'breached':'')} key={item.key}>
            <span className={'governance-severity '+item.severity.toLowerCase()}>{item.severity}</span>
            <div><strong>{item.title}</strong><small>{item.description}</small><small>Aberta há {item.ageHours??0} h · SLA {item.slaHours??'—'} h{item.acknowledgedBy?' · reconhecida por '+item.acknowledgedBy:''}</small></div>
            <b>{item.value}</b>
            <span className={'governance-status '+item.status.toLowerCase()}>{item.status}</span>
            {item.status==='OPEN'&&!readOnly&&<button className="ghost compact" disabled={saving} onClick={()=>void acknowledge(item)}>Reconhecer</button>}
          </div>)}
          {!loading&&actions.length===0&&<div className="governance-empty"><CheckCircle2 size={18}/> Nenhuma ação governada pendente.</div>}
        </div>
      </article>

      <article className="governance-panel">
        <div className="section-head"><div><span className="eyebrow">61 · POLÍTICAS</span><h2>Reposição por produto</h2></div><Settings2 size={19}/></div>
        <form className="governance-form" onSubmit={savePolicy}>
          <label>Produto
            <select value={policyProductId} onChange={e=>selectPolicy(e.target.value)}>
              {policies.map(item=><option key={item.productId} value={item.productId}>{item.productName} · {item.sku}</option>)}
            </select>
          </label>
          <div className="governance-inline">
            <label>Cobertura alvo<input type="number" min="1" max="365" value={policyForm.targetCoverageDays} onChange={e=>setPolicyForm({...policyForm,targetCoverageDays:e.target.value})}/></label>
            <label>Segurança ×<input type="number" min="0" max="10" step="0.1" value={policyForm.safetyStockMultiplier} onChange={e=>setPolicyForm({...policyForm,safetyStockMultiplier:e.target.value})}/></label>
          </div>
          <div className="governance-inline">
            <label>Pedido mínimo<input type="number" min="0.001" step="0.001" value={policyForm.minimumOrderQuantity} onChange={e=>setPolicyForm({...policyForm,minimumOrderQuantity:e.target.value})}/></label>
            <label>Múltiplo<input type="number" min="0.001" step="0.001" value={policyForm.orderMultiple} onChange={e=>setPolicyForm({...policyForm,orderMultiple:e.target.value})}/></label>
          </div>
          <label>Fornecedor preferencial
            <select value={policyForm.preferredSupplierId} onChange={e=>setPolicyForm({...policyForm,preferredSupplierId:e.target.value})}>
              <option value="">Histórico automático</option>
              {suppliers.filter(item=>item.active!==false).map(item=><option key={item.id} value={item.id}>{item.name}</option>)}
            </select>
          </label>
          <label className="governance-check"><input type="checkbox" checked={policyForm.enabled} onChange={e=>setPolicyForm({...policyForm,enabled:e.target.checked})}/> Reposição automática habilitada</label>
          <button className="primary compact" disabled={!isAdmin||saving||!policyProductId} title={!isAdmin?'Somente Admin pode alterar políticas':undefined}>Salvar política</button>
          {!isAdmin&&<small className="governance-help">Consulta disponível. Alterações de política são exclusivas de Admin.</small>}
        </form>
      </article>

      <article className="governance-panel">
        <div className="section-head"><div><span className="eyebrow">62 · EXCEÇÕES</span><h2>Pausas temporárias</h2></div><Clock3 size={19}/></div>
        {!readOnly&&<form className="governance-form" onSubmit={createException}>
          <label>Produto
            <select value={exceptionForm.productId} onChange={e=>setExceptionForm({...exceptionForm,productId:e.target.value})}>
              {policies.map(item=><option key={item.productId} value={item.productId}>{item.productName} · {item.sku}</option>)}
            </select>
          </label>
          <label>Tipo<select value={exceptionForm.exceptionType} onChange={e=>setExceptionForm({...exceptionForm,exceptionType:e.target.value})}>
            <option value="REPLENISHMENT_PAUSE">Pausar reposição</option>
            <option value="COUNTING_PAUSE">Pausar contagem</option>
          </select></label>
          <label>Motivo<input maxLength={255} value={exceptionForm.reason} onChange={e=>setExceptionForm({...exceptionForm,reason:e.target.value})} placeholder="Justificativa obrigatória"/></label>
          <label>Validade<input type="datetime-local" value={exceptionForm.expiresAt} onChange={e=>setExceptionForm({...exceptionForm,expiresAt:e.target.value})}/></label>
          <button className="primary compact" disabled={saving}>Registrar exceção</button>
        </form>}
        <div className="governance-exceptions">
          {exceptions.map(item=><div className="governance-exception" key={item.id}>
            <div><strong>{item.productName}</strong><small>{item.exceptionType==='REPLENISHMENT_PAUSE'?'Reposição pausada':'Contagem pausada'} · até {dateTime(item.expiresAt)}</small><small>{item.reason} · por {item.createdBy}</small></div>
            {!readOnly&&<button className="ghost compact" disabled={saving} onClick={()=>void cancelException(item.id)}><XCircle size={14}/> Cancelar</button>}
          </div>)}
          {!loading&&exceptions.length===0&&<div className="empty-state">Nenhuma exceção ativa.</div>}
        </div>
      </article>

      <article className="governance-panel governance-wide">
        <div className="section-head"><div><span className="eyebrow">63 · CONTAGEM CÍCLICA</span><h2>Inventário por risco</h2></div><ClipboardCheck size={19}/></div>
        <div className="cycle-count-table-wrap">
          <table className="cycle-count-table">
            <thead><tr><th>Produto</th><th>ABC</th><th>Valor</th><th>Última contagem</th><th>Frequência</th><th>Atraso</th><th>Prioridade</th></tr></thead>
            <tbody>
              {cycleCounts.slice(0,30).map(item=><tr key={item.productId}>
                <td><strong>{item.productName}</strong><small>{item.sku} · {item.category}</small></td>
                <td><span className={'abc-badge abc-'+item.abcClass.toLowerCase()}>{item.abcClass}</span></td>
                <td>{money(Number(item.stockValue))}</td>
                <td>{dateTime(item.lastCountedAt)}</td>
                <td>{item.frequencyDays} d</td>
                <td>{item.daysOverdue==null?'—':item.daysOverdue>0?item.daysOverdue+' d':'em dia'}</td>
                <td><span className={'cycle-priority '+item.priority.toLowerCase()}>{item.paused?'PAUSADA':item.priority}</span></td>
              </tr>)}
              {!loading&&cycleCounts.length===0&&<tr><td colSpan={7}><div className="empty-state">Sem sugestões de contagem.</div></td></tr>}
            </tbody>
          </table>
        </div>
      </article>
    </section>
  </>;
}
