# Rotavyn

**Inteligência para mover o mundo.**

Plataforma de logística para múltiplas empresas, começando pelo fluxo de transportadoras. O objetivo da primeira versão é cadastrar remessas, atribuir motoristas e veículos, acompanhar eventos e oferecer recomendações para ocorrências com revisão humana.

## Estado atual

Primeiro fluxo executável: API Java 21/Spring Boot para criar, listar e consultar remessas, com PostgreSQL, migrações Flyway, dois operadores de demonstração e isolamento por empresa. O CI usa PostgreSQL e executa testes de regras, autenticação e isolamento. A autenticação HTTP Basic é apenas para desenvolvimento; ainda não há frontend nem agentes de IA.

### Iniciar localmente

1. Instale Java 21, Maven e Docker Compose.
2. Copie `.env.example` para `.env` e troque as três senhas. Carregue as variáveis no terminal: `set -a; source .env; set +a`.
3. Execute `docker compose up -d` e depois `cd backend && mvn spring-boot:run`.
4. Exemplo: `curl -u "$ROTAVYN_DEMO_USER_A:$ROTAVYN_DEMO_PASSWORD_A" http://localhost:8080/api/v1/shipments`.

`POST /api/v1/shipments` aceita `trackingCode`, `senderName`, `recipientName`, `destinationAddress`, `destinationCountry` (código de duas letras) e `promisedAt` (data ISO 8601). `GET /api/v1/shipments/{id}` e a listagem limitam os dados ao operador autenticado. Os UUIDs de demonstração são cadastrados ao iniciar. Para verificar: `cd backend && mvn verify` com o banco ativo.

## Arquitetura prevista

- Backend: Java 21 e Spring Boot, monólito modular.
- Banco: PostgreSQL 17, com isolamento de dados por empresa.
- Frontend: React e TypeScript.
- IA: recomendações para atrasos e ocorrências, sujeitas à decisão de um operador.

Leia a [especificação](docs/superpowers/specs/2026-09-23-rotavyn-design.md) e o [plano](docs/superpowers/plans/2026-09-23-rotavyn-mvp.md). O arquivo `compose.yml` inicia somente o banco de dados e requer `DB_PASSWORD` no ambiente ou em um arquivo `.env` local.

## Próximo marco

Adicionar eventos de remessa, controle de permissões persistente, interface React e recomendações de IA com revisão humana antes de aceitar dados reais.
