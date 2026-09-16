package com.lotusblight.lib;

/** Declarative compatibility rules for an optional integration. */
public record LotusIntegrationSpec(
        String modId,
        String minimumVersion,
        String maximumVersion,
        int priority,
        boolean optional
) {
    public static LotusIntegrationSpec optional(String modId, String minimumVersion, String maximumVersion, int priority) {
        return new LotusIntegrationSpec(modId, minimumVersion, maximumVersion, priority, true);
    }
}
