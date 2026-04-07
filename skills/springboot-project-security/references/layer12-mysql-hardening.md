# Layer 12 — Proteção do Banco de Dados MySQL

> **Mindset pentester:** O banco de dados é o prêmio final. Toda outra proteção existe
> para que o atacante nunca chegue aqui. Mas quando chega — via SQLi, RCE, credencial
> comprometida — o banco é a última linha de defesa. Ele precisa estar endurecido.

---

## Princípio de Menor Privilégio — Usuários Separados

```sql
-- Nunca usar root para a aplicação
-- Criar usuários específicos por função

-- Usuário da aplicação: DML apenas
CREATE USER 'app_user'@'%' IDENTIFIED BY 'senha-forte-aqui';
GRANT SELECT, INSERT, UPDATE, DELETE ON financas_db.* TO 'app_user'@'%';

-- Usuário de migrations (Flyway): DDL
CREATE USER 'flyway_user'@'localhost' IDENTIFIED BY 'outra-senha-forte';
GRANT SELECT, INSERT, UPDATE, DELETE, CREATE, ALTER, DROP, INDEX,
      REFERENCES ON financas_db.* TO 'flyway_user'@'localhost';

-- Usuário de backup (apenas leitura)
CREATE USER 'backup_user'@'localhost' IDENTIFIED BY 'senha-backup';
GRANT SELECT, LOCK TABLES ON financas_db.* TO 'backup_user'@'localhost';

-- Usuário de audit logs (apenas INSERT — atacante comprometido não consegue apagar)
CREATE USER 'audit_user'@'%' IDENTIFIED BY 'senha-audit';
GRANT INSERT ON financas_db.security_audit_logs TO 'audit_user'@'%';

FLUSH PRIVILEGES;
```

---

## Configuração de Segurança do MySQL

```ini
# /etc/mysql/conf.d/security.cnf

[mysqld]
# Desativar arquivo LOCAL DATA INFILE (leitura de arquivos do servidor)
local_infile = 0

# Desativar symbolic links (path traversal via symlink)
symbolic_links = 0

# Bind apenas em localhost ou IP interno — NUNCA 0.0.0.0 sem firewall
bind-address = 127.0.0.1

# Desativar SELECT INTO OUTFILE (write de arquivos)
# (via secure_file_priv — limitar diretório permitido)
secure_file_priv = /var/lib/mysql-files/

# Tamanho máximo de pacote (proteção contra DoS via queries enormes)
max_allowed_packet = 16M

# Número máximo de conexões por usuário
max_user_connections = 100

# Log de queries lentas (detectar potencial time-based blind SQLi)
slow_query_log = ON
slow_query_log_file = /var/log/mysql/slow.log
long_query_time = 2  # logar queries > 2 segundos

# Log de erros para auditoria
log_error = /var/log/mysql/error.log
log_warnings = 2

# Desativar plugin antigo de autenticação
default_authentication_plugin = caching_sha2_password

# Exigir SSL para conexões externas
require_secure_transport = ON
```

---

## Connection Pooling Seguro com HikariCP

```yaml
# application.yml
spring:
  datasource:
    url: jdbc:mysql://${DB_HOST}:3306/${DB_NAME}?
         useSSL=true&
         requireSSL=true&
         verifyServerCertificate=true&
         serverTimezone=UTC&
         characterEncoding=UTF-8&
         connectionCollation=utf8mb4_unicode_ci&
         allowPublicKeyRetrieval=false&
         autoReconnect=false
    username: ${DB_APP_USER}
    password: ${DB_APP_PASS}
    driver-class-name: com.mysql.cj.jdbc.Driver

    hikari:
      # Pool sizing
      minimum-idle: 5
      maximum-pool-size: 20
      connection-timeout: 5000      # 5s para obter conexão do pool
      idle-timeout: 300000          # 5min antes de fechar conexões idle
      max-lifetime: 1200000         # 20min — rotacionar conexões

      # Segurança
      connection-test-query: SELECT 1
      validation-timeout: 3000

      # Isolar transações — evitar conexão "suja" entre requests
      auto-commit: false
      transaction-isolation: TRANSACTION_READ_COMMITTED

      # Detectar vazamentos de conexão
      leak-detection-threshold: 10000  # 10s sem retornar = log warning
```

---

## Colunas Sensíveis — Criptografia em Nível de Coluna

```java
// Dados extremamente sensíveis são criptografados antes de ir para o banco
// Mesmo se o banco for comprometido, os dados são ilegíveis

// utils/ColumnEncryptor.java
@Component
public class ColumnEncryptor {

    @Value("${db.column-encryption-key}")
    private String base64Key;

    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_IV_LENGTH = 12;
    private static final int GCM_TAG_LENGTH = 128;

    public String encrypt(String plaintext) {
        if (plaintext == null) return null;
        try {
            byte[] key = Base64.getDecoder().decode(base64Key);
            SecretKeySpec secretKey = new SecretKeySpec(key, "AES");

            byte[] iv = new byte[GCM_IV_LENGTH];
            new SecureRandom().nextBytes(iv);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, new GCMParameterSpec(GCM_TAG_LENGTH, iv));

            byte[] encrypted = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

            // IV + ciphertext — IV precisa ir junto para decriptografar
            byte[] combined = new byte[iv.length + encrypted.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(encrypted, 0, combined, iv.length, encrypted.length);

            return Base64.getEncoder().encodeToString(combined);
        } catch (Exception e) {
            throw new RuntimeException("Erro ao criptografar", e);
        }
    }

    public String decrypt(String ciphertext) {
        if (ciphertext == null) return null;
        try {
            byte[] combined = Base64.getDecoder().decode(ciphertext);
            byte[] key = Base64.getDecoder().decode(base64Key);

            byte[] iv = Arrays.copyOfRange(combined, 0, GCM_IV_LENGTH);
            byte[] encrypted = Arrays.copyOfRange(combined, GCM_IV_LENGTH, combined.length);

            SecretKeySpec secretKey = new SecretKeySpec(key, "AES");
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, new GCMParameterSpec(GCM_TAG_LENGTH, iv));

            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException("Erro ao decriptografar", e);
        }
    }
}

// Uso como converter JPA — transparente para o código
@Converter
@Component
public class EncryptedStringConverter implements AttributeConverter<String, String> {

    @Autowired
    private static ColumnEncryptor encryptor;

    @Override
    public String convertToDatabaseColumn(String attribute) {
        return encryptor.encrypt(attribute);
    }

    @Override
    public String convertToEntityAttribute(String dbData) {
        return encryptor.decrypt(dbData);
    }
}

// Uso na entity:
@Column(name = "tax_id")
@Convert(converter = EncryptedStringConverter.class)
private String cpfCnpj; // armazenado criptografado no banco
```

