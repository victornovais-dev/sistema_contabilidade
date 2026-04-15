package com.sistema_contabilidade.notificacao.service;

import com.sistema_contabilidade.item.model.Item;
import com.sistema_contabilidade.item.model.TipoItem;
import com.sistema_contabilidade.item.service.ItemAccessUtils;
import com.sistema_contabilidade.notificacao.dto.NotificacaoListResponse;
import com.sistema_contabilidade.notificacao.model.Notificacao;
import com.sistema_contabilidade.notificacao.repository.NotificacaoRepository;
import com.sistema_contabilidade.rbac.repository.RoleRepository;
import com.sistema_contabilidade.usuario.repository.UsuarioRepository;
import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class NotificacaoService {

  private final NotificacaoRepository notificacaoRepository;
  private final UsuarioRepository usuarioRepository;
  private final RoleRepository roleRepository;

  @Transactional
  public void registrarReceitaLancada(Item item) {
    if (item == null || item.getTipo() != TipoItem.RECEITA) {
      return;
    }

    Notificacao notificacao = new Notificacao();
    notificacao.setItemId(item.getId());
    notificacao.setRoleNome(ItemAccessUtils.normalizarRole(item.getRoleNome()));
    notificacao.setDescricao(item.getDescricao());
    notificacao.setRazaoSocialNome(item.getRazaoSocialNome());
    notificacao.setValor(item.getValor());
    notificacao.setCriadoEm(
        item.getHorarioCriacao() == null ? LocalDateTime.now() : item.getHorarioCriacao());
    notificacaoRepository.save(notificacao);
  }

  @Transactional(readOnly = true)
  public List<NotificacaoListResponse> listar(Authentication authentication, String roleFiltro) {
    String roleFiltroNormalizada = ItemAccessUtils.normalizarRole(roleFiltro);
    boolean admin = ItemAccessUtils.isAdmin(authentication);

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
      return roleRepository.findAllRoleNamesOrdered();
    }

    return ItemAccessUtils.extrairRoleNomes(
            ItemAccessUtils.buscarUsuarioAutenticado(authentication, usuarioRepository))
        .stream()
        .filter(java.util.Objects::nonNull)
        .collect(Collectors.toCollection(LinkedHashSet::new))
        .stream()
        .sorted()
        .toList();
  }
}
