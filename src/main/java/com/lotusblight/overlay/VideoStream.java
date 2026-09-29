package com.lotusblight.overlay;

import org.jcodec.api.FrameGrab;
import org.jcodec.api.PictureWithMetadata;
import org.jcodec.common.DemuxerTrackMeta;
import org.jcodec.common.io.ByteBufferSeekableByteChannel;
import org.jcodec.common.io.SeekableByteChannel;
import org.jcodec.common.model.Picture;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Plays the finale video straight from the .mp4 bytes, decoded in memory as it goes - nothing is
 * written to disk. One decoder alone can't keep up with 1080p at full rate, so several work side by side, each on
 * its own stretch between key frames, and only a few seconds of pictures are ever held ahead of the one
 * on screen, kept as the decoder gives them (YUV, half the size of RGB); only the picture actually shown
 * is turned into colour, and the ones already shown are let go at once. Decoding starts as soon as this
 * is made, so the first seconds are ready long before they are needed. If a picture isn't ready in time
 * the previous one stays up and playback keeps to the clock. HD video is read with the BT.709 colours
 * players assume for it.
 */
final class VideoStream implements FinaleVideo {
    /** Most of the heap stays free: pictures may use a third of it, within these bounds (in frames). */
    private static final int MIN_WINDOW = 24, MAX_WINDOW = 120;
    /** A stretch never runs this many pictures past the next key frame looking for its own. */
    private static final int OVERRUN = 32;

    /** The whole .mp4, held in memory - each decoder reads it through its own view. */
    private final byte[] data;
    private final double fps;
    private final int total;
    /** Key frames, as sample numbers in decoding order - each starts a stretch one worker decodes. */
    private final int[] keys;
    private final int window;
    private final Map<Integer, Picture> ready = new ConcurrentHashMap<>();
    private final AtomicInteger nextStretch = new AtomicInteger();
    private final Object tick = new Object();
    private volatile int playhead;
    private volatile boolean failed;
    private BufferedImage shown;
    private int shownIndex = -1;

    VideoStream(byte[] data) throws Exception {
        this.data = data;
        int w, h;
        try (SeekableByteChannel ch = channel()) {
            DemuxerTrackMeta meta = FrameGrab.createFrameGrab(ch).getVideoTrack().getMeta();
            total = meta.getTotalFrames();
            fps = total / meta.getTotalDuration();
            int[] seek = meta.getSeekFrames();
            int[] k = seek == null || seek.length == 0 ? new int[]{0} : seek.clone();
            Arrays.sort(k);
            if (k[0] != 0) {
                int[] withStart = new int[k.length + 1];
                System.arraycopy(k, 0, withStart, 1, k.length);
                k = withStart;
            }
            keys = k;
            w = meta.getVideoCodecMeta().getSize().getWidth();
            h = meta.getVideoCodecMeta().getSize().getHeight();
        }
        long frameBytes = (long) w * h * 3 / 2;
        window = (int) Math.max(MIN_WINDOW, Math.min(MAX_WINDOW, Runtime.getRuntime().maxMemory() / 3 / frameBytes));
        int workers = Math.max(1, Math.min(6, Runtime.getRuntime().availableProcessors() - 1));
        for (int i = 0; i < workers; i++) {
            Thread t = new Thread(this::work, "finale video " + i);
            t.setDaemon(true);
            t.start();
        }
    }

    double fps() {
        return fps;
    }

    @Override
    public boolean ready() {
        return !failed || shown != null;
    }

