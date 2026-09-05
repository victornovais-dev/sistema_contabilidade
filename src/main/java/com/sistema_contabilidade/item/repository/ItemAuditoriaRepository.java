package com.sistema_contabilidade.item.repository;

import com.sistema_contabilidade.item.model.ItemAuditoria;
import java.util.List;
import java.util.UUID;
import org.springframework.data.repository.Repository;

public interface ItemAuditoriaRepository extends Repository<ItemAuditoria, UUID> {

  ItemAuditoria save(ItemAuditoria auditoria);

  List<ItemAuditoria> findByItemIdOrderByOcorridoEmDescIdDesc(UUID itemId);
}
