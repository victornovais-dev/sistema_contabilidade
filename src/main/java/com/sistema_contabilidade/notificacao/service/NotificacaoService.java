package com.sistema_contabilidade.notificacao.service;

import com.sistema_contabilidade.common.util.CandidateRoleUtils;
import com.sistema_contabilidade.item.model.Item;
import com.sistema_contabilidade.item.model.TipoItem;
import com.sistema_contabilidade.item.repository.ItemRepository;
import com.sistema_contabilidade.item.service.ItemAccessUtils;
import com.sistema_contabilidade.notificacao.dto.NotificacaoListResponse;
import com.sistema_contabilidade.notificacao.model.Notificacao;
import com.sistema_contabilidade.notificacao.repository.NotificacaoRepository;
import com.sistema_contabilidade.rbac.repository.RoleRepository;
import com.sistema_contabilidade.usuario.repository.UsuarioRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class NotificacaoService {

  private final ItemRepository itemRepository;
  private final NotificacaoRepository notificacaoRepository;
  private final UsuarioRepository usuarioRepository;
  private final RoleRepository roleRepository;

  @Transactional
  public void registrarReceitaLancada(Item item) {
    sincronizarComItemInterno(item);
  }

  @Transactional
  public void sincronizarComItem(Item item) {
    sincronizarComItemInterno(item);
  }

  private void sincronizarComItemInterno(Item item) {
    if (item == null || item.getId() == null) {
      return;
    }

    if (item.getTipo() != TipoItem.RECEITA) {
      notificacaoRepository.deleteByItemId(item.getId());
      return;
    }

    Notificacao notificacao =
        notificacaoRepository.findFirstByItemId(item.getId()).orElseGet(Notificacao::new);
    LocalDateTime criadoEm =
        item.getHorarioCriacao() == null ? LocalDateTime.now() : item.getHorarioCriacao();
    String roleNome = ItemAccessUtils.normalizarRole(item.getRoleNome());
    boolean nova = notificacao.getId() == null;
    boolean alterada = nova;

    if (!Objects.equals(notificacao.getItemId(), item.getId())) {
      notificacao.setItemId(item.getId());
      alterada = true;
    }
    if (!Objects.equals(notificacao.getRoleNome(), roleNome)) {
      notificacao.setRoleNome(roleNome);
      alterada = true;
    }
    if (!Objects.equals(notificacao.getDescricao(), item.getDescricao())) {
      notificacao.setDescricao(item.getDescricao());
      alterada = true;
    }
    if (!Objects.equals(notificacao.getRazaoSocialNome(), item.getRazaoSocialNome())) {
      notificacao.setRazaoSocialNome(item.getRazaoSocialNome());
      alterada = true;
    }
    if (!Objects.equals(notificacao.getValor(), item.getValor())) {
      notificacao.setValor(item.getValor());
      alterada = true;
    }
    if (!Objects.equals(notificacao.getCriadoEm(), criadoEm)) {
      notificacao.setCriadoEm(criadoEm);
      alterada = true;
    }
    if (notificacao.getId() == null) {
      notificacao.setLimpa(false);
      alterada = true;
    }
    if (alterada) {
      notificacaoRepository.save(notificacao);
    }
  }

  @Transactional
  public void removerPorItemId(java.util.UUID itemId) {
    if (itemId == null) {
      return;
    }
    notificacaoRepository.deleteByItemId(itemId);
  }

  @Transactional
  public List<NotificacaoListResponse> listar(Authentication authentication, String roleFiltro) {
    String roleFiltroNormalizada = ItemAccessUtils.normalizarRole(roleFiltro);
    boolean admin = ItemAccessUtils.isAdmin(authentication);
    notificacaoRepository.deleteOrfasOuInvalidas();
    garantirNotificacoesDasReceitas(authentication, roleFiltroNormalizada, admin);

    if (roleFiltroNormalizada == null && admin) {
      return notificacaoRepository.findAllResumoOrderByCriadoEmDesc();
    }
    if (roleFiltroNormalizada != null && admin) {
      return notificacaoRepository.findResumoByRoleNomeOrderByCriadoEmDesc(roleFiltroNormalizada);
    }

    Set<String> roleNomesUsuario =
        ItemAccessUtils.extrairRoleNomes(
            ItemAccessUtils.buscarUsuarioAutenticado(authentication, usuarioRepository));
    if (roleNomesUsuario.isEmpty()) {
      return List.of();
    }
    if (roleFiltroNormalizada != null) {
      ItemAccessUtils.validarRoleFiltro(roleFiltroNormalizada, roleNomesUsuario);
      return notificacaoRepository.findResumoByRoleNomeOrderByCriadoEmDesc(roleFiltroNormalizada);
    }
    return notificacaoRepository.findResumoByRoleNomesOrderByCriadoEmDesc(roleNomesUsuario);
  }

  @Transactional(readOnly = true)
  public List<String> listarRolesDisponiveis(Authentication authentication) {
    if (ItemAccessUtils.isAdmin(authentication)) {
      return CandidateRoleUtils.filterCandidateRoles(roleRepository.findAllRoleNamesOrdered());
    }

    return CandidateRoleUtils.filterCandidateRoles(
        ItemAccessUtils.extrairRoleNomes(
            ItemAccessUtils.buscarUsuarioAutenticado(authentication, usuarioRepository)));
  }

  @Transactional
  public NotificacaoListResponse atualizarLimpeza(
      Authentication authentication, java.util.UUID id, boolean limpa) {
    Notificacao notificacao =
        notificacaoRepository
            .findById(id)
            .orElseThrow(
                () ->
                    new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Notificacao nao encontrada."));

    validarAcesso(notificacao, authentication);
    notificacao.setLimpa(limpa);
    Notificacao salva = notificacaoRepository.save(notificacao);
    return new NotificacaoListResponse(
        salva.getId(),
        salva.getItemId(),
        salva.getRoleNome(),
        salva.getDescricao(),
        salva.getRazaoSocialNome(),
        salva.getValor(),
        salva.getCriadoEm(),
        salva.isLimpa());
  }

  private void validarAcesso(Notificacao notificacao, Authentication authentication) {
    if (ItemAccessUtils.isAdmin(authentication)) {
      return;
    }

    Set<String> roleNomesUsuario =
        ItemAccessUtils.extrairRoleNomes(
            ItemAccessUtils.buscarUsuarioAutenticado(authentication, usuarioRepository));
    String roleNotificacao = ItemAccessUtils.normalizarRole(notificacao.getRoleNome());
    ItemAccessUtils.validarRoleFiltro(roleNotificacao, roleNomesUsuario);
  }

  private void garantirNotificacoesDasReceitas(
      Authentication authentication, String roleFiltroNormalizada, boolean admin) {
    if (roleFiltroNormalizada == null && admin) {
      itemRepository
          .findReceitasOrderByHorarioCriacaoDesc()
          .forEach(this::sincronizarComItemInterno);
      return;
    }
    if (roleFiltroNormalizada != null && admin) {
      itemRepository
          .findReceitasPorRoleNomeOrderByHorarioCriacaoDesc(roleFiltroNormalizada)
          .forEach(this::sincronizarComItemInterno);
      return;
    }

    Set<String> roleNomesUsuario =
        ItemAccessUtils.extrairRoleNomes(
            ItemAccessUtils.buscarUsuarioAutenticado(authentication, usuarioRepository));
    if (roleNomesUsuario.isEmpty()) {
      return;
    }
    if (roleFiltroNormalizada != null) {
      ItemAccessUtils.validarRoleFiltro(roleFiltroNormalizada, roleNomesUsuario);
      itemRepository
          .findReceitasPorRoleNomeOrderByHorarioCriacaoDesc(roleFiltroNormalizada)
          .forEach(this::sincronizarComItemInterno);
      return;
    }
    itemRepository
        .findReceitasPorRoleNomesOrderByHorarioCriacaoDesc(roleNomesUsuario)
        .forEach(this::sincronizarComItemInterno);
  }
}