    @Override
    public void render(Graphics2D g, int w, int h, double timeSec) {
        int want = Math.min(total - 1, (int) Math.floor(timeSec * fps));
        if (want != playhead) {
            playhead = want;
            ready.keySet().removeIf(i -> i < want);
            synchronized (tick) {
                tick.notifyAll();
            }
        }
        if (want != shownIndex) {
            Picture picture = ready.get(want);
            if (picture != null) {
                if (shown == null || shown.getWidth() != picture.getWidth() || shown.getHeight() != picture.getHeight()) {
                    shown = new BufferedImage(picture.getWidth(), picture.getHeight(), BufferedImage.TYPE_INT_RGB);
                }
                toRgb(picture, ((DataBufferInt) shown.getRaster().getDataBuffer()).getData());
                shownIndex = want;
            }
        }
        if (shownIndex < 0) return;
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        double s = Math.max((double) w / shown.getWidth(), (double) h / shown.getHeight());
        int dw = (int) (shown.getWidth() * s), dh = (int) (shown.getHeight() * s);
        g.drawImage(shown, (w - dw) / 2, (h - dh) / 2, dw, dh, null);
    }

    private SeekableByteChannel channel() {
        return ByteBufferSeekableByteChannel.readFromByteBuffer(ByteBuffer.wrap(data));
    }

    /** One decoder: takes the next stretch between key frames, decodes it, repeats. */
    private void work() {
        try (SeekableByteChannel ch = channel()) {
            FrameGrab grab = FrameGrab.createFrameGrab(ch);
            int n;
            while ((n = nextStretch.getAndIncrement()) < keys.length) {
                decodeStretch(grab, n);
            }
        } catch (Throwable e) {
            failed = true;
            System.err.println("Finale video: " + e);
        }
    }

    /**
     * Decodes stretch n: from its key frame up to the next one. Pictures come out in decoding order;
     * each is placed by its own timestamp. Pictures shown before a key frame but decoded after it (the
     * leading pictures of an open stretch) lean on the stretch before, so they are left to that
     * stretch's worker, which keeps decoding just past the next key frame to pick them up.
     */
    private void decodeStretch(FrameGrab grab, int n) throws Exception {
        int start = keys[n];
        int end = n + 1 < keys.length ? keys[n + 1] : total;
        waitUntilNear(start);
        grab.seekToFrameSloppy(start);
        int own = Integer.MIN_VALUE;     // where this stretch's key frame is shown
        int next = Integer.MAX_VALUE;    // where the next key frame is shown, once reached
        for (int s = start; s < Math.min(total, end + OVERRUN); s++) {
            PictureWithMetadata frame = grab.getNativeFrameWithMetadata();
            if (frame == null) return;
            int index = (int) Math.round(frame.getTimestamp() * fps);
            if (s == start) own = index;
            if (s == end) {
                next = index;
                continue;
            }
            if (s > end && index > next) return;       // past the next stretch's leading pictures
            if (index < own || index >= next) continue; // not ours to show
            waitUntilNear(index);
            if (index < playhead) continue;             // too late to be seen
            // A copy: the decoder reuses its own buffers. Cropped to the real size (it pads to 16).
            ready.put(index, frame.getPicture().cloneCropped());
        }
    }

    /**
     * YUV 4:2:0 (JCodec keeps samples as value - 128) to packed RGB, limited range, BT.709 - fixed-point,
     * straight into the shown image's pixels.
     */
    private static void toRgb(Picture p, int[] out) {
        int w = p.getWidth(), h = p.getHeight();
        byte[] ys = p.getPlaneData(0), us = p.getPlaneData(1), vs = p.getPlaneData(2);
        int cw = p.getPlaneWidth(1);
        for (int y = 0; y < h; y++) {
            int row = y * w, crow = (y >> 1) * cw;
            for (int x = 0; x < w; x++) {
                int yy = 1192 * (ys[row + x] + 128 - 16);
                int u = us[crow + (x >> 1)], v = vs[crow + (x >> 1)];
                int r = (yy + 1836 * v) >> 10;
                int g = (yy - 218 * u - 546 * v) >> 10;
                int b = (yy + 2163 * u) >> 10;
                out[row + x] = clamp(r) << 16 | clamp(g) << 8 | clamp(b);
            }
        }
    }

    private static int clamp(int c) {
        return c < 0 ? 0 : Math.min(c, 255);
    }

    /** Holds a worker back while the picture it's about to make is further ahead than memory allows. */
    private void waitUntilNear(int index) throws InterruptedException {
        synchronized (tick) {
            while (index > playhead + window) tick.wait(100);
        }
    }
}
