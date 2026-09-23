CREATE TABLE tenant (
  id UUID PRIMARY KEY,
  name VARCHAR(160) NOT NULL,
  time_zone VARCHAR(80) NOT NULL DEFAULT 'America/Sao_Paulo'
);
CREATE TABLE user_account (
  id UUID PRIMARY KEY,
  tenant_id UUID NOT NULL REFERENCES tenant(id),
  email VARCHAR(254) NOT NULL,
  password_hash VARCHAR(255) NOT NULL,
  role VARCHAR(32) NOT NULL,
  UNIQUE (tenant_id, email),
  UNIQUE (tenant_id, id)
);
CREATE TABLE driver (
  id UUID PRIMARY KEY,
  tenant_id UUID NOT NULL REFERENCES tenant(id),
  display_name VARCHAR(160) NOT NULL,
  UNIQUE (tenant_id, id)
);
CREATE TABLE vehicle (
  id UUID PRIMARY KEY,
  tenant_id UUID NOT NULL REFERENCES tenant(id),
  plate VARCHAR(32) NOT NULL,
  UNIQUE (tenant_id, id), UNIQUE (tenant_id, plate)
);
CREATE TABLE shipment (
  id UUID PRIMARY KEY,
  tenant_id UUID NOT NULL REFERENCES tenant(id),
  tracking_code VARCHAR(80) NOT NULL,
  sender_name VARCHAR(160) NOT NULL,
  recipient_name VARCHAR(160) NOT NULL,
  destination_address TEXT NOT NULL,
  destination_country CHAR(2) NOT NULL,
  promised_at TIMESTAMPTZ NOT NULL,
  status VARCHAR(32) NOT NULL DEFAULT 'CREATED',
  version BIGINT NOT NULL DEFAULT 0,
  driver_id UUID,
  vehicle_id UUID,
  UNIQUE (tenant_id, id), UNIQUE (tenant_id, tracking_code),
  FOREIGN KEY (tenant_id, driver_id) REFERENCES driver (tenant_id, id),
  FOREIGN KEY (tenant_id, vehicle_id) REFERENCES vehicle (tenant_id, id)
);
CREATE TABLE shipment_event (
  id UUID PRIMARY KEY,
  tenant_id UUID NOT NULL,
  shipment_id UUID NOT NULL,
  actor_id UUID NOT NULL,
  event_type VARCHAR(32) NOT NULL,
  occurred_at TIMESTAMPTZ NOT NULL,
  note TEXT,
  idempotency_key VARCHAR(120) NOT NULL,
  UNIQUE (tenant_id, idempotency_key),
  FOREIGN KEY (tenant_id, shipment_id) REFERENCES shipment (tenant_id, id),
  FOREIGN KEY (tenant_id, actor_id) REFERENCES user_account (tenant_id, id)
);
CREATE INDEX shipment_tenant_status_deadline ON shipment (tenant_id, status, promised_at);
CREATE INDEX shipment_event_history ON shipment_event (tenant_id, shipment_id, occurred_at);
