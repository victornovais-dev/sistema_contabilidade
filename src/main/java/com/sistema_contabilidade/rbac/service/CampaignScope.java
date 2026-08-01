package com.sistema_contabilidade.rbac.service;

import java.util.Collections;
import java.util.Set;
import java.util.TreeSet;

/** Effective campaign scope used to isolate item and report data. */
public record CampaignScope(boolean allCampaigns, Set<String> campaignNames, String roleFilter) {

  public CampaignScope {
    campaignNames = Collections.unmodifiableSet(new TreeSet<>(campaignNames));
  }

  public static CampaignScope all() {
    return new CampaignScope(true, Set.of(), null);
  }

  public static CampaignScope restricted(Set<String> campaignNames) {
    return new CampaignScope(false, campaignNames, null);
  }

  public CampaignScope withRoleFilter(String normalizedRoleFilter) {
    return new CampaignScope(allCampaigns, campaignNames, normalizedRoleFilter);
  }

  public Set<String> effectiveCampaignNames() {
    if (roleFilter != null) {
      return Set.of(roleFilter);
    }
    return campaignNames;
  }

  public Set<String> queryCampaignNames() {
    return allCampaigns && roleFilter == null ? null : effectiveCampaignNames();
  }

  public String canonicalCacheScope() {
    if (allCampaigns && roleFilter == null) {
      return "ALL";
    }
    return String.join("\u001f", effectiveCampaignNames());
  }
}
