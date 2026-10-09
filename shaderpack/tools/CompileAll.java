import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;

import java.io.File;
import java.nio.file.Files;

/** Compiles every .vsh/.fsh in a shaders folder in a hidden context and prints the driver's verdict. */
public class CompileAll {
    public static void main(String[] a) throws Exception {
        if (!GLFW.glfwInit()) throw new IllegalStateException("glfw");
        GLFW.glfwWindowHint(GLFW.GLFW_VISIBLE, GLFW.GLFW_FALSE);
        long win = GLFW.glfwCreateWindow(64, 64, "compile", 0, 0);
        GLFW.glfwMakeContextCurrent(win);
        GL.createCapabilities();
        int bad = 0;
        File[] files = new File(a[0]).listFiles((d, n) -> n.endsWith(".vsh") || n.endsWith(".fsh"));
        java.util.Arrays.sort(files);
        for (File f : files) {
            int sh = GL20.glCreateShader(f.getName().endsWith(".vsh") ? GL20.GL_VERTEX_SHADER : GL20.GL_FRAGMENT_SHADER);
            GL20.glShaderSource(sh, new String(Files.readAllBytes(f.toPath())));
            GL20.glCompileShader(sh);
            boolean ok = GL20.glGetShaderi(sh, GL20.GL_COMPILE_STATUS) == GL11.GL_TRUE;
            String log = GL20.glGetShaderInfoLog(sh).trim();
            System.out.println(f.getName() + ": " + (ok ? "OK" : "ERROR") + (log.isEmpty() ? "" : "\n" + log));
            if (!ok) bad++;
        }
        System.out.println(bad == 0 ? "ALL OK" : bad + " FAILED");
        GLFW.glfwDestroyWindow(win);
        GLFW.glfwTerminate();
    }
}
