CREATE TABLE ai_recommendation (
  id UUID PRIMARY KEY,
  tenant_id UUID NOT NULL,
  shipment_id UUID NOT NULL,
  reason VARCHAR(32) NOT NULL,
  recommendation TEXT NOT NULL,
  rationale TEXT NOT NULL,
  provider VARCHAR(40) NOT NULL,
  status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
  created_at TIMESTAMPTZ NOT NULL,
  decided_at TIMESTAMPTZ,
  decided_by UUID,
  decision_note TEXT,
  UNIQUE (tenant_id, id),
  FOREIGN KEY (tenant_id, shipment_id) REFERENCES shipment (tenant_id, id),
  FOREIGN KEY (tenant_id, decided_by) REFERENCES user_account (tenant_id, id)
);
CREATE INDEX recommendation_tenant_shipment ON ai_recommendation (tenant_id, shipment_id, created_at);

CREATE TABLE follow_up_task (
  id UUID PRIMARY KEY,
  tenant_id UUID NOT NULL,
  recommendation_id UUID NOT NULL,
  shipment_id UUID NOT NULL,
  description TEXT NOT NULL,
  created_at TIMESTAMPTZ NOT NULL,
  status VARCHAR(16) NOT NULL DEFAULT 'OPEN',
  UNIQUE (tenant_id, recommendation_id),
  FOREIGN KEY (tenant_id, recommendation_id) REFERENCES ai_recommendation (tenant_id, id),
  FOREIGN KEY (tenant_id, shipment_id) REFERENCES shipment (tenant_id, id)
);
