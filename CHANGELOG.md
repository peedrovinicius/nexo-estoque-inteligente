# Changelog

## 1.0.0

Primeira versão estável do Nexo Estoque Inteligente.

### Operação de estoque

- cadastro e inativação controlada de produtos
- estoque por lote, validade e posição física
- FEFO transacional com proteção contra concorrência
- idempotência em operações críticas
- inventário cego
- transferências internas entre posições
- leitura de código de barras por câmera
- histórico de movimentos e autoria

### Compras e fornecedores

- fornecedores e pedidos de compra
- recebimento parcial e total
- desempenho de fornecedores
- reposição sugerida com criação de pedidos em lote
- aprovação administrativa antes do envio
- divergências de recebimento

### Inteligência operacional

- dashboard operacional
- curva ABC
- estoque sem giro
- cobertura por consumo real
- capital imobilizado
- exposição por validade
- pedidos em atraso
- exportações CSV seguras

### Planejamento

- previsão ponderada com histórico de 7, 30 e 90 dias
- estoque de segurança
- ponto de reposição
- estoque-alvo
- projeção de ruptura
- recomendação de compra

### Governança

- políticas de reposição por produto
- exceções operacionais temporárias
- contagem cíclica por risco
- SLA de ações operacionais
- reconhecimento com autoria

### Qualidade e recall

- quarentena de lotes
- bloqueio de FEFO e transferências para lotes retidos
- recall por produto e lote
- bloqueio de novos recebimentos sob recall
- rastreabilidade de impacto por saldo, recebimento, saída e local
- liberação e encerramento restritos a Admin

### Segurança e confiabilidade

- autenticação por perfis Admin, Operador e Consulta
- modo demonstração somente leitura
- CORS restrito em produção
- migrações Flyway até V9
- stored procedures versionadas
- testes de integração com MySQL
- CI para backend, frontend e banco
- deploy em Railway

### Interface

- temas claro e escuro
- workspaces dedicados para inteligência, rastreabilidade, ação, planejamento, governança e qualidade
- carregamento sob demanda dos módulos avançados
- logo original servida como asset estático para reduzir o bundle inicial
