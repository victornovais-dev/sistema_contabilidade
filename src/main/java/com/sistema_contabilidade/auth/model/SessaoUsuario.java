package com.sistema_contabilidade.auth.model;

import com.sistema_contabilidade.auth.config.AuthProvider;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

@Entity
@Table(
    name = "sessoes_usuario",
    indexes = {
      @Index(name = "idx_sessao_usuario_id", columnList = "usuario_id"),
      @Index(name = "idx_sessao_expira_em", columnList = "expira_em"),
      @Index(name = "idx_sessao_cognito_sub", columnList = "cognito_sub")
    })
@Getter
@Setter
@NoArgsConstructor
public class SessaoUsuario {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  @UuidGenerator(style = UuidGenerator.Style.TIME)
  private UUID id;

  @Column(name = "usuario_id", nullable = false)
  private UUID usuarioId;

  @Column(name = "criada_em", nullable = false)
  private LocalDateTime criadaEm;

  @Column(name = "expira_em", nullable = false)
  private LocalDateTime expiraEm;

  @Column(name = "atualizada_em", nullable = false)
  private LocalDateTime atualizadaEm;

  @Enumerated(EnumType.STRING)
  @Column(name = "auth_provider", nullable = false, length = 20)
  private AuthProvider authProvider = AuthProvider.LOCAL;

  @Column(name = "auth_username", length = 120)
  private String authUsername;

  @Column(name = "cognito_sub", length = 80)
  private String cognitoSub;

  @Lob
  @Column(name = "refresh_token_cifrado")
  private String refreshTokenCiphertext;

  @Lob
  @Column(name = "groups_snapshot")
  private String groupsSnapshot;

  @Column(name = "groups_hash", length = 128)
  private String groupsHash;

  @Column(name = "revogada_em")
  private LocalDateTime revogadaEm;

  @Column(nullable = false)
  private boolean revogada;
}
