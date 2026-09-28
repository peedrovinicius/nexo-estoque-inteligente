# Nexo Estoque

Sistema desktop de gestão de estoque desenvolvido em Java e MySQL, com foco em rastreabilidade, prevenção de perdas e apoio à decisão.

O projeto combina operações tradicionais de estoque com recursos menos comuns em sistemas acadêmicos: controle por lote e validade, inventário cego, simulação de cenários, análise de risco de ruptura e excesso, trilha de auditoria e uma camada de assistência inteligente baseada nos dados reais do estoque.

## Objetivo

Atender pequenos e médios negócios que precisam de controle de estoque no dia a dia, como farmácias, mercantis, lojas, depósitos e distribuidores, sem limitar o sistema a um único segmento.

## Diferenciais

- CRUD de produtos executado por Stored Procedures
- integração Java/MySQL com JDBC e `CallableStatement`
- controle de entradas, saídas, ajustes e devoluções
- rastreabilidade por lote e validade
- estratégia FEFO para itens perecíveis
- inventário cego para reduzir viés de contagem
- alertas de estoque baixo e risco de vencimento
- simulador de cenários de demanda e reposição
- histórico auditável das recomendações
- camada de assistência inteligente para explicar riscos e sugerir ações, sem substituir as regras determinísticas do sistema

## Stack

- Java 21
- Swing / JFrame
- Maven
- MySQL 8
- JDBC
- Stored Procedures
- FlatLaf
- JUnit 5

## Estrutura inicial

```text
src/main/java/br/com/nexoestoque/
├── config/
├── dao/
├── model/
├── service/
├── simulation/
└── view/

database/
├── schema.sql
└── procedures.sql
```

## Princípio de decisão

O núcleo de estoque permanece determinístico e auditável. A assistência inteligente recebe apenas os dados necessários para interpretar cenários e explicar recomendações, enquanto cálculos críticos de quantidade, saldo, validade e risco continuam reproduzíveis pelo próprio sistema.

## Status

Em desenvolvimento.
