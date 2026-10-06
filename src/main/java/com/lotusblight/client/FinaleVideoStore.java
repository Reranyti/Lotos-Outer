package com.lotusblight.client;

import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import org.slf4j.Logger;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Where the finale video lives while the fight needs it. It is not in the jar (it is 120 MB, four fifths of the mod): it is downloaded
 * from the repository's release to a drive with enough room, preferably not the one the game is on and not the system one, read into
 * memory by the fight, and deleted at once. Whatever is left (the game closed mid-download, a crash) is swept up at the next start and
 * when the game exits. No disk is searched: only the drives' free space is asked.
 */
public final class FinaleVideoStore {
    private static final Logger LOG = LogUtils.getLogger();

    /** The file as published with the release: its address, size and SHA-256, so a half-loaded or changed file is never used. */
    private static final String URL = "https://github.com/Reranyti/Lotos-Outer/releases/download/finale-video/finale.mp4";
    private static final long SIZE = 125_948_066L;
    private static final String SHA256 = "6f0141218b4281e327acecc5a428b3e2ee1e1b13cc22eab5f869e1b4dd07aa0d";
    private static final String DIR = ".lotusblight-cache";
    private static final String NAME = "finale.mp4";

    private static volatile File current;
    private static boolean hookInstalled;

    private FinaleVideoStore() {}

    /**
     * The video, ready to be read, or null if it can't be had (no network, no room, a wrong file): the fight then goes on without it.
     * Blocks while downloading, so call it off the game's thread.
     */
    public static File obtain(File gameDir) {
        java.util.concurrent.CompletableFuture<File> running = prefetching;
        if (running != null) {                                   // the download begun at launch: wait for it rather than start another
            try {
                File f = running.get();
                if (f != null && f.isFile()) return f;
            } catch (Exception ignored) {
            }
        }
        return fetch(gameDir);
    }

    private static volatile java.util.concurrent.CompletableFuture<File> prefetching;
    private static volatile boolean cancelled;

    /** Starts the download in the background (at the game's launch), so the fight finds the video already there. */
    public static void prefetch(File gameDir) {
        if (prefetching != null) return;
        cancelled = false;
        prefetching = java.util.concurrent.CompletableFuture.supplyAsync(() -> fetch(gameDir), r -> {
            Thread t = new Thread(r, "LotusBlight video fetch");
            t.setDaemon(true);
            t.setPriority(Thread.MIN_PRIORITY);
            t.start();
        });
    }

