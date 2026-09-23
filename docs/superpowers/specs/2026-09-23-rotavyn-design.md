# Rotavyn — desenho da primeira versão

**Assinatura:** Inteligência para mover o mundo.

## Objetivo e decisões aprovadas

Rotavyn é uma plataforma de logística para várias empresas. A primeira operação atendida é a de uma transportadora: cadastrar remessas, distribuir entregas, acompanhar andamento e tratar ocorrências. O operador deve conseguir concluir o fluxo completo no painel. A IA recomenda ações para atrasos e ocorrências; uma pessoa decide se as executa.

O usuário aprovou Java 21, Spring Boot, PostgreSQL e React, com backend em monólito modular. A primeira versão é um produto operacional demonstrável, preparado para ampliar cobertura geográfica e integrar outras transportadoras. Escala mundial é uma direção futura, não uma capacidade reivindicada para o primeiro lançamento.

## Limites da primeira entrega

- Administrador da plataforma provisiona empresas e gestores; gestor gerencia motoristas, veículos e operadores de sua empresa.
- Operador cria remessas com remetente, destinatário, endereços, país, janela de entrega e código único por empresa; vincula motorista e veículo; registra coleta, saída, tentativa, entrega ou falha.
- Motorista visualiza apenas as remessas que lhe foram atribuídas e registra eventos de execução; comprovante inicial contém data, responsável e observação, sem foto ou assinatura digital.
- Painel lista remessas por status, prazo e ocorrência e mostra linha do tempo imutável de eventos.
- Assistente analisa remessas atrasadas ou com ocorrência e gera recomendação, justificativa e referências aos eventos utilizados; operador aceita ou descarta. Aceitar apenas registra a decisão e uma tarefa de acompanhamento; não altera status nem aciona serviços externos automaticamente.
- Idioma inicial português; datas persistidas em UTC e apresentadas no fuso da empresa; país e códigos de endereço explícitos. Moedas, alfândega, integração GPS, otimização matemática de rotas, pagamentos, estoque e mensageria externa ficam para fases posteriores.

## Arquitetura

```
React + TypeScript → API REST Spring Boot 3 / Java 21 → PostgreSQL
                         ├─ identidade e empresas
                         ├─ frota e pessoas
                         ├─ remessas e despacho
                         ├─ eventos e ocorrências
                         └─ recomendações de IA
```

Uma única implantação do backend e um banco PostgreSQL, com módulos separados por pacote e contratos de serviço. Flyway gerencia migrações. API versionada em `/api/v1`, DTOs validados, erros ProblemDetail e paginação nas listagens. React consome apenas a API; a chave do provedor de IA permanece no servidor. Docker Compose permite executar frontend, backend e banco localmente. Sem Kafka na primeira entrega: a atualização transacional da remessa e do histórico ocorre no mesmo banco. A separação em módulos permite introduzir eventos assíncronos quando houver uma integração que exija isso.

## Isolamento e acesso

Cada registro operacional tem `tenant_id` obrigatório. O servidor obtém o tenant do usuário autenticado, nunca do corpo da requisição; repositórios e consultas filtram por tenant. Chaves únicas incluem `tenant_id` quando representam identificação do cliente. Papéis: `PLATFORM_ADMIN`, `TENANT_MANAGER`, `DISPATCHER`, `DRIVER`. O administrador provisiona uma empresa, mas os dados de empresas diferentes não aparecem nas consultas comuns. Senhas têm hash seguro, sessões são autenticadas e o backend aplica autorização por função e por recurso. O primeiro conjunto de testes inclui acessos cruzados entre duas empresas.

## Modelo e fluxo

Entidades centrais: `tenant`, `user_account`, `driver`, `vehicle`, `shipment`, `shipment_event`, `incident`, `ai_recommendation`, `follow_up_task`. Identificadores UUID; eventos registram instante UTC, ator e motivo. A remessa guarda status atual e versão para controle de concorrência; a linha do tempo é acrescentada na mesma transação da mudança de status.

Estados permitidos: `CREATED → ASSIGNED → PICKED_UP → IN_TRANSIT → DELIVERED`; de `IN_TRANSIT` pode ir a `DELIVERY_ATTEMPTED`, de onde pode voltar a `IN_TRANSIT` ou ir a `DELIVERED`; uma falha pode levar a `EXCEPTION`, com retomada documentada por um operador. Cancelamento só antes da coleta. Eventos repetidos com a mesma chave de idempotência por tenant não criam transições duplicadas. Conflito de versão retorna HTTP 409; transição inválida retorna HTTP 422.

Fluxo demonstrável: gestor cadastra motorista e veículo; operador cria remessa e despacha; motorista registra coleta e andamento; uma tentativa malsucedida gera ocorrência; painel destaca atraso; agente sugere contato ou reprogramação com evidência; operador registra decisão e tarefa; motorista conclui entrega; painel exibe histórico completo.

## IA e limites de decisão

Um adaptador de IA isolado recebe somente os campos necessários da remessa e eventos daquela empresa, minimizando dados pessoais. O backend calcula atraso por regra determinística (`prazo < agora` e não entregue) e seleciona casos candidatos; o modelo elabora recomendação estruturada, nunca inventa ou escreve eventos operacionais. A resposta é validada por esquema e registra versão do prompt/modelo, horário, custo quando disponível, evidências e decisão humana. Falha, timeout ou indisponibilidade do provedor mantém a operação principal funcional e mostra recomendação indisponível. Um modo local determinístico permite desenvolver e demonstrar o fluxo sem chave paga; não será apresentado como inferência de IA. LangChain/LangGraph dos materiais do curso servem como referência para evolução de ferramentas e fluxos com revisão humana, sem copiar notebooks diretamente para produção.

## Interfaces e verificação

Telas: acesso; visão geral; lista e detalhe de remessas; criação; despacho; frota; ocorrências; recomendações e decisões; visão do motorista. Interface responsiva com indicadores claros para atraso, sem depender apenas de cor.

Endpoints iniciais: autenticação; `/tenants` (admin); `/drivers`; `/vehicles`; `/shipments` (criar/listar/detalhar); `/shipments/{id}/assignment`; `/shipments/{id}/events`; `/incidents`; `/recommendations/{shipmentId}` e decisão de recomendação. O desenho exato dos contratos será fechado no plano de implementação.

Verificação mínima: testes de transição de estado, idempotência, concorrência, isolamento entre duas empresas e autorização por papel; integração com PostgreSQL e migrações; teste de fluxo completo pela API e construção do frontend. Observabilidade inicial: health check, logs estruturados com correlação e métricas básicas. Dados de exemplo são fictícios.

## Etapas seguintes

1. Fundação: autenticação, tenant, migrações e isolamento.
2. Operação: frota, remessas, eventos, despacho e painel.
3. Assistência: detecção de atraso, recomendações, revisão humana e auditoria.
4. Expansão posterior: integrações externas, rastreamento, roteirização, outros países, internacionalização e arquitetura orientada a eventos quando houver demanda concreta.

## Critério de aceite

Em ambiente local, duas empresas conseguem executar fluxos independentes sem acesso aos dados uma da outra. Um operador conclui o ciclo de uma remessa por meio do painel; a API rejeita transições ilegais e repetições; uma ocorrência atrasada recebe recomendação revisável, e a operação prossegue quando o provedor de IA estiver indisponível.
