package com.lotusblight.lib;

import net.minecraftforge.fml.ModList;

import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Discovers optional Forge integrations without hard-linking their classes. */
public final class LotusIntegrationManager {
    private final Map<String, LotusIntegration> integrations = new LinkedHashMap<>();
    private final Map<String, LotusIntegrationSpec> specs = new LinkedHashMap<>();
    // Priority order is fixed once registration (a handful of calls at mod init) settles, so it's
    // sorted once here instead of every server tick forever.
    private List<LotusIntegration> tickOrderCache;

    public void register(LotusIntegration integration) {
        if (integration != null) {
            register(integration, LotusIntegrationSpec.optional(integration.modId(), "", "", 0));
        }
    }

    public void register(LotusIntegration integration, LotusIntegrationSpec spec) {
        if (integration == null || spec == null || !ModList.get().isLoaded(spec.modId())) return;
        if (!integrations.containsKey(spec.modId()) || specs.get(spec.modId()).priority() <= spec.priority()) {
            integrations.put(spec.modId(), integration);
            specs.put(spec.modId(), spec);
            tickOrderCache = null;
        }
    }

    public boolean isEnabled(String modId) {
        return integrations.containsKey(modId);
    }

    public Collection<LotusIntegration> enabledIntegrations() {
        return integrations.values();
    }

    public void tick() {
        if (tickOrderCache == null) {
            tickOrderCache = integrations.values().stream()
                    .sorted(Comparator.<LotusIntegration>comparingInt(i -> specs.get(i.modId()).priority()).reversed())
                    .toList();
        }
        for (LotusIntegration integration : tickOrderCache) {
            if (integration.enabled()) {
                integration.onServerTick();
            }
        }
    }
}
