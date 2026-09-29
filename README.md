# Nexo Estoque

Plataforma web de gestão inteligente e auditável de estoque para farmácias, mercados, lojas, depósitos e distribuidores.

O Nexo concentra operação, rastreabilidade e apoio à decisão em uma única aplicação. Os cálculos críticos permanecem determinísticos e auditáveis. A assistência por IA funciona como camada explicativa e não altera saldo nem executa decisões críticas por conta própria.

## Stack

- Java 21 e Spring Boot
- React, TypeScript e Vite
- MySQL
- JDBC e CallableStatement
- Flyway
- Stored Procedures
- JUnit e Vitest
- GitHub Actions
- Railway

## O que já está implementado

- autenticação real no backend com perfis Admin, Operador e Consulta
- demonstração pública somente leitura
- produtos com edição e inativação controlada
- estoque por lote e validade
- FEFO com proteção contra concorrência
- idempotência em operações críticas
- inventário cego
- histórico de movimentações e autoria
- auditoria de decisões e simulações
- fornecedores e pedidos de compra
- recebimento parcial e total vinculado ao pedido
- múltiplos depósitos e posições físicas
- transferências internas sem alterar o saldo global
- leitura de código de barras por câmera com fallback manual
- backup, restauração e política de retenção
- dashboard operacional calculado no backend
- lista de produtos críticos para reposição
- classificação de risco de validade
- posição de estoque por depósito, posição e lote
- exportação CSV segura da posição de estoque

## Inteligência operacional

A API possui uma camada própria para leitura operacional:

```text
GET /api/v1/operations/dashboard
GET /api/v1/operations/critical
GET /api/v1/operations/expiry
GET /api/v1/operations/stock-position
GET /api/v1/operations/stock-position.csv
```

O dashboard não recalcula as regras críticas no navegador. Totais, valor em estoque, ruptura, produtos abaixo do mínimo, lotes vencidos ou próximos do vencimento e precisão do último inventário são consolidados pelo backend.

## Segurança e consistência

- CORS restrito em produção
- credenciais administrativas mantidas fora do repositório
- operações de escrita bloqueadas para o perfil de consulta
- chaves de idempotência persistidas
- travas de banco em saídas FEFO
- histórico operacional preservado
- exportação CSV neutraliza células que poderiam ser interpretadas como fórmulas

## Demonstração

Aplicação: https://nexo-estoque-web-production.up.railway.app

Acesso público somente leitura:

```text
usuário: demo
senha: Nexo@2026
```

## Estado

Em desenvolvimento ativo. A versão da API neste bloco é 0.3.0.
