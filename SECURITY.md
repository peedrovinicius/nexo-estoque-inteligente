# Política de segurança

## Escopo

Relatos de segurança são relevantes quando envolvem autenticação, autorização, perfis de acesso, operações de estoque, idempotência, concorrência, exportações, banco de dados, segredos, integrações ou cadeia de dependências.

Problemas funcionais de cálculo, planejamento de demanda ou regras operacionais sem impacto de segurança devem ser registrados como issues comuns.

## Como relatar

Não publique uma vulnerabilidade ainda não corrigida em uma issue pública.

Prefira o mecanismo privado de reporte de vulnerabilidade do GitHub quando disponível. Caso não esteja habilitado, entre em contato de forma privada com o mantenedor pelo perfil do GitHub.

Inclua, quando possível:

- componente e versão ou commit afetado;
- passos mínimos para reprodução;
- impacto observado ou plausível;
- evidências sem credenciais, tokens ou dados pessoais;
- sugestão de mitigação, se houver.

## Segredos e dados

Nunca devem ser enviados ao repositório:

- arquivos `.env` com valores reais;
- senhas, tokens, chaves privadas ou cookies de sessão;
- credenciais de banco ou serviços externos;
- dumps de produção;
- dados pessoais reais usados apenas para teste.

A demonstração pública deve permanecer somente leitura. Dados usados em testes e exemplos devem ser fictícios.

## Mudanças sensíveis

Alterações em autenticação, autorização, movimentações de estoque, FEFO, quarentena, recall, backup, restauração ou integrações devem preservar:

- validação no servidor;
- princípio do menor privilégio;
- auditoria sem segredos;
- idempotência quando aplicável;
- transações e proteção contra concorrência;
- testes automatizados de regressão.

Vulnerabilidades confirmadas devem ser corrigidas antes da divulgação técnica detalhada.
