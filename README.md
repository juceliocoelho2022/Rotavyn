# Rotavyn

**Inteligência para mover o mundo.**

[![Backend CI](https://github.com/juceliocoelho2022/Rotavyn/actions/workflows/backend-ci.yml/badge.svg)](https://github.com/juceliocoelho2022/Rotavyn/actions/workflows/backend-ci.yml)

Rotavyn é uma plataforma de logística multiempresa em desenvolvimento. O projeto começa pela operação de transportadoras: organizar remessas, acompanhar entregas e, em etapas futuras, apoiar o tratamento de ocorrências com recomendações de IA revisadas por pessoas.

> **Estágio atual:** primeiro fluxo de remessas implementado na API. Este repositório ainda não representa um produto pronto para uso em produção.

## O que já funciona

- Cadastro, listagem e consulta de remessas pela API REST; registro e consulta de eventos com atualização transacional do status.
- Isolamento por empresa: o servidor identifica a empresa pelo operador autenticado; consultas e códigos de rastreamento são restritos ao respectivo tenant.
- Validação dos dados de entrada, `201` na criação, `400` para dados inválidos, `401` sem autenticação, `404` para remessa inacessível ou inexistente e `409` para código de rastreamento duplicado na mesma empresa.
- Migração inicial do PostgreSQL com Flyway e provisionamento de duas empresas fictícias para demonstração.
- Testes automatizados de autenticação, validação, isolamento entre empresas, idempotência e regras de transição; pipeline de CI com PostgreSQL.

## Tecnologias e arquitetura

| Camada | Implementado | Planejado |
| --- | --- | --- |
| Backend | Java 21, Spring Boot, Spring Security, JDBC e Bean Validation | Operação completa, permissões persistentes e auditoria |
| Dados | PostgreSQL 17 e Flyway | Evolução do modelo e auditoria de operações |
| Interface | — | React e TypeScript |
| IA | — | Recomendações de ocorrências com decisão humana |
| Qualidade | JUnit, MockMvc e GitHub Actions | Testes do fluxo operacional completo |

O backend segue a direção de um monólito modular. Nesta fase, estão implementados os pacotes de identidade de demonstração e remessas. O modelo SQL prevê motoristas e veículos, mas suas APIs ainda não estão disponíveis.

## Executar localmente

**Pré-requisitos:** Java 21, Maven e Docker com Docker Compose.

1. Clone o repositório e crie o arquivo de configuração:

   ```bash
   git clone https://github.com/juceliocoelho2022/Rotavyn.git
   cd Rotavyn
   cp .env.example .env
   ```

   No PowerShell, use `Copy-Item .env.example .env` no lugar de `cp`. Troque `DB_PASSWORD`, `ROTAVYN_DEMO_PASSWORD_A` e `ROTAVYN_DEMO_PASSWORD_B` no arquivo `.env`. Não publique esse arquivo.

2. Inicie apenas o banco de dados:

   ```bash
   docker compose up -d
   ```

3. Exporte as variáveis do `.env` para o processo do backend. No Bash:

   ```bash
   set -a
   source .env
   set +a
   cd backend
   mvn spring-boot:run
   ```

   No PowerShell, a partir da raiz do repositório:

   ```powershell
   Get-Content .env | Where-Object { $_ -match '^[A-Za-z_][A-Za-z0-9_]*=' } | ForEach-Object {
       $key, $value = $_ -split '=', 2
       [Environment]::SetEnvironmentVariable($key, $value, 'Process')
   }
   Set-Location backend
   mvn spring-boot:run
   ```

   A API fica em `http://localhost:8080`. O health check está em `/actuator/health`.

## Exemplo da API

Os dois operadores definidos no `.env` pertencem a empresas diferentes. Com o backend iniciado, execute no Bash:

```bash
curl -u "$ROTAVYN_DEMO_USER_A:$ROTAVYN_DEMO_PASSWORD_A" \
  -H 'Content-Type: application/json' \
  -d '{"trackingCode":"ROT-2026-001","senderName":"Empresa Exemplo","recipientName":"Cliente Exemplo","destinationAddress":"Rua Exemplo, 100","destinationCountry":"BR","promisedAt":"2030-01-01T12:00:00Z"}' \
  http://localhost:8080/api/v1/shipments
```

A resposta contém o `id` e o status `CREATED`. Para listar remessas da empresa autenticada:

```bash
curl -u "$ROTAVYN_DEMO_USER_A:$ROTAVYN_DEMO_PASSWORD_A" \
  http://localhost:8080/api/v1/shipments
```

| Método | Rota | Resultado |
| --- | --- | --- |
| `POST` | `/api/v1/shipments` | Cria uma remessa (`201`) |
| `GET` | `/api/v1/shipments` | Lista as remessas da empresa (`200`) |
| `GET` | `/api/v1/shipments/{id}` | Consulta uma remessa da empresa (`200` ou `404`) |
| `POST` | `/api/v1/shipments/{id}/events` | Registra evento e avança o status (`200` ou `422`) |
| `GET` | `/api/v1/shipments/{id}/events` | Consulta o histórico da empresa (`200` ou `404`) |
| `GET` | `/actuator/health` | Verifica a saúde da aplicação |

`destinationCountry` aceita duas letras maiúsculas; `promisedAt` usa data e hora ISO 8601 com fuso. A API não aceita `tenantId` no corpo da requisição.

Para registrar um evento, envie `eventType`, `idempotencyKey` e, opcionalmente, `note`. Exemplo: `{"eventType":"CANCEL","idempotencyKey":"cancelamento-001","note":"Solicitação do remetente"}`. Repetir a mesma chave para a mesma remessa e evento retorna o registro existente; uma transição proibida retorna `422`. Os tipos aceitos seguem a [máquina de estados](backend/src/main/java/br/com/rotavyn/shipment/ShipmentRules.java). Nesta fase, `ASSIGN` altera somente o status: o vínculo efetivo com motorista e veículo ainda será implementado. Use apenas dados fictícios.

## Testes

Com o PostgreSQL ativo e as variáveis do `.env` carregadas:

```bash
cd backend
mvn verify
```

O [pipeline Backend CI](https://github.com/juceliocoelho2022/Rotavyn/actions/workflows/backend-ci.yml) executa o mesmo comando com Java 21 e PostgreSQL 17 em cada atualização da branch principal e em pull requests.

## Limites atuais e próximos passos

A autenticação usa HTTP Basic e operadores configurados por variáveis de ambiente **somente para demonstração local**. Antes de uso real, são necessários usuários persistentes, autorização por papel, gerenciamento seguro de credenciais e revisão de segurança. Ainda faltam os endpoints de frota, atribuição efetiva e ocorrências; a interface React; e a integração com IA. Nenhuma recomendação automatizada está ativa nesta versão.

O roteiro aprovado está na [especificação de produto](docs/superpowers/specs/2026-09-23-rotavyn-design.md) e no [plano de implementação](docs/superpowers/plans/2026-09-23-rotavyn-mvp.md). A meta é concluir o ciclo operacional de uma remessa e depois adicionar recomendações com justificativa e aprovação humana.
