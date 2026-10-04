# Sessões de acesso

O login recebe `POST /api/v1/auth/login`, com JSON `{ "username": "...", "password": "..." }`. Use HTTPS fora do desenvolvimento local. A resposta contém `username`, `role`, `token` e `expiresAt`, com `Cache-Control: no-store`.

As chamadas autenticadas usam `Authorization: Bearer <token>`. HTTP Basic não é aceito. O frontend elimina a sessão legada que continha credenciais Basic e exige um novo login.

O token aleatório tem 256 bits. O servidor armazena seu SHA-256, o usuário e a expiração; não guarda a senha na sessão. A duração padrão é 30 minutos, configurável por `nexo.auth.session-seconds` entre 60 e 3600 segundos. A atividade não estende a validade. `POST /api/v1/auth/logout` revoga o token apresentado; respostas 401 eliminam a sessão local.

O navegador guarda apenas o token temporário e metadados em `sessionStorage`. A senha não é persistida. O token ainda é acessível a JavaScript: prevenção de XSS continua necessária. Se o logout não alcançar a API, a cópia local é eliminada, mas o token no servidor permanece válido até expirar.

Após cinco tentativas de login para o mesmo par endereço remoto/usuário em 15 minutos, a API responde 429 até encerrar a janela. O limite é local ao processo e não confia em cabeçalhos de proxy enviados pelo cliente. Um proxy pode compartilhar um endereço entre usuários; esta política deve ser revista antes de aumentar a escala.

## Limites operacionais

As sessões e os limites ficam em memória e atendem uma única instância da API. Reiniciar o servidor encerra todas as sessões. Não configure múltiplas réplicas sem migrar as sessões e os limites para um armazenamento compartilhado. O serviço limita as tabelas em memória a 5000 entradas cada.

Publique API e frontend da mesma revisão. O contrato de login mudou; consumidores anteriores precisam adotar JSON no login e Bearer nas consultas.

## Verificação

Os testes Java cobrem expiração, revogação, usuário removido, falhas de login, rejeição de Basic e permissões VIEWER/ADMIN pela cadeia HTTP. Os testes do frontend cobrem remoção da sessão legada, persistência sem senha, expiração, respostas 401 e logout.
