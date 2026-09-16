package com.lotusblight.lib;

import net.minecraftforge.fml.ModList;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Comparator;

/** Discovers optional Forge integrations without hard-linking their classes. */
public final class LotusIntegrationManager {
    private final Map<String, LotusIntegration> integrations = new LinkedHashMap<>();
    private final Map<String, LotusIntegrationSpec> specs = new LinkedHashMap<>();

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
        }
    }

    public boolean isEnabled(String modId) {
        return integrations.containsKey(modId);
    }

    public Collection<LotusIntegration> enabledIntegrations() {
        return integrations.values();
    }

    public void tick() {
        integrations.values().stream()
                .sorted(Comparator.<LotusIntegration>comparingInt(i -> specs.get(i.modId()).priority()).reversed())
                .filter(LotusIntegration::enabled)
                .forEach(LotusIntegration::onServerTick);
    }
}
