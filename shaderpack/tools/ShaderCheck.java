import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL20;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Paths;

/** Offscreen check of the pack's final pass: compiles it and runs it over a screenshot. */
public class ShaderCheck {
    public static void main(String[] a) throws Exception {
        String dir = a[0], in = a[1], out = a[2];
        BufferedImage src = ImageIO.read(new File(in));
        int w = src.getWidth(), h = src.getHeight();
        if (!GLFW.glfwInit()) throw new IllegalStateException("glfw");
        GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE, GLFW.GLFW_FALSE);
        long win = GLFW.glfwCreateWindow(w, h, "check", 0, 0);
        GLFW.glfwMakeContextCurrent(win);
        GL.createCapabilities();

        int tex = GL11.glGenTextures();
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, tex);
        ByteBuffer px = ByteBuffer.allocateDirect(w * h * 4).order(ByteOrder.nativeOrder());
        for (int y = h - 1; y >= 0; y--) for (int x = 0; x < w; x++) {
            int c = src.getRGB(x, y);
            px.put((byte) (c >> 16)).put((byte) (c >> 8)).put((byte) c).put((byte) 255);
        }
        px.flip();
        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, w, h, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, px);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);

        int prog = GL20.glCreateProgram();
        for (String[] s : new String[][]{{"final.vsh", "v"}, {"final.fsh", "f"}}) {
            int sh = GL20.glCreateShader(s[1].equals("v") ? GL20.GL_VERTEX_SHADER : GL20.GL_FRAGMENT_SHADER);
            GL20.glShaderSource(sh, new String(Files.readAllBytes(Paths.get(dir, s[0]))));
            GL20.glCompileShader(sh);
            String log = GL20.glGetShaderInfoLog(sh);
            boolean ok = GL20.glGetShaderi(sh, GL20.GL_COMPILE_STATUS) == GL11.GL_TRUE;
            System.out.println(s[0] + ": " + (ok ? "OK" : "COMPILE ERROR") + (log.isBlank() ? "" : "\n" + log));
            if (!ok) System.exit(2);
            GL20.glAttachShader(prog, sh);
        }
        GL20.glLinkProgram(prog);
        if (GL20.glGetProgrami(prog, GL20.GL_LINK_STATUS) != GL11.GL_TRUE) {
            System.out.println("LINK ERROR\n" + GL20.glGetProgramInfoLog(prog));
            System.exit(3);
        }
        GL20.glUseProgram(prog);
        GL20.glUniform1i(GL20.glGetUniformLocation(prog, "colortex0"), 0);
        GL20.glUniform1f(GL20.glGetUniformLocation(prog, "viewWidth"), w);
        GL20.glUniform1f(GL20.glGetUniformLocation(prog, "viewHeight"), h);
        GL20.glUniform1f(GL20.glGetUniformLocation(prog, "frameTimeCounter"), 1.25f);

        GL11.glViewport(0, 0, w, h);
        GL11.glMatrixMode(GL11.GL_PROJECTION);
        GL11.glLoadIdentity();
        GL11.glOrtho(0, 1, 0, 1, -1, 1);
        GL11.glMatrixMode(GL11.GL_MODELVIEW);
        GL11.glLoadIdentity();
        GL13.glActiveTexture(GL13.GL_TEXTURE0);
        GL11.glBegin(GL11.GL_QUADS);
        GL11.glTexCoord2f(0, 0); GL11.glVertex2f(0, 0);
        GL11.glTexCoord2f(1, 0); GL11.glVertex2f(1, 0);
        GL11.glTexCoord2f(1, 1); GL11.glVertex2f(1, 1);
        GL11.glTexCoord2f(0, 1); GL11.glVertex2f(0, 1);
        GL11.glEnd();

        ByteBuffer rd = ByteBuffer.allocateDirect(w * h * 4).order(ByteOrder.nativeOrder());
        GL11.glReadPixels(0, 0, w, h, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, rd);
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
            int i = ((h - 1 - y) * w + x) * 4;
            img.setRGB(x, y, ((rd.get(i) & 255) << 16) | ((rd.get(i + 1) & 255) << 8) | (rd.get(i + 2) & 255));
        }
        ImageIO.write(img, "png", new File(out));
        System.out.println("rendered " + w + "x" + h + " -> " + out);
        GLFW.glfwDestroyWindow(win);
        GLFW.glfwTerminate();
    }
}
