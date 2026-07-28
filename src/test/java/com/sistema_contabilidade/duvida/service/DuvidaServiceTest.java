package com.sistema_contabilidade.duvida.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sistema_contabilidade.duvida.dto.DuvidaCreateRequest;
import com.sistema_contabilidade.duvida.model.Duvida;
import com.sistema_contabilidade.duvida.model.DuvidaStatus;
import com.sistema_contabilidade.duvida.repository.DuvidaRepository;
import com.sistema_contabilidade.security.validation.InputSanitizer;
import java.time.LocalDateTime;
import java.time.Month;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
@DisplayName("DuvidaService unit tests")
class DuvidaServiceTest {

  @Mock private DuvidaRepository duvidaRepository;
  @Mock private InputSanitizer inputSanitizer;

  private DuvidaService duvidaService;

  @BeforeEach
  void setUp() {
    duvidaService = new DuvidaService(duvidaRepository, inputSanitizer);
  }

  @Test
  @DisplayName("Deve sanitizar, persistir e devolver protocolo da duvida")
  void deveRegistrarDuvidaComDadosSanitizados() {
    UUID protocolo = UUID.randomUUID();
    var request =
        new DuvidaCreateRequest(
            "  Ana Silva  ", "  ANA@EMAIL.COM  ", " Como acompanho meus comprovantes? ");
    when(inputSanitizer.sanitizeInlineText(request.nome(), "nome", 120)).thenReturn("Ana Silva");
    when(inputSanitizer.sanitizeEmail(request.email(), "email")).thenReturn("ana@email.com");
    when(inputSanitizer.sanitizeMultilineText(request.duvida(), "duvida", 1200))
        .thenReturn("Como acompanho meus comprovantes?");
    when(duvidaRepository.save(org.mockito.ArgumentMatchers.any(Duvida.class)))
        .thenAnswer(
            invocation -> {
              Duvida duvida = invocation.getArgument(0);
              duvida.setId(protocolo);
              return duvida;
            });

    var response = duvidaService.registrar(request);

    ArgumentCaptor<Duvida> captor = ArgumentCaptor.forClass(Duvida.class);
    verify(duvidaRepository).save(captor.capture());
    Duvida salva = captor.getValue();
    assertEquals("Ana Silva", salva.getNome());
    assertEquals("ana@email.com", salva.getEmail());
    assertEquals("Como acompanho meus comprovantes?", salva.getMensagem());
    assertEquals(DuvidaStatus.PENDENTE, salva.getStatus());
    assertEquals(protocolo, response.protocolo());
    assertEquals(salva.getRecebidaEm(), response.recebidaEm());
  }

  @Test
  @DisplayName("Deve listar duvidas filtradas e paginadas")
  void deveListarDuvidasFiltradas() {
    Duvida duvida = criarDuvida(DuvidaStatus.PENDENTE);
    when(duvidaRepository.buscar(
            org.mockito.ArgumentMatchers.eq("comprovante"),
            org.mockito.ArgumentMatchers.eq(DuvidaStatus.PENDENTE),
            org.mockito.ArgumentMatchers.any(Pageable.class)))
        .thenReturn(new PageImpl<>(List.of(duvida)));

    var response = duvidaService.listar("  COMPROVANTE  ", DuvidaStatus.PENDENTE, 0, 12);

    assertEquals(1, response.totalElementos());
    assertEquals(1, response.duvidas().size());
    assertEquals(duvida.getId(), response.duvidas().getFirst().protocolo());
    assertEquals(DuvidaStatus.PENDENTE, response.duvidas().getFirst().status());
  }

  @Test
  @DisplayName("Deve atualizar situacao de duvida existente")
  void deveAtualizarStatusDaDuvida() {
    Duvida duvida = criarDuvida(DuvidaStatus.PENDENTE);
    when(duvidaRepository.findById(duvida.getId())).thenReturn(Optional.of(duvida));
    when(duvidaRepository.save(duvida)).thenReturn(duvida);

    var response = duvidaService.atualizarStatus(duvida.getId(), DuvidaStatus.RESPONDIDA);

    assertEquals(DuvidaStatus.RESPONDIDA, response.status());
    verify(duvidaRepository).save(duvida);
  }

  private Duvida criarDuvida(DuvidaStatus status) {
    Duvida duvida = new Duvida();
    duvida.setId(UUID.randomUUID());
    duvida.setNome("Ana Silva");
    duvida.setEmail("ana@email.com");
    duvida.setMensagem("Como acompanho meu comprovante?");
    duvida.setRecebidaEm(LocalDateTime.of(2026, Month.JULY, 27, 12, 0));
    duvida.setStatus(status);
    return duvida;
  }
}
