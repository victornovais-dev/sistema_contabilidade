package com.sistema_contabilidade.relatorio.controller;

import com.sistema_contabilidade.relatorio.dto.RelatorioFinanceiroResponse;
import com.sistema_contabilidade.relatorio.dto.RelatorioFinanceiroResumoResponse;
import com.sistema_contabilidade.relatorio.service.RelatorioFinanceiroService;
import java.util.List;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.security.core.Authentication;

@TestConfiguration
class RelatorioControllerWebMvcTestConfig {

  @Bean
  StubRelatorioFinanceiroService relatorioFinanceiroService() {
    return new StubRelatorioFinanceiroService();
  }

  static class StubRelatorioFinanceiroService extends RelatorioFinanceiroService {
    RelatorioFinanceiroResumoResponse resumoResponse;
    RelatorioFinanceiroResponse relatorioResponse;
    byte[] pdfResponse = new byte[0];
    List<String> rolesResponse = List.of();

    StubRelatorioFinanceiroService() {
      super(null, null, null, null);
    }

    @Override
    public RelatorioFinanceiroResumoResponse gerarResumo(
        Authentication authentication, String roleFiltro) {
      return resumoResponse;
    }

    @Override
    public RelatorioFinanceiroResponse gerar(Authentication authentication, String roleFiltro) {
      return relatorioResponse;
    }

    @Override
    public byte[] gerarPdf(Authentication authentication, RelatorioFinanceiroResponse relatorio) {
      return pdfResponse;
    }

    @Override
    public List<String> listarRolesDisponiveis(Authentication authentication) {
      return rolesResponse;
    }
  }
}
