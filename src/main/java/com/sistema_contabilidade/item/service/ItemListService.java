package com.sistema_contabilidade.item.service;

import com.sistema_contabilidade.item.dto.ItemListResponse;
import com.sistema_contabilidade.item.repository.ItemRepository;
import com.sistema_contabilidade.rbac.repository.RoleRepository;
import com.sistema_contabilidade.usuario.repository.UsuarioRepository;
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
public class ItemListService {

  private final ItemRepository itemRepository;
  private final UsuarioRepository usuarioRepository;
  private final RoleRepository roleRepository;

  @Transactional(readOnly = true)
  public List<ItemListResponse> listarItens(Authentication authentication, String roleFiltro) {
    String roleFiltroNormalizada = ItemAccessUtils.normalizarRole(roleFiltro);
    boolean admin = ItemAccessUtils.isAdmin(authentication);

    if (roleFiltroNormalizada == null && admin) {
      return itemRepository.findAllResumoOrderByHorarioCriacaoDesc();
    }
    if (roleFiltroNormalizada != null && admin) {
      return itemRepository.findResumoVisiveisPorRoleNomeOrderByHorarioCriacaoDesc(
          roleFiltroNormalizada);
    }

    Set<String> roleNomesUsuario =
        ItemAccessUtils.extrairRoleNomes(
            ItemAccessUtils.buscarUsuarioAutenticado(authentication, usuarioRepository));
    if (roleNomesUsuario.isEmpty()) {
      return List.of();
    }
    if (roleFiltroNormalizada != null) {
      ItemAccessUtils.validarRoleFiltro(roleFiltroNormalizada, roleNomesUsuario);
      return itemRepository.findResumoVisiveisPorRoleNomeOrderByHorarioCriacaoDesc(
          roleFiltroNormalizada);
    }
    return itemRepository.findResumoVisiveisPorRoleNomesOrderByHorarioCriacaoDesc(roleNomesUsuario);
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
