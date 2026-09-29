# Backup e restauração do banco

O Nexo usa duas camadas complementares de proteção de dados:

1. migrações Flyway versionadas para reconstruir o schema e procedures;
2. backup lógico do MySQL para recuperar os dados operacionais.

## Política

- Backup lógico diário.
- Retenção sugerida: 7 diários, 4 semanais e 6 mensais.
- Todo backup deve gerar checksum SHA-256.
- Uma cópia deve ficar fora do mesmo volume do banco de produção.
- Teste de restauração deve ser executado periodicamente em banco isolado.
- RPO alvo: até 24 horas.
- RTO operacional alvo: até 2 horas para uma base compatível com o porte atual do projeto.

## Criar backup

O script aceita `DB_HOST` ou consegue extrair host, porta e banco de `DB_URL`.

```bash
DB_HOST=host DB_PORT=3306 DB_NAME=nexo_estoque DB_USER=usuario DB_PASSWORD=senha OUT_DIR=./backups ./scripts/db/backup.sh
```

O resultado inclui:

- `.sql.gz`
- `.sql.gz.sha256`

As credenciais nunca devem ser gravadas no repositório.

## Restaurar

A restauração exige confirmação explícita:

```bash
CONFIRM_RESTORE=YES DB_HOST=host DB_PORT=3306 DB_NAME=nexo_restore DB_USER=usuario DB_PASSWORD=senha ./scripts/db/restore.sh backups/nexo_estoque_YYYYMMDDTHHMMSSZ.sql.gz
```

Use primeiro um banco vazio de recuperação. Não restaure diretamente sobre produção sem validação.

## Validação mínima pós-restore

Verifique:

- contagem de produtos;
- lotes;
- movimentações;
- inventários;
- fornecedores;
- pedidos de compra;
- `flyway_schema_history`;
- procedures críticas.

O CI do projeto executa uma restauração real em MySQL isolado para garantir que os scripts continuem válidos.
