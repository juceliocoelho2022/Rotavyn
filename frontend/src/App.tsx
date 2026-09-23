import { useCallback, useEffect, useState, type FormEvent } from 'react';
import { api, type Credentials, type Driver, type Shipment, type ShipmentEvent, type Vehicle } from './api';

type View = 'overview' | 'shipments' | 'fleet';
const labels: Record<string, string> = {
  CREATED: 'Criada', ASSIGNED: 'Atribuída', PICKED_UP: 'Coletada', IN_TRANSIT: 'Em trânsito',
  DELIVERY_ATTEMPTED: 'Tentativa de entrega', EXCEPTION: 'Ocorrência', DELIVERED: 'Entregue', CANCELLED: 'Cancelada'
};
const transitions: Record<string, { type: string; label: string }[]> = {
  CREATED: [{ type: 'CANCEL', label: 'Cancelar' }],
  ASSIGNED: [{ type: 'PICK_UP', label: 'Registrar coleta' }, { type: 'CANCEL', label: 'Cancelar' }],
  PICKED_UP: [{ type: 'DEPART', label: 'Registrar saída' }, { type: 'REPORT_EXCEPTION', label: 'Informar ocorrência' }],
  IN_TRANSIT: [{ type: 'ATTEMPT', label: 'Registrar tentativa' }, { type: 'DELIVER', label: 'Confirmar entrega' }, { type: 'REPORT_EXCEPTION', label: 'Informar ocorrência' }],
  DELIVERY_ATTEMPTED: [{ type: 'RETRY', label: 'Nova tentativa' }, { type: 'DELIVER', label: 'Confirmar entrega' }, { type: 'REPORT_EXCEPTION', label: 'Informar ocorrência' }],
  EXCEPTION: [{ type: 'RESUME', label: 'Retomar entrega' }]
};
const formatDate = (value: string) => new Intl.DateTimeFormat('pt-BR', { dateStyle: 'short', timeStyle: 'short' }).format(new Date(value));

