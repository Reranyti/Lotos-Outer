package com.lotusblight.branch;

import com.lotusblight.LotusBlight;
import net.minecraftforge.fml.loading.FMLPaths;
import org.slf4j.Logger;
import com.mojang.logging.LogUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.CodeSource;

/**
 * The safe "the mod disappears" lock for the Нормальная_ветка ending. When the ending fires we write a
 * small marker in the config folder that records this exact installed jar. On the next launch, if the
 * marker still matches the jar in place, the mod loads INERT - its blocks and items stay registered so
 * old worlds don't break, but its logic, events, spread, HUD and dialogue all stand down, so to the
 * player it's as if the mod is gone. Dropping a fresh jar in (a reinstall) changes the fingerprint and
 * the lock lifts. Nothing is ever deleted; real file removal is only ever done by the opt-in Consequences
 * add-on, never here.
 */
public final class NormalBranchLock {
    private static final Logger LOG = LogUtils.getLogger();
    private static final String MARKER = "normalbranch.lock";

    private static boolean locked;
    private static boolean checked;

    private NormalBranchLock() {}

    /** True once the ending has fired for this exact jar and it has not been reinstalled since. */
    public static boolean isLocked() {
        if (!checked) {
            checked = true;
            try {
                Path marker = markerPath();
                locked = Files.exists(marker) && Files.readString(marker).trim().equals(fingerprint());
            } catch (Exception e) {
                locked = false;
            }
            if (locked) LOG.info("Lotus Blight is standing down (Нормальная_ветка lock in place; reinstall to lift).");
        }
        return locked;
    }

    /** Writes the lock for this jar. From the next launch on, the mod loads inert until reinstalled. */
    public static void engage() {
        try {
            Path marker = markerPath();
            Files.createDirectories(marker.getParent());
            Files.writeString(marker, fingerprint());
            locked = true;
            checked = true;
        } catch (Exception e) {
            LOG.warn("Could not write the Нормальная_ветка lock: {}", e.toString());
        }
    }

    private static Path markerPath() {
        return FMLPaths.CONFIGDIR.get().resolve(LotusBlight.MODID).resolve(MARKER);
    }

    /** Identifies the installed jar - its size and modified time, so a reinstall reads as a new file. */
    private static String fingerprint() {
        try {
            CodeSource src = NormalBranchLock.class.getProtectionDomain().getCodeSource();
            if (src == null || src.getLocation() == null) return "dev";
            Path jar = Path.of(src.getLocation().toURI());
            if (!Files.isRegularFile(jar)) return "dev";      // running from classes, not a jar
            return jar.getFileName() + "|" + Files.size(jar) + "|" + Files.getLastModifiedTime(jar).toMillis();
        } catch (Exception e) {
            return "dev";
        }
    }
}