    private static synchronized File fetch(File gameDir) {
        sweep(gameDir);
        installHook(gameDir);
        List<File> places = candidates(gameDir);
        for (File dir : places) {
            File target = new File(dir, NAME);
            try {
                Files.createDirectories(dir.toPath());
                if (!canWrite(dir)) continue;
                if (target.isFile() && target.length() == SIZE && matches(target)) {
                    remember(gameDir, target);
                    return current = target;
                }
                if (dir.getUsableSpace() < SIZE + SIZE / 4 + 200L * 1024 * 1024) continue;
                File part = new File(dir, NAME + ".part");
                remember(gameDir, target);                           // so it is swept even if we die half way
                download(part);
                if (part.length() != SIZE || !matches(part)) {
                    Files.deleteIfExists(part.toPath());
                    LOG.warn("Finale video: downloaded file is not the expected one");
                    continue;
                }
                Files.move(part.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
                return current = target;
            } catch (Exception e) {
                LOG.warn("Finale video: {} could not be used: {}", dir, e.toString());
                try {
                    Files.deleteIfExists(new File(dir, NAME + ".part").toPath());
                } catch (IOException ignored) {
                }
            }
        }
        return null;
    }

    /** Deletes the video and its folder if it is empty. */
    public static void remove(File gameDir) {
        cancelled = true;
        File f = current;
        current = null;
        prefetching = null;
        if (f != null) deleteWithDir(f);
        sweep(gameDir);
    }

    // ------------------------------------------------------------------ where

    /** Drives in the order to try: not the game's, not the system one; then the game's; then the system drive; then the temp folder. */
    private static List<File> candidates(File gameDir) {
        String gameRoot = rootOf(gameDir);
        String sys = System.getenv("SystemDrive");
        String sysRoot = sys == null ? null : (sys.endsWith("\\") ? sys : sys + "\\").toUpperCase();
        List<File> first = new ArrayList<>(), second = new ArrayList<>(), third = new ArrayList<>();
        File[] roots = File.listRoots();
        if (roots != null) {
            for (File root : roots) {
                String r = root.getPath().toUpperCase();
                File dir = new File(root, DIR);
                boolean isGame = r.equals(gameRoot), isSys = sysRoot != null && r.equals(sysRoot);
                if (!isGame && !isSys) first.add(dir);
                else if (isGame && !isSys) second.add(dir);
                else third.add(dir);
            }
        }
        List<File> all = new ArrayList<>(first);
        all.addAll(second);
        all.addAll(third);
        all.add(new File(System.getProperty("java.io.tmpdir"), DIR));
        return all;
    }

    private static String rootOf(File f) {
        Path root = f.toPath().toAbsolutePath().getRoot();
        return root == null ? "" : root.toString().toUpperCase();
    }

    private static boolean canWrite(File dir) {
        try {
            File probe = new File(dir, ".probe");
            Files.writeString(probe.toPath(), "x");
            Files.deleteIfExists(probe.toPath());
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    // ------------------------------------------------------------------ download and check

    private static void download(File part) throws Exception {
        HttpClient client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.ALWAYS).connectTimeout(Duration.ofSeconds(15)).build();
        HttpRequest req = HttpRequest.newBuilder(URI.create(URL)).timeout(Duration.ofMinutes(30)).GET().build();
        HttpResponse<InputStream> res = client.send(req, HttpResponse.BodyHandlers.ofInputStream());
        if (res.statusCode() != 200) throw new IOException("HTTP " + res.statusCode());
        try (InputStream in = res.body(); java.io.OutputStream out = Files.newOutputStream(part.toPath())) {
            byte[] buf = new byte[1 << 16];
            int n;
            while ((n = in.read(buf)) > 0) {
                if (cancelled) throw new IOException("cancelled");        // the player left the path: stop at once
                out.write(buf, 0, n);
            }
        }
    }

    private static boolean matches(File f) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        try (InputStream in = Files.newInputStream(f.toPath())) {
            byte[] buf = new byte[1 << 20];
            int n;
            while ((n = in.read(buf)) > 0) md.update(buf, 0, n);
        }
        StringBuilder sb = new StringBuilder();
        for (byte b : md.digest()) sb.append(String.format("%02x", b));
        return sb.toString().equals(SHA256);
    }

    // ------------------------------------------------------------------ clean-up

    private static File marker(File gameDir) {
        return new File(new File(gameDir, "config/lotusblight"), "finale_video_path.txt");
    }

    private static void remember(File gameDir, File video) {
        try {
            File m = marker(gameDir);
            Files.createDirectories(m.getParentFile().toPath());
            Files.writeString(m.toPath(), video.getAbsolutePath());
        } catch (IOException ignored) {
        }
    }

    /** Removes whatever an earlier run left: the file named in the marker, a half-loaded part, and the folder. */
    private static void sweep(File gameDir) {
        try {
            File m = marker(gameDir);
            if (m.isFile()) {
                File video = new File(Files.readString(m.toPath()).trim());
                if (video.getName().equals(NAME) && DIR.equals(video.getParentFile() == null ? null : video.getParentFile().getName())) deleteWithDir(video);
                Files.deleteIfExists(m.toPath());
            }
        } catch (Exception ignored) {
        }
    }

    private static void deleteWithDir(File video) {
        try {
            Files.deleteIfExists(video.toPath());
            Files.deleteIfExists(new File(video.getParentFile(), NAME + ".part").toPath());
            File dir = video.getParentFile();
            String[] left = dir == null ? null : dir.list();
            if (dir != null && left != null && left.length == 0) Files.deleteIfExists(dir.toPath());
        } catch (IOException e) {
            LOG.warn("Finale video: could not delete {}: {}", video, e.toString());
        }
    }

    private static void installHook(File gameDir) {
        if (hookInstalled) return;
        hookInstalled = true;
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            File f = current;
            if (f != null) deleteWithDir(f);
            sweep(gameDir);
        }, "LotusBlight video clean-up"));
    }

    /** The game's folder, for callers that have only the client. */
    public static File gameDir() {
        return Minecraft.getInstance().gameDirectory;
    }
}