export default function App() {
  const [credentials, setCredentials] = useState<Credentials | null>(null);
  const [view, setView] = useState<View>('overview');
  const [shipments, setShipments] = useState<Shipment[]>([]);
  const [drivers, setDrivers] = useState<Driver[]>([]);
  const [vehicles, setVehicles] = useState<Vehicle[]>([]);
  const [selected, setSelected] = useState<string | null>(null);
  const [history, setHistory] = useState<ShipmentEvent[]>([]);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [note, setNote] = useState('');

  const refresh = useCallback(async (auth: Credentials) => {
    const [nextShipments, nextDrivers, nextVehicles] = await Promise.all([
      api<Shipment[]>(auth, '/shipments'), api<Driver[]>(auth, '/drivers'), api<Vehicle[]>(auth, '/vehicles')
    ]);
    setShipments(nextShipments); setDrivers(nextDrivers); setVehicles(nextVehicles);
  }, []);

  useEffect(() => {
    if (!credentials || !selected) { setHistory([]); return; }
    api<ShipmentEvent[]>(credentials, `/shipments/${selected}/events`).then(setHistory).catch(e => setError(e.message));
  }, [credentials, selected, shipments]);

  async function login(event: FormEvent) {
    event.preventDefault(); setBusy(true); setError('');
    const auth = { username, password };
    try { await refresh(auth); setCredentials(auth); setPassword(''); }
    catch (e) { setError((e as Error).message); }
    finally { setBusy(false); }
  }

  async function submit(action: () => Promise<unknown>, success: string) {
    if (!credentials) return;
    setBusy(true); setError(''); setNotice('');
    try { await action(); await refresh(credentials); setNotice(success); return true; }
    catch (e) { setError((e as Error).message); return false; }
    finally { setBusy(false); }
  }

  function logout() {
    setCredentials(null); setSelected(null); setShipments([]); setDrivers([]); setVehicles([]); setPassword(''); setNotice(''); setError('');
  }

  if (!credentials) return <main className="login-page">
    <div className="brand"><span className="brand-icon">↗</span> rotavyn<span className="brand-dot">.</span></div>
    <div className="login-layout">
      <div className="login-intro"><div className="eyebrow">PLATAFORMA DE OPERAÇÕES</div>
        <h1>O movimento começa com clareza.</h1>
        <p>Organize remessas, despache sua frota e acompanhe cada etapa em um só lugar.</p>
        <div className="route-art"><span>ORIGEM</span><div className="route-line"><i></i><i></i><i></i></div><span>DESTINO</span></div>
      </div>
      <form className="login-card" onSubmit={login}>
        <div className="eyebrow">ACESSO DE DEMONSTRAÇÃO</div><h2>Entrar no painel</h2>
        <p>Use um dos operadores configurados no seu arquivo <code>.env</code>.</p>
        <label>Usuário<input value={username} onChange={e => setUsername(e.target.value)} required autoComplete="username" placeholder="operator-a" /></label>
        <label>Senha<input type="password" value={password} onChange={e => setPassword(e.target.value)} required autoComplete="current-password" placeholder="Sua senha de demonstração" /></label>
        {error && <div className="alert error" role="alert">{error}</div>}
        <button className="primary" disabled={busy}>{busy ? 'Conectando…' : 'Acessar operações →'}</button>
        <small>Ambiente de desenvolvimento · Use somente dados fictícios.</small>
      </form>
    </div>
    <footer>Rotavyn · Inteligência para mover o mundo.</footer>
  </main>;

  const current = shipments.find(item => item.id === selected);
  const overdue = shipments.filter(item => new Date(item.promisedAt) < new Date() && !['DELIVERED', 'CANCELLED'].includes(item.status)).length;
  const active = shipments.filter(item => !['DELIVERED', 'CANCELLED'].includes(item.status)).length;

  return <div className="app-shell">
    <aside className="sidebar">
      <div className="brand"><span className="brand-icon">↗</span> rotavyn<span className="brand-dot">.</span></div>
      <div className="side-caption">WORKSPACE</div>
      <nav aria-label="Navegação principal">
        <button className={view === 'overview' ? 'active' : ''} onClick={() => setView('overview')}>▦ <span>Visão geral</span></button>
        <button className={view === 'shipments' ? 'active' : ''} onClick={() => setView('shipments')}>▤ <span>Remessas</span></button>
        <button className={view === 'fleet' ? 'active' : ''} onClick={() => setView('fleet')}>▰ <span>Frota</span></button>
      </nav>
      <div className="sidebar-bottom"><span className="avatar">{credentials.username.slice(0, 1).toUpperCase()}</span>
        <div><strong>{credentials.username}</strong><small>Operador</small></div><button onClick={logout} title="Sair" aria-label="Sair">↪</button></div>
    </aside>
    <main className="workspace">
      <header className="topbar"><span>Operações <span className="breadcrumb">/ {view === 'overview' ? 'Visão geral' : view === 'shipments' ? 'Remessas' : 'Frota'}</span></span><span className="environment">● Ambiente de demonstração</span></header>
      <div className="content">
        {error && <div className="alert error" role="alert">{error}<button onClick={() => setError('')} aria-label="Fechar aviso">×</button></div>}
        {notice && <div className="alert success" role="status">{notice}<button onClick={() => setNotice('')} aria-label="Fechar aviso">×</button></div>}
        {view === 'overview' && <>
          <div className="page-head"><div><div className="eyebrow">CENTRAL DE OPERAÇÕES</div><h1>Visão geral</h1><p>Uma visão clara de tudo o que está em movimento.</p></div><button className="primary" onClick={() => setView('shipments')}>+ Nova remessa</button></div>
          <div className="stats"><div className="stat"><span>Total de remessas</span><strong>{shipments.length}</strong><small>Registradas nesta empresa</small></div><div className="stat"><span>Em andamento</span><strong>{active}</strong><small>Aguardando conclusão</small></div><div className="stat"><span>Atrasadas</span><strong>{overdue}</strong><small>Prazo de entrega ultrapassado</small></div><div className="stat"><span>Frota</span><strong>{drivers.length}<em> / {vehicles.length}</em></strong><small>Motoristas / veículos</small></div></div>
          <div className="panel"><div className="panel-heading"><h2>Remessas recentes</h2><button className="text-button" onClick={() => setView('shipments')}>Ver todas →</button></div><ShipmentTable items={shipments.slice(0, 6)} onSelect={id => { setSelected(id); setView('shipments'); }} /></div>
        </>}
        {view === 'shipments' && <>
          <div className="page-head"><div><div className="eyebrow">ACOMPANHAMENTO</div><h1>Remessas</h1><p>Crie, despache e acompanhe o histórico das entregas.</p></div><span className="count-pill">{shipments.length} remessa(s)</span></div>
          <div className="two-column"><div className="panel"><h2>Nova remessa</h2><p className="muted">Preencha os dados para começar o acompanhamento.</p>
            <form className="form-grid" onSubmit={e => { e.preventDefault(); const form = new FormData(e.currentTarget); const target = e.currentTarget; void submit(() => api<Shipment>(credentials, '/shipments', {
              trackingCode: form.get('trackingCode'), senderName: form.get('senderName'), recipientName: form.get('recipientName'), destinationAddress: form.get('destinationAddress'), destinationCountry: String(form.get('destinationCountry')).toUpperCase(), promisedAt: new Date(String(form.get('promisedAt'))).toISOString()
            }), 'Remessa criada.').then(ok => { if (ok) target.reset(); }); }}>
              <label>Código de rastreamento<input name="trackingCode" required maxLength={80} placeholder="ROT-2026-001" /></label>
              <label>Remetente<input name="senderName" required maxLength={160} placeholder="Empresa de origem" /></label>
              <label>Destinatário<input name="recipientName" required maxLength={160} placeholder="Nome do destinatário" /></label>
              <label>Endereço de destino<input name="destinationAddress" required placeholder="Rua, número e cidade" /></label>
              <div className="form-row"><label>País (ISO)<input name="destinationCountry" required minLength={2} maxLength={2} defaultValue="BR" /></label><label>Prazo de entrega<input name="promisedAt" type="datetime-local" required /></label></div>
              <button className="primary" disabled={busy}>Criar remessa</button>
            </form>
          </div><div className="panel panel-highlight"><div className="eyebrow">FLUXO DE ENTREGA</div><h2>Cada etapa, no seu lugar.</h2><p>Cadastre a remessa, atribua motorista e veículo, registre os eventos e acompanhe a entrega até o fim.</p><div className="steps"><span>01 · Criar</span><span>02 · Despachar</span><span>03 · Acompanhar</span></div></div></div>
          <div className="panel"><div className="panel-heading"><h2>Todas as remessas</h2><button className="text-button" onClick={() => void submit(async () => {}, 'Lista atualizada.')}>Atualizar ↻</button></div><ShipmentTable items={shipments} onSelect={setSelected} /></div>
          {current && <div className="panel detail"><div className="panel-heading"><div><div className="eyebrow">DETALHE DA REMESSA</div><h2>{current.trackingCode}</h2></div><button className="text-button" onClick={() => setSelected(null)}>Fechar ×</button></div>
            <div className="detail-grid"><div><small>DESTINATÁRIO</small><strong>{current.recipientName}</strong></div><div><small>DESTINO</small><strong>{current.destinationAddress}, {current.destinationCountry}</strong></div><div><small>PRAZO</small><strong>{formatDate(current.promisedAt)}</strong></div><div><small>STATUS</small><strong>{labels[current.status] || current.status}</strong></div></div>
            {current.status === 'CREATED' && <form className="assignment" onSubmit={e => { e.preventDefault(); const form = new FormData(e.currentTarget); void submit(() => api(credentials, `/shipments/${current.id}/assignment`, { driverId: form.get('driverId'), vehicleId: form.get('vehicleId'), idempotencyKey: crypto.randomUUID() }), 'Remessa despachada.'); }}><h3>Despachar remessa</h3><div className="form-row"><label>Motorista<select name="driverId" required defaultValue=""><option value="" disabled>Selecionar motorista</option>{drivers.map(d => <option key={d.id} value={d.id}>{d.displayName}</option>)}</select></label><label>Veículo<select name="vehicleId" required defaultValue=""><option value="" disabled>Selecionar veículo</option>{vehicles.map(v => <option key={v.id} value={v.id}>{v.plate}</option>)}</select></label></div><button className="primary" disabled={busy || !drivers.length || !vehicles.length}>Confirmar despacho</button>{(!drivers.length || !vehicles.length) && <small>Cadastre motorista e veículo na seção Frota.</small>}</form>}
            {!!transitions[current.status]?.length && <div className="event-actions"><h3>Registrar etapa</h3><label>Observação (opcional)<input value={note} onChange={e => setNote(e.target.value)} placeholder="Detalhes desta etapa" /></label><div className="button-row">{transitions[current.status].map(item => <button className="secondary" disabled={busy} key={item.type} onClick={() => void submit(() => api(credentials, `/shipments/${current.id}/events`, { eventType: item.type, idempotencyKey: crypto.randomUUID(), note }), 'Etapa registrada.').then(ok => { if (ok) setNote(''); }))}>{item.label}</button>)}</div></div>}
            <h3>Histórico</h3>{history.length ? <ol className="timeline">{history.map(event => <li key={event.id}><strong>{event.eventType.replaceAll('_', ' ')}</strong><span>{formatDate(event.occurredAt)}</span>{event.note && <p>{event.note}</p>}</li>)}</ol> : <p className="muted">Nenhum evento registrado.</p>}
          </div>}
        </>}
        {view === 'fleet' && <><div className="page-head"><div><div className="eyebrow">RECURSOS DA OPERAÇÃO</div><h1>Frota</h1><p>Motoristas e veículos disponíveis para o despacho.</p></div></div>
          <div className="two-column"><div className="panel"><h2>Motoristas <span className="count-pill">{drivers.length}</span></h2><form className="inline-form" onSubmit={e => { e.preventDefault(); const target = e.currentTarget; const name = String(new FormData(target).get('displayName')); void submit(() => api(credentials, '/drivers', { displayName: name }), 'Motorista cadastrado.').then(ok => { if (ok) target.reset(); }); }}><label>Nome do motorista<input name="displayName" required maxLength={160} placeholder="Nome completo" /></label><button className="primary" disabled={busy}>Adicionar</button></form><ul className="fleet-list">{drivers.map(d => <li key={d.id}><span className="fleet-icon">◉</span>{d.displayName}</li>)}</ul>{!drivers.length && <p className="muted">Nenhum motorista cadastrado.</p>}</div>
          <div className="panel"><h2>Veículos <span className="count-pill">{vehicles.length}</span></h2><form className="inline-form" onSubmit={e => { e.preventDefault(); const target = e.currentTarget; const plate = String(new FormData(target).get('plate')).toUpperCase(); void submit(() => api(credentials, '/vehicles', { plate }), 'Veículo cadastrado.').then(ok => { if (ok) target.reset(); }); }}><label>Placa do veículo<input name="plate" required maxLength={32} placeholder="ABC1D23" /></label><button className="primary" disabled={busy}>Adicionar</button></form><ul className="fleet-list">{vehicles.map(v => <li key={v.id}><span className="fleet-icon">▰</span>{v.plate}</li>)}</ul>{!vehicles.length && <p className="muted">Nenhum veículo cadastrado.</p>}</div></div>
        </>}
      </div>
    </main>
  </div>;
}

function ShipmentTable({ items, onSelect }: { items: Shipment[]; onSelect: (id: string) => void }) {
  if (!items.length) return <div className="empty">Ainda não há remessas nesta empresa.</div>;
  return <div className="table-wrap"><table><thead><tr><th>RASTREAMENTO</th><th>DESTINATÁRIO</th><th>DESTINO</th><th>PRAZO</th><th>STATUS</th><th></th></tr></thead><tbody>{items.map(s => <tr key={s.id}><td><strong>{s.trackingCode}</strong></td><td>{s.recipientName}</td><td>{s.destinationCountry}</td><td>{formatDate(s.promisedAt)}</td><td><span className={`status ${s.status.toLowerCase()}`}>{labels[s.status] || s.status}</span></td><td><button className="text-button" onClick={() => onSelect(s.id)}>Detalhes →</button></td></tr>)}</tbody></table></div>;
}
