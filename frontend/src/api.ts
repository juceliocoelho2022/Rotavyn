export type Driver = { id: string; displayName: string };
export type Vehicle = { id: string; plate: string };
export type Shipment = {
  id: string; trackingCode: string; senderName: string; recipientName: string;
  destinationAddress: string; destinationCountry: string; promisedAt: string;
  status: string; driverId: string | null; vehicleId: string | null;
};
export type ShipmentEvent = { id: string; eventType: string; occurredAt: string; note: string | null };
export type Incident = { shipmentId: string; trackingCode: string; status: string; promisedAt: string; reason: string };
export type Recommendation = { id: string; shipmentId: string; reason: string; recommendation: string; rationale: string; provider: string; status: string; createdAt: string; decidedAt: string | null };
export type Credentials = { username: string; password: string };
const base = import.meta.env.VITE_API_URL || 'http://localhost:8080';

export async function api<T>(credentials: Credentials, path: string, body?: unknown): Promise<T> {
  const response = await fetch(`${base}/api/v1${path}`, {
    method: body === undefined ? 'GET' : 'POST',
    headers: {
      Authorization: `Basic ${btoa(`${credentials.username}:${credentials.password}`)}`,
      ...(body === undefined ? {} : { 'Content-Type': 'application/json' })
    },
    ...(body === undefined ? {} : { body: JSON.stringify(body) })
  });
  if (!response.ok) {
    if (response.status === 401) throw new Error('Usuário ou senha inválidos.');
    if (response.status === 404) throw new Error('Recurso não encontrado nesta empresa.');
    if (response.status === 409) throw new Error('Conflito: verifique se o código ou a chave já existe.');
    if (response.status === 422) throw new Error('Esta mudança de status não é permitida.');
    if (response.status === 400) throw new Error('Confira os campos informados.');
    throw new Error(`Não foi possível concluir a operação (HTTP ${response.status}).`);
  }
  return response.json() as Promise<T>;
}
