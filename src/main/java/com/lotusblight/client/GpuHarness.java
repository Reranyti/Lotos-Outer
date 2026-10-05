package com.lotusblight.client;

import com.lotusblight.cinema.GpuScene;

import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11;

import java.awt.image.BufferedImage;
import java.io.File;

/**
 * A stand of its own for the graphics-card renderer: opens a hidden OpenGL window, draws frames of a scene and saves them as pictures, so
 * the picture can be looked at without starting the game. {@code GpuHarness <folder> <skin.png|-> <beats.json|-> <width> <height> <seconds...>}.
 * (Not used by the mod itself.)
 */
public final class GpuHarness {
    public static void main(String[] args) throws Exception {
        System.setProperty("java.awt.headless", "true");
        File dir = new File(args[0]);
        dir.mkdirs();
        int[] skinPx = null;
        if (!args[1].equals("-")) {
            BufferedImage s = javax.imageio.ImageIO.read(new File(args[1]));
            skinPx = new int[64 * 64];
            s.getRGB(0, 0, 64, 64, skinPx, 0, 64);
        }
        int w = Integer.parseInt(args[3]), h = Integer.parseInt(args[4]);
        if (!GLFW.glfwInit()) throw new IllegalStateException("no GLFW");
        GLFW.glfwDefaultWindowHints();
        GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE, GLFW.GLFW_FALSE);
        GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MAJOR, 3);
        GLFW.glfwWindowHint(GLFW.GLFW_CONTEXT_VERSION_MINOR, 2);
        GLFW.glfwWindowHint(GLFW.GLFW_OPENGL_PROFILE, GLFW.GLFW_OPENGL_CORE_PROFILE);
        GLFW.glfwWindowHint(GLFW.GLFW_OPENGL_FORWARD_COMPAT, GLFW.GLFW_TRUE);
        long win = GLFW.glfwCreateWindow(64, 64, "stand", 0, 0);
        if (win == 0) throw new IllegalStateException("no window");
        GLFW.glfwMakeContextCurrent(win);
        GL.createCapabilities();
        System.out.println("GL " + GL11.glGetString(GL11.GL_VERSION) + " on " + GL11.glGetString(GL11.GL_RENDERER));
        GpuScene gpu = new GpuScene(w, h);
        Ending13Scene scene = skinPx == null ? new Ending13Scene() : new Ending13Scene(skinPx);
        if (!args[2].equals("-")) {
            String json = new String(java.nio.file.Files.readAllBytes(new File(args[2]).toPath()), java.nio.charset.StandardCharsets.UTF_8);
            int a = json.indexOf("\"kicks\": [") + 10;
            int b = json.indexOf(']', a);
            String[] parts = json.substring(a, b).split(",");
            double[] ks = new double[parts.length];
            for (int i = 0; i < parts.length; i++) ks[i] = Double.parseDouble(parts[i].trim());
            scene.setKicks(ks);
        }
        gpu.draw(scene.collectGpu(0, w, h));                       // a first frame to warm up
        for (int i = 5; i < args.length; i++) {
            double t = Double.parseDouble(args[i]);
            long t0 = System.nanoTime();
            GpuScene.Frame fr = scene.collectGpu(t - Ending13Scene.T0, w, h);
            long tc = System.nanoTime();
            gpu.draw(fr);
            GL11.glFinish();
            long ms = (System.nanoTime() - t0) / 1_000_000;
            int[] px = gpu.readPixels();
            BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
            out.setRGB(0, 0, w, h, px, 0, w);
            javax.imageio.ImageIO.write(out, "png", new File(dir, String.format(java.util.Locale.ROOT, "g%06.2f.png", t)));
            System.out.println("t=" + t + "  total " + ms + " ms, of which drawing " + (System.nanoTime() - tc) / 1_000_000 + " ms");
        }
        GLFW.glfwDestroyWindow(win);
        GLFW.glfwTerminate();
    }
}
