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
- curva ABC por valor imobilizado
- análise de estoque sem giro
- cobertura de estoque baseada em consumo real
- envelhecimento e atraso de pedidos de compra abertos
- exposição financeira por lotes vencidos e próximos do vencimento
- capital imobilizado por categoria e por depósito
- filtros analíticos por produto, SKU e categoria
- exportação CSV de curva ABC, estoque sem giro e cobertura
- central de alertas operacionais com limiares persistidos e edição exclusiva de Admin
- desempenho de fornecedores com taxa de atendimento, prazo e lead time observado
- fluxo de estoque por produto combinando histórico ativo e arquivado
- livro de movimentações pesquisável com exportação CSV
- rastreabilidade de lote desde recebimento, transferências e consumo FEFO
- diagnóstico de integridade entre saldos, lotes e recebimentos
- reposição automática baseada em consumo, saldo reservado, estoque a caminho e fornecedor histórico
- criação de pedidos de compra em lote agrupados por fornecedor
- aprovação administrativa obrigatória para envio de pedidos gerados por reposição assistida
- reserva de estoque sem alteração do saldo físico, com validade e liberação controlada
- fila diária de decisões combinando reposição, aprovações, reservas e compras atrasadas
- workspace de inteligência operacional no frontend
- workspace dedicado de rastreabilidade no frontend
- previsão ponderada de demanda usando histórico de 7, 30 e 90 dias
- estoque de segurança baseado na variabilidade diária observada
- ponto de reposição e estoque-alvo por produto
- projeção de ruptura considerando reservas, compras em aberto e lead time
- workspace dedicado de planejamento de demanda no frontend
- políticas de reposição por produto com cobertura, segurança, pedido mínimo, múltiplo e fornecedor preferencial
- exceções operacionais temporárias com justificativa, validade e autoria
- contagem cíclica priorizada por valor, classe ABC, divergência e tempo desde a última contagem
- SLA para ações operacionais com reconhecimento, autoria e histórico de atendimento
- workspace dedicado de governança operacional no frontend
- quarentena de lotes com bloqueio imediato de consumo FEFO
- recall por produto e lote com bloqueio de todas as posições afetadas
- rastreabilidade de impacto do recall por saldo, recebimentos, saídas e locais
- bloqueio de transferência e novo recebimento para lotes sob recall
- divergências de recebimento por falta, excesso, avaria, rejeição ou outra ocorrência
- workspace dedicado de Qualidade & Recall no frontend

## Inteligência operacional

A API possui uma camada própria para leitura operacional:

```text
GET /api/v1/operations/dashboard
GET /api/v1/operations/critical
GET /api/v1/operations/expiry
GET /api/v1/operations/stock-position
GET /api/v1/operations/stock-position.csv
GET /api/v1/operations/abc
GET /api/v1/operations/abc.csv
GET /api/v1/operations/slow-moving
GET /api/v1/operations/slow-moving.csv
GET /api/v1/operations/coverage
GET /api/v1/operations/coverage.csv
GET /api/v1/operations/open-purchases
GET /api/v1/operations/expiry-exposure
GET /api/v1/operations/capital
GET /api/v1/operations/alerts
GET /api/v1/operations/alerts/config
PUT /api/v1/operations/alerts/config

GET /api/v1/traceability/suppliers
GET /api/v1/traceability/stock-flow
GET /api/v1/traceability/movements
GET /api/v1/traceability/movements.csv
GET /api/v1/traceability/lots/{lotCode}
GET /api/v1/traceability/integrity

GET /api/v1/action-center/replenishment
POST /api/v1/action-center/replenishment/batch-drafts
GET /api/v1/action-center/purchase-approvals
POST /api/v1/action-center/purchase-approvals/{orderId}/request
POST /api/v1/action-center/purchase-approvals/{orderId}/approve
POST /api/v1/action-center/purchase-approvals/{orderId}/reject
GET /api/v1/action-center/reservations
POST /api/v1/action-center/reservations
POST /api/v1/action-center/reservations/{id}/cancel
GET /api/v1/action-center/daily-actions

GET /api/v1/planning
GET /api/v1/planning/summary
GET /api/v1/planning/rule

GET /api/v1/governance/policies
PUT /api/v1/governance/policies/{productId}
GET /api/v1/governance/exceptions
POST /api/v1/governance/exceptions
POST /api/v1/governance/exceptions/{id}/cancel
GET /api/v1/governance/cycle-counts
GET /api/v1/governance/actions
POST /api/v1/governance/actions/{key}/acknowledge
GET /api/v1/governance/summary

GET /api/v1/quality/summary
GET /api/v1/quality/batches
POST /api/v1/quality/batches/{batchId}/quarantine
POST /api/v1/quality/batches/{batchId}/release
GET /api/v1/quality/recalls
POST /api/v1/quality/recalls
GET /api/v1/quality/recalls/{recallId}/impact
POST /api/v1/quality/recalls/{recallId}/close
GET /api/v1/quality/receipt-variances
POST /api/v1/quality/receipt-variances
```

Filtros de produto/SKU e categoria são aplicados no backend. A central de alertas combina ruptura, validade, cobertura, ausência de giro e compras atrasadas usando limiares persistidos no MySQL.

A camada de rastreabilidade consolida movimentos correntes e arquivados, mede desempenho de fornecedores, reconstrói a linha do tempo de lotes e reconcilia automaticamente o saldo mestre contra lotes e recebimentos.

A Central de Ação transforma os diagnósticos em operação. As sugestões de reposição consideram consumo real, reservas ativas, compras em aberto, estoque mínimo e o fornecedor mais recente do produto. Pedidos assistidos exigem aprovação de Admin antes do envio.

A camada de governança permite configurar regras de reposição por produto, registrar pausas operacionais temporárias, priorizar inventários cíclicos e acompanhar ações por SLA. As alterações de política são exclusivas de Admin; exceções e reconhecimentos preservam autoria e validade.

A camada de Qualidade & Recall controla lotes retidos sem alterar artificialmente o saldo físico. Lotes em quarentena ou bloqueados ficam fora do FEFO e não podem ser transferidos. Um recall bloqueia todas as posições do mesmo produto/lote, impede novos recebimentos daquele lote e preserva o estado anterior para restauração controlada no encerramento. O impacto consolida saldo atual, quantidade recebida, quantidade já expedida e locais afetados.

## Segurança e consistência

- CORS restrito em produção
- credenciais administrativas mantidas fora do repositório
- operações de escrita bloqueadas para o perfil de consulta
- configuração de alertas alterável somente por Admin
- políticas de reposição alteráveis somente por Admin
- exceções operacionais com justificativa, validade e autoria
- reconhecimento de ações com registro do responsável e do SLA
- liberação de quarentena e encerramento de recall restritos a Admin
- bloqueio transacional de FEFO, transferência e recebimento para lotes retidos
- histórico de mudança de status de qualidade com ator, motivo e data
- pedidos gerados por reposição assistida não podem ser enviados sem aprovação administrativa
- chaves de idempotência persistidas
- travas de banco em saídas FEFO
- histórico operacional preservado
- exportações CSV neutralizam células que poderiam ser interpretadas como fórmulas

## Demonstração

Aplicação: https://nexo-estoque-web-production.up.railway.app

Acesso público somente leitura:

```text
usuário: demo
senha: Nexo@2026
```

## Estado

Versão 1.0.0. O escopo funcional planejado para o projeto de portfólio está concluído; evoluções futuras passam a ser incrementais, sem ampliar o núcleo operacional sem necessidade.
