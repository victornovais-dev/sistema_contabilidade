package com.sistema_contabilidade.privacidade.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sistema_contabilidade.database.crypto.service.BlindIndexService;
import com.sistema_contabilidade.database.crypto.service.DatabaseCryptoService;
import com.sistema_contabilidade.privacidade.model.SolicitacaoPrivacidade;
import com.sistema_contabilidade.privacidade.model.SolicitacaoPrivacidadeCanal;
import com.sistema_contabilidade.privacidade.model.SolicitacaoPrivacidadeStatus;
import com.sistema_contabilidade.privacidade.model.SolicitacaoPrivacidadeTipo;
import com.sistema_contabilidade.privacidade.model.SolicitacaoPrivacidadeVinculo;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Month;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
@Import({DatabaseCryptoService.class, BlindIndexService.class})
@DisplayName("SolicitacaoPrivacidadeRepository DataJpa tests")
class SolicitacaoPrivacidadeRepositoryTest {

  @Autowired private SolicitacaoPrivacidadeRepository solicitacaoRepository;
  @Autowired private BlindIndexService blindIndexService;
  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private EntityManager entityManager;

  @Test
  @DisplayName("Deve cifrar dados pessoais e consultar por protocolo e indice cego do email")
  void devePersistirDadosCifradosEConsultarSemEmailEmClaro() {
    SolicitacaoPrivacidade solicitacao = novaSolicitacao();
    solicitacaoRepository.saveAndFlush(solicitacao);
    entityManager.clear();

    Map<String, Object> raw =
        jdbcTemplate.queryForMap(
            """
            select nome_titular, email_titular, descricao, email_bidx
            from solicitacoes_privacidade
            where id = ?
            """,
            solicitacao.getId());

    assertTrue(raw.get("nome_titular").toString().startsWith("enc:v1:"));
    assertTrue(raw.get("email_titular").toString().startsWith("enc:v1:"));
    assertTrue(raw.get("descricao").toString().startsWith("enc:v1:"));
    assertNotEquals("titular@email.com", raw.get("email_titular"));
    assertEquals(64, raw.get("email_bidx").toString().length());

    var encontrada =
        solicitacaoRepository.findByProtocoloAndEmailBlindIndex(
            solicitacao.getProtocolo(), blindIndexService.email(" TITULAR@EMAIL.COM "));
    assertTrue(encontrada.isPresent());
    assertEquals("Titular Teste", encontrada.orElseThrow().getNomeTitular());
    assertFalse(
        solicitacaoRepository
            .findByProtocoloAndEmailBlindIndex(
                solicitacao.getProtocolo(), blindIndexService.email("outro@email.com"))
            .isPresent());
  }

  private SolicitacaoPrivacidade novaSolicitacao() {
    LocalDate hoje = LocalDate.of(2026, Month.JULY, 30);
    LocalDateTime agora = hoje.atTime(10, 0);
    SolicitacaoPrivacidade solicitacao = new SolicitacaoPrivacidade();
    solicitacao.setProtocolo("LGPD-2026-A1B2C3D4E5F6");
    solicitacao.setNomeTitular("Titular Teste");
    solicitacao.setEmailTitular("titular@email.com");
    solicitacao.setOrganizacao("Campanha Teste");
    solicitacao.setVinculo(SolicitacaoPrivacidadeVinculo.USUARIO);
    solicitacao.setTipo(SolicitacaoPrivacidadeTipo.ACESSO);
    solicitacao.setEscopos("CADASTRO_PERFIL");
    solicitacao.setCanalResposta(SolicitacaoPrivacidadeCanal.EMAIL);
    solicitacao.setStatus(SolicitacaoPrivacidadeStatus.IDENTIDADE_PENDENTE);
    solicitacao.setRecebidaEm(hoje);
    solicitacao.setPrazo(hoje.plusDays(15));
    solicitacao.setResponsavel("Equipe de Privacidade");
    solicitacao.setDescricao("Quero acessar os dados associados ao meu cadastro.");
    solicitacao.setVersaoAviso("2026-07");
    solicitacao.setCriadaEm(agora);
    solicitacao.setAtualizadaEm(agora);
    return solicitacao;
  }
}
