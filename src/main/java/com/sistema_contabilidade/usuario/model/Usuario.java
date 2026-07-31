package com.sistema_contabilidade.usuario.model;

import com.sistema_contabilidade.database.crypto.BlindIndexAware;
import com.sistema_contabilidade.database.crypto.BlindIndexEntityListener;
import com.sistema_contabilidade.database.crypto.EncryptedStringConverter;
import com.sistema_contabilidade.database.crypto.service.BlindIndexService;
import com.sistema_contabilidade.rbac.model.Role;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.SoftDelete;
import org.hibernate.annotations.SoftDeleteType;
import org.hibernate.annotations.UuidGenerator;

@Entity
@Table(
    name = "usuarios",
    indexes = {
      @Index(name = "idx_usuarios_email_bidx", columnList = "email_bidx", unique = true),
      @Index(
          name = "idx_usuarios_cognito_sub_bidx",
          columnList = "cognito_sub_bidx",
          unique = true),
      @Index(
          name = "idx_usuarios_cognito_username_bidx",
          columnList = "cognito_username_bidx",
          unique = true)
    })
@EntityListeners(BlindIndexEntityListener.class)
@SoftDelete(strategy = SoftDeleteType.TIMESTAMP, columnName = "deleted_at")
@Getter
@Setter
@NoArgsConstructor
public class Usuario implements BlindIndexAware {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  @UuidGenerator(style = UuidGenerator.Style.TIME)
  private UUID id;

  @Version private Long version;

  @NotBlank
  @Convert(converter = EncryptedStringConverter.class)
  @Column(nullable = false, length = 512)
  private String nome;

  @Email
  @NotBlank
  @Convert(converter = EncryptedStringConverter.class)
  @Column(nullable = false, length = 512)
  private String email;

  @Column(name = "email_bidx", length = 64, unique = true)
  private String emailBlindIndex;

  @NotBlank
  @Convert(converter = EncryptedStringConverter.class)
  @Column(nullable = false, length = 512)
  private String senha;

  @Convert(converter = EncryptedStringConverter.class)
  @Column(name = "cognito_sub", length = 256)
  private String cognitoSub;

  @Column(name = "cognito_sub_bidx", length = 64, unique = true)
  private String cognitoSubBlindIndex;

  @Convert(converter = EncryptedStringConverter.class)
  @Column(name = "cognito_username", length = 256)
  private String cognitoUsername;

  @Column(name = "cognito_username_bidx", length = 64, unique = true)
  private String cognitoUsernameBlindIndex;

  @Convert(converter = EncryptedStringConverter.class)
  @Column(name = "cognito_groups_hash", length = 256)
  private String cognitoGroupsHash;

  @Column(name = "cognito_synced_at")
  private java.time.LocalDateTime cognitoSyncedAt;

  @ManyToMany(fetch = FetchType.LAZY)
  @JoinTable(
      name = "usuario_roles",
      joinColumns = @JoinColumn(name = "usuario_id"),
      inverseJoinColumns = @JoinColumn(name = "role_id"))
  private Set<Role> roles = new HashSet<>();

  @Override
  public void synchronizeBlindIndexes(BlindIndexService blindIndexService) {
    this.emailBlindIndex = blindIndexService.email(email);
    this.cognitoSubBlindIndex = blindIndexService.cognitoSub(cognitoSub);
    this.cognitoUsernameBlindIndex = blindIndexService.cognitoUsername(cognitoUsername);
  }
}