---

## Soft Delete — Nunca Deletar Dados Permanentemente

```java
// entity/BaseEntity.java
@MappedSuperclass
public abstract class BaseEntity {
    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Column(name = "is_deleted", nullable = false)
    private boolean deleted = false;

    public void softDelete() {
        this.deleted = true;
        this.deletedAt = Instant.now();
    }
}

// Filtro global — queries nunca retornam registros deletados
@FilterDef(name = "notDeleted", defaultCondition = "is_deleted = false")
@Filter(name = "notDeleted")
@Entity
public class User extends BaseEntity { ... }

// Habilitar filtro globalmente
@Aspect @Component
public class SoftDeleteFilterAspect {
    @Autowired EntityManager em;

    @Before("@annotation(Transactional)")
    public void enableSoftDeleteFilter() {
        em.unwrap(Session.class).enableFilter("notDeleted");
    }
}
```

---

## Proteção contra Bulk Data Extraction

```java
// Detectar e limitar queries que retornam volumes suspeitos de dados

@Aspect @Component
public class BulkDataProtectionAspect {

    private static final int BULK_THRESHOLD = 1000;

    @Around("@annotation(org.springframework.data.jpa.repository.Query)")
    public Object checkBulkData(ProceedingJoinPoint pjp) throws Throwable {
        Object result = pjp.proceed();

        if (result instanceof Collection<?> collection) {
            if (collection.size() > BULK_THRESHOLD) {
                SecurityEventPublisher.publish(SecurityEvent.BULK_DATA_ACCESS,
                    "Method: " + pjp.getSignature().getName() +
                    " | Rows: " + collection.size());
            }
        }

        return result;
    }
}

// Em repositories: SEMPRE usar paginação — nunca retornar tudo
Page<User> findAll(Pageable pageable); // ← correto
List<User> findAll();                  // ← NUNCA em produção
```

---

## Backup Seguro

```bash
#!/bin/bash
# backup.sh — backup criptografado com GPG

DB_HOST="${DB_HOST}"
DB_NAME="${DB_NAME}"
BACKUP_USER="${DB_BACKUP_USER}"
BACKUP_PASS="${DB_BACKUP_PASS}"
GPG_RECIPIENT="backup@empresa.com"
BACKUP_DIR="/var/backups/mysql"
TIMESTAMP=$(date +%Y%m%d_%H%M%S)

# Dump + criptografia em pipeline (nunca salvar dump em texto puro)
mysqldump \
    --host="${DB_HOST}" \
    --user="${BACKUP_USER}" \
    --password="${BACKUP_PASS}" \
    --ssl-ca=/etc/ssl/mysql/ca.pem \
    --single-transaction \
    --quick \
    --lock-tables=false \
    "${DB_NAME}" | \
    gzip | \
    gpg --encrypt --recipient "${GPG_RECIPIENT}" \
    > "${BACKUP_DIR}/backup_${TIMESTAMP}.sql.gz.gpg"

# Verificar integridade
echo "Backup gerado: backup_${TIMESTAMP}.sql.gz.gpg"
sha256sum "${BACKUP_DIR}/backup_${TIMESTAMP}.sql.gz.gpg" >> "${BACKUP_DIR}/checksums.txt"
```

---

## Checklist Layer 12

- [ ] Usuário `app_user`: apenas SELECT/INSERT/UPDATE/DELETE — sem DDL
- [ ] Usuário `flyway_user`: DDL — apenas de localhost
- [ ] Usuário `audit_user`: apenas INSERT em tabela de auditoria
- [ ] `local_infile = 0` — sem leitura de arquivos do servidor
- [ ] `symbolic_links = 0` — sem symlinks
- [ ] `bind-address` não é 0.0.0.0 sem firewall
- [ ] SSL obrigatório nas conexões (`requireSSL=true`, `verifyServerCertificate=true`)
- [ ] `allowPublicKeyRetrieval=false` — evitar downgrade attack
- [ ] HikariCP: `leak-detection-threshold` configurado
- [ ] Dados sensíveis (CPF/CNPJ, etc.) criptografados com AES-GCM no nível de coluna
- [ ] Soft delete — nunca DELETE real em dados de negócio
- [ ] Queries de listagem sempre com paginação — nunca `findAll()` sem limite
- [ ] Detecção de bulk extraction (> 1000 rows)
- [ ] Slow query log habilitado (detectar time-based blind SQLi)
- [ ] Backups criptografados com GPG — nunca em texto puro
- [ ] Checksum de backups registrado
