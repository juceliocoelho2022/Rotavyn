# Rotavyn

**Inteligência para mover o mundo.**

Plataforma de logística para múltiplas empresas, começando pelo fluxo de transportadoras. O objetivo da primeira versão é cadastrar remessas, atribuir motoristas e veículos, acompanhar eventos e oferecer recomendações para ocorrências com revisão humana.

## Estado atual

Este repositório está em desenvolvimento inicial. Contém a especificação, o plano de implementação, regras básicas de transição de remessas, um esquema SQL inicial e uma configuração do PostgreSQL. **Ainda não há API executável, frontend ou agente de IA integrado.**

## Arquitetura prevista

- Backend: Java 21 e Spring Boot, monólito modular.
- Banco: PostgreSQL 17, com isolamento de dados por empresa.
- Frontend: React e TypeScript.
- IA: recomendações para atrasos e ocorrências, sujeitas à decisão de um operador.

Leia a [especificação](docs/superpowers/specs/2026-09-23-rotavyn-design.md) e o [plano](docs/superpowers/plans/2026-09-23-rotavyn-mvp.md). O arquivo `compose.yml` inicia somente o banco de dados e requer `DB_PASSWORD` no ambiente ou em um arquivo `.env` local.

## Próximo marco

Implementar autenticação, API Spring Boot e testes de isolamento entre empresas antes de aceitar dados reais.
