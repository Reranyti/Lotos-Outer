package com.lotusblight.branch;

import com.lotusblight.LotusBlight;
import net.minecraftforge.fml.loading.LoadingModList;
import net.minecraftforge.fml.loading.moddiscovery.ModFileInfo;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Where this mod actually sits on disk. Under Forge a class's CodeSource points into the mod's union
 * filesystem (union:/...jar%23NN!/), not at the jar file itself, so the path comes from the loading mod
 * list instead - it records the real file, whichever launcher or folder it was loaded from.
 */
public final class NormalBranchJar {
    private NormalBranchJar() {}

    /** The installed mod jar, or null when running from build folders (dev) or if it can't be told. */
    public static Path jar() {
        try {
            ModFileInfo info = LoadingModList.get().getModFileById(LotusBlight.MODID);
            if (info == null) return null;
            Path path = info.getFile().getFilePath();
            return path != null && Files.isRegularFile(path) ? path : null;
        } catch (Exception e) {
            return null;
        }
    }

    /** The mod's version as declared in mods.toml, or "unknown". */
    public static String version() {
        try {
            ModFileInfo info = LoadingModList.get().getModFileById(LotusBlight.MODID);
            return info.getMods().get(0).getVersion().toString();
        } catch (Exception e) {
            return "unknown";
        }
    }

    /**
     * A classpath holding the mod's own classes and resources, for starting a separate Java process from
     * them: the jar itself, or in a ForgeGradle dev run the build folders listed in MOD_CLASSES
     * ("modid%%path" entries). Null if neither is available.
     */
    public static String classpath() {
        Path jar = jar();
        if (jar != null) return jar.toString();
        String env = System.getenv("MOD_CLASSES");
        if (env == null || env.isEmpty()) return null;
        List<String> parts = new ArrayList<>();
        for (String entry : env.split(File.pathSeparator)) {
            int at = entry.indexOf("%%");
            String path = at >= 0 ? entry.substring(at + 2) : entry;
            if (!path.isEmpty()) parts.add(path);
        }
        return parts.isEmpty() ? null : String.join(File.pathSeparator, parts);
    }
}
