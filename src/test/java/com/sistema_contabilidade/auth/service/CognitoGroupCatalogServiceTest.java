package com.sistema_contabilidade.auth.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.sistema_contabilidade.auth.config.CognitoProperties;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.services.cognitoidentityprovider.CognitoIdentityProviderClient;
import software.amazon.awssdk.services.cognitoidentityprovider.model.GroupType;
import software.amazon.awssdk.services.cognitoidentityprovider.model.ListGroupsRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.ListGroupsResponse;

@ExtendWith(MockitoExtension.class)
@DisplayName("CognitoGroupCatalogService unit tests")
class CognitoGroupCatalogServiceTest {

  @Mock private CognitoIdentityProviderClient cognitoIdentityProviderClient;
  @Mock private CognitoRoleSyncService cognitoRoleSyncService;

  @Test
  @DisplayName("Deve listar apenas grupos com descricao candidato")
  void deveListarApenasGruposComDescricaoCandidato() {
    CognitoProperties cognitoProperties = new CognitoProperties();
    cognitoProperties.setUserPoolId("pool-123");
    when(cognitoRoleSyncService.normalizeGroup(any()))
        .thenAnswer(
            invocation -> invocation.getArgument(0, String.class).trim().toUpperCase(Locale.ROOT));
    when(cognitoIdentityProviderClient.listGroups(any(ListGroupsRequest.class)))
        .thenReturn(
            ListGroupsResponse.builder()
                .groups(
                    GroupType.builder().groupName("ADMIN").description("Administradores").build(),
                    GroupType.builder()
                        .groupName("ANDRE DO PRADO")
                        .description("candidato")
                        .build(),
                    GroupType.builder().groupName("TARCISIO").description("estrutural").build(),
                    GroupType.builder().groupName("PAULO FREIRE").description("CANDIDATO").build())
                .build());

    CognitoGroupCatalogService service =
        new CognitoGroupCatalogService(
            cognitoIdentityProviderClient, cognitoProperties, cognitoRoleSyncService);

    List<String> groups = service.listCandidateNormalizedGroups();

    assertEquals(List.of("ANDRE DO PRADO", "PAULO FREIRE"), groups);
  }
}
