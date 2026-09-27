package com.lotusblight.client;

import com.lotusblight.LotusBlight;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import org.lwjgl.stb.STBVorbis;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.lwjgl.system.libc.LibCStdlib;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;
import java.nio.ShortBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * What the exit process needs from the jar, put where it can use it. The fight's two tracks ship as Ogg
 * Vorbis, which plain Java can't play, so they are decoded to WAV with the stb_vorbis the game already
 * carries; the finale video and the video decoder's libraries are copied out as they are. All of it goes
 * to our own folder under the system temp directory - nothing else is written anywhere - and each file
 * appears only once complete (written under a temporary name, then moved into place).
 */
final class NormalBranchTracks {
    private static final Logger LOG = LogUtils.getLogger();
    private static final String[] SOURCES = {"overlay/map_1.ogg", "overlay/map_2.ogg"};
    private static final String VIDEO = "overlay/finale.mp4";
    /** The exit process's libraries, shipped as plain files in the jar - keep in step with build.gradle. */
    private static final String[] LIBS = {"jcodec-0.2.5.jar", "jcodec-javase-0.2.5.jar"};

    private NormalBranchTracks() {}

    /** Where the tracks go: song1.wav and song2.wav in this folder. */
    static Path folder() {
        return Path.of(System.getProperty("java.io.tmpdir"), LotusBlight.MODID);
    }

    static Path song(int n) {
        return folder().resolve("song" + n + ".wav");
    }

    static Path video() {
        return folder().resolve("finale.mp4");
    }

    /**
     * Copies the exit process's libraries out of the jar (small, done right here - they must be in
     * place before the process starts) and returns them as classpath entries. Missing ones are skipped.
     */
    static List<String> libraries(Minecraft mc) {
        List<String> paths = new ArrayList<>();
        for (String lib : LIBS) {
            Path out = folder().resolve("lib").resolve(lib);
            try {
                Optional<Resource> res = mc.getResourceManager().getResource(new ResourceLocation(LotusBlight.MODID, "overlay/lib/" + lib));
                if (res.isEmpty()) continue;
                try (InputStream in = res.get().open()) {
                    copy(in, out);
                }
                paths.add(out.toString());
            } catch (IOException e) {
                LOG.warn("Нормальная_ветка: can't unpack {}: {}", lib, e.toString());
            }
        }
        return paths;
    }

    /**
     * Reads both tracks out of the mod's resources (on the calling thread - the game thread) and decodes
     * them on a background thread. Returns right away.
     */
    static void prepare(Minecraft mc) {
        byte[][] ogg = new byte[SOURCES.length][];
        for (int i = 0; i < SOURCES.length; i++) {
            Optional<Resource> res = mc.getResourceManager().getResource(new ResourceLocation(LotusBlight.MODID, SOURCES[i]));
            if (res.isEmpty()) {
                LOG.warn("Нормальная_ветка: {} is missing", SOURCES[i]);
                continue;
            }
            try (InputStream in = res.get().open()) {
                ogg[i] = in.readAllBytes();
            } catch (IOException e) {
                LOG.warn("Нормальная_ветка: can't read {}: {}", SOURCES[i], e.toString());
            }
        }
        Optional<Resource> video = mc.getResourceManager().getResource(new ResourceLocation(LotusBlight.MODID, VIDEO));
        Thread worker = new Thread(() -> {
            // The video first: the exit starts cutting it into frames as soon as it's there.
            if (video.isPresent()) {
                try (InputStream in = video.get().open()) {
                    copy(in, video());
                } catch (IOException e) {
                    LOG.warn("Нормальная_ветка: can't unpack {}: {}", VIDEO, e.toString());
                }
            }
            for (int i = 0; i < ogg.length; i++) {
                if (ogg[i] == null) continue;
                try {
                    writeWav(ogg[i], song(i + 1));
                } catch (Exception e) {
                    LOG.warn("Нормальная_ветка: can't decode {}: {}", SOURCES[i], e.toString());
                }
            }
        }, "LotusBlight tracks");
        worker.setDaemon(true);
        worker.start();
    }

    /** Writes a stream to a file, under a temporary name until it's complete. */
    private static void copy(InputStream in, Path out) throws IOException {
        Files.createDirectories(out.getParent());
        Path part = out.resolveSibling(out.getFileName() + ".part");
        Files.copy(in, part, StandardCopyOption.REPLACE_EXISTING);
        Files.move(part, out, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }

    private static void writeWav(byte[] ogg, Path out) throws IOException {
        Files.createDirectories(out.getParent());
        Files.deleteIfExists(out);
        Path part = out.resolveSibling(out.getFileName() + ".part");
        ByteBuffer data = MemoryUtil.memAlloc(ogg.length);
        try (MemoryStack stack = MemoryStack.stackPush()) {
            data.put(ogg).flip();
            IntBuffer channels = stack.mallocInt(1), rate = stack.mallocInt(1);
            ShortBuffer pcm = STBVorbis.stb_vorbis_decode_memory(data, channels, rate);
            if (pcm == null) throw new IOException("not a readable Ogg Vorbis stream");
            try {
                ByteBuffer samples = MemoryUtil.memByteBuffer(pcm).order(ByteOrder.LITTLE_ENDIAN);
                try (FileChannel file = FileChannel.open(part, StandardOpenOption.CREATE,
                        StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE)) {
                    file.write(header(channels.get(0), rate.get(0), samples.remaining()));
                    while (samples.hasRemaining()) file.write(samples);
                }
            } finally {
                LibCStdlib.free(pcm);
            }
        } finally {
            MemoryUtil.memFree(data);
        }
        Files.move(part, out, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }

    /** A 44-byte header for 16-bit PCM. */
    private static ByteBuffer header(int channels, int rate, int dataBytes) {
        ByteBuffer h = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN);
        h.put(new byte[]{'R', 'I', 'F', 'F'}).putInt(36 + dataBytes).put(new byte[]{'W', 'A', 'V', 'E'});
        h.put(new byte[]{'f', 'm', 't', ' '}).putInt(16).putShort((short) 1).putShort((short) channels)
                .putInt(rate).putInt(rate * channels * 2).putShort((short) (channels * 2)).putShort((short) 16);
        h.put(new byte[]{'d', 'a', 't', 'a'}).putInt(dataBytes);
        return h.flip();
    }
}
