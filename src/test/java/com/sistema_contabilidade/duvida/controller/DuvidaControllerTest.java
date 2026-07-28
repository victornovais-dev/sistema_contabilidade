package com.sistema_contabilidade.duvida.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sistema_contabilidade.duvida.dto.DuvidaCreateRequest;
import com.sistema_contabilidade.duvida.dto.DuvidaCreateResponse;
import com.sistema_contabilidade.duvida.dto.DuvidaListResponse;
import com.sistema_contabilidade.duvida.dto.DuvidaResponse;
import com.sistema_contabilidade.duvida.dto.DuvidaStatusUpdateRequest;
import com.sistema_contabilidade.duvida.model.DuvidaStatus;
import com.sistema_contabilidade.duvida.service.DuvidaService;
import jakarta.validation.Validation;
import java.time.LocalDateTime;
import java.time.Month;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;

@ExtendWith(MockitoExtension.class)
@DisplayName("DuvidaController unit tests")
class DuvidaControllerTest {

  @Mock private DuvidaService duvidaService;
  @InjectMocks private DuvidaController duvidaController;

  @Test
  @DisplayName("Deve registrar duvida valida e retornar protocolo")
  void deveRegistrarDuvidaValida() {
    UUID protocolo = UUID.randomUUID();
    LocalDateTime recebidaEm = LocalDateTime.of(2026, Month.JULY, 27, 12, 0);
    var request =
        new DuvidaCreateRequest("Ana Silva", "ana@email.com", "Como acompanho meus comprovantes?");
    var serviceResponse = new DuvidaCreateResponse(protocolo, recebidaEm);
    when(duvidaService.registrar(request)).thenReturn(serviceResponse);

    var response = duvidaController.registrar(request);

    assertEquals(HttpStatus.CREATED, response.getStatusCode());
    assertSame(serviceResponse, response.getBody());
    verify(duvidaService).registrar(request);
  }

  @Test
  @DisplayName("Deve rejeitar nome, email e duvida invalidos no contrato de entrada")
  void deveRejeitarFormularioInvalido() {
    var request = new DuvidaCreateRequest("", "email-invalido", "curta");

    Set<String> camposInvalidos;
    try (var validatorFactory = Validation.buildDefaultValidatorFactory()) {
      camposInvalidos =
          validatorFactory.getValidator().validate(request).stream()
              .map(violation -> violation.getPropertyPath().toString())
              .collect(Collectors.toSet());
    }

    assertEquals(Set.of("nome", "email", "duvida"), camposInvalidos);
  }

  @Test
  @DisplayName("Deve listar duvidas para consulta administrativa")
  void deveListarDuvidas() {
    var serviceResponse = new DuvidaListResponse(List.of(), 0, 12, 0, 0);
    when(duvidaService.listar("ana", DuvidaStatus.PENDENTE, 0, 12)).thenReturn(serviceResponse);

    var response = duvidaController.listar("ana", DuvidaStatus.PENDENTE, 0, 12);

    assertSame(serviceResponse, response);
    verify(duvidaService).listar("ana", DuvidaStatus.PENDENTE, 0, 12);
  }

  @Test
  @DisplayName("Deve atualizar situacao da duvida")
  void deveAtualizarStatus() {
    UUID protocolo = UUID.randomUUID();
    var request = new DuvidaStatusUpdateRequest(DuvidaStatus.RESPONDIDA);
    var serviceResponse =
        new DuvidaResponse(
            protocolo,
            "Ana",
            "ana@email.com",
            "Como funciona?",
            LocalDateTime.of(2026, Month.JULY, 27, 12, 0),
            DuvidaStatus.RESPONDIDA);
    when(duvidaService.atualizarStatus(protocolo, DuvidaStatus.RESPONDIDA))
        .thenReturn(serviceResponse);

    var response = duvidaController.atualizarStatus(protocolo, request);

    assertSame(serviceResponse, response);
    verify(duvidaService).atualizarStatus(protocolo, DuvidaStatus.RESPONDIDA);
  }

  @Test
  @DisplayName("Deve restringir consulta e atualizacao a role admin")
  void deveRestringirOperacoesAdministrativas() throws Exception {
    PreAuthorize listAuthorization =
        DuvidaController.class
            .getMethod("listar", String.class, DuvidaStatus.class, int.class, int.class)
            .getAnnotation(PreAuthorize.class);
    PreAuthorize updateAuthorization =
        DuvidaController.class
            .getMethod("atualizarStatus", UUID.class, DuvidaStatusUpdateRequest.class)
            .getAnnotation(PreAuthorize.class);

    assertNotNull(listAuthorization);
    assertNotNull(updateAuthorization);
    assertEquals("hasRole('ADMIN')", listAuthorization.value());
    assertEquals("hasRole('ADMIN')", updateAuthorization.value());
  }
}
