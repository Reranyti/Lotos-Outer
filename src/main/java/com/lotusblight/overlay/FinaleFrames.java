package com.lotusblight.overlay;

import org.jcodec.api.FrameGrab;
import org.jcodec.api.PictureWithMetadata;
import org.jcodec.common.DemuxerTrackMeta;
import org.jcodec.common.io.NIOUtils;
import org.jcodec.common.io.SeekableByteChannel;
import org.jcodec.scale.AWTUtil;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

/**
 * Turns the finale video into the numbered JPEG frames VideoScene plays (f_0001.jpg, ...), at the
 * video's own resolution, with JCodec - the same result ffmpeg gives, without needing ffmpeg. Each
 * frame appears only once fully written, so the player can start while the rest are still coming; they
 * can turn up a few out of order (the decoder's own order), and the player simply waits for the next.
 * Runs well ahead of when the frames are needed (the scene and the first song come first).
 */
final class FinaleFrames {
    /** JPEG quality: visually the same as the video frame. */
    private static final float QUALITY = 0.95f;
    private static final String DONE = "complete";

    private final File video;
    private final File dir;
    private volatile double fps = 30;
    private volatile int total;

    FinaleFrames(File video, File dir) {
        this.video = video;
        this.dir = dir;
    }

    /** Frames per second of the video, once it has been opened; 30 until then. */
    double fps() {
        return fps;
    }

    static String name(int index) {
        return String.format("f_%04d.jpg", index);
    }

    /**
     * Writes every frame into dir. Waits up to waitMs for the video file to appear first. Skips the
     * work if dir already holds a complete set cut from this same file.
     */
    void run(long waitMs) throws Exception {
        long until = System.currentTimeMillis() + waitMs;
        while (!video.isFile() && System.currentTimeMillis() < until) Thread.sleep(200);
        if (!video.isFile()) throw new IOException("no video at " + video);
        Files.createDirectories(dir.toPath());
        String stamp = video.length() + "|" + video.lastModified();
        File done = new File(dir, DONE);

        try (SeekableByteChannel ch = NIOUtils.readableChannel(video)) {
            FrameGrab grab = FrameGrab.createFrameGrab(ch);
            DemuxerTrackMeta meta = grab.getVideoTrack().getMeta();
            if (meta != null && meta.getTotalDuration() > 0 && meta.getTotalFrames() > 0) {
                total = meta.getTotalFrames();
                fps = total / meta.getTotalDuration();
            }
            if (done.isFile() && Files.readString(done.toPath()).equals(stamp)) return;
            Files.deleteIfExists(done.toPath());

            ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
            ImageWriteParam param = writer.getDefaultWriteParam();
            param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            param.setCompressionQuality(QUALITY);
            try {
                int last = 0;
                PictureWithMetadata frame;
                while ((frame = grab.getNativeFrameWithMetadata()) != null) {
                    // Frames come out in decoding order, not showing order (B-frames), so each one is
                    // filed under the number its own timestamp gives it.
                    int index = (int) Math.round(frame.getTimestamp() * fps) + 1;
                    last = Math.max(last, index);
                    BufferedImage image = AWTUtil.toBufferedImage(frame.getPicture());
                    File part = new File(dir, name(index) + ".part");
                    try (ImageOutputStream out = ImageIO.createImageOutputStream(part)) {
                        writer.setOutput(out);
                        writer.write(null, new IIOImage(image, null, null), param);
                    }
                    Files.move(part.toPath(), new File(dir, name(index)).toPath(),
                            StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                }
                total = last;
            } finally {
                writer.dispose();
            }
            Files.writeString(done.toPath(), stamp);
        }
    }

    /** Removes exactly the frames this wrote (and its marker), leaving the folder itself. */
    void clean() {
        int last = Math.max(total, 1);
        for (int i = 1; i <= last + 1; i++) {
            new File(dir, name(i)).delete();
            new File(dir, name(i) + ".part").delete();
        }
        new File(dir, DONE).delete();
    }
}
