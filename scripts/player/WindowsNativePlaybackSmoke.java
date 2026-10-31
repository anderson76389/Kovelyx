import java.awt.*;
import java.io.*;
import java.lang.reflect.*;
import java.nio.file.*;
import javax.imageio.ImageIO;
import javax.swing.SwingUtilities;

/** Exercises the bridge and DLLs from the shipped application, without provider credentials. */
public final class WindowsNativePlaybackSmoke {
    public static void main(String[] args) throws Exception {
        Frame frame = new Frame("Kovelyx native playback verification");
        Canvas canvas = new Canvas();
        SwingUtilities.invokeAndWait(() -> {
            frame.add(canvas);
            frame.setSize(640, 400);
            frame.setVisible(true);
        });
        Object bridge = null;
        Class<?> type = null;
        long handle = 0;
        try {
            Field peerField = Component.class.getDeclaredField("peer");
            peerField.setAccessible(true);
            Object peer = peerField.get(canvas);
            Method hwndMethod = null;
            for (Class<?> c = peer.getClass(); c != null && hwndMethod == null; c = c.getSuperclass()) {
                try { hwndMethod = c.getDeclaredMethod("getHWnd"); } catch (NoSuchMethodException ignored) {}
            }
            if (hwndMethod == null) throw new AssertionError("Windows HWND unavailable");
            hwndMethod.setAccessible(true);
            long hwnd = ((Number) hwndMethod.invoke(peer)).longValue();
            type = Class.forName("com.nuvio.app.features.player.desktop.NativePlayerBridge");
            bridge = type.getField("INSTANCE").get(null);
            Class<?> sinkType = Class.forName("com.nuvio.app.features.player.desktop.NativePlayerEventSink");
            Object sink = Proxy.newProxyInstance(sinkType.getClassLoader(), new Class<?>[]{sinkType},
                (proxy, method, values) -> null);
            String controls = (String) type.getMethod("getControlsPageUrl").invoke(bridge);
            System.out.println("Native library and control assets loaded");
            handle = ((Number) type.getMethod("create", long.class, String.class, String[].class,
                boolean.class, long.class, String.class, int.class, boolean.class, sinkType)
                .invoke(bridge, hwnd, args[0], new String[0], true, 0L, controls, 1, false, sink)).longValue();
            if (handle == 0) throw new AssertionError("No native player handle");
            long deadline = System.nanoTime() + 30_000_000_000L;
            long position = 0;
            long duration = 0;
            while (System.nanoTime() < deadline) {
                position = ((Number) type.getMethod("positionMs", long.class).invoke(bridge, handle)).longValue();
                duration = ((Number) type.getMethod("durationMs", long.class).invoke(bridge, handle)).longValue();
                if (position >= 800 && duration >= 1800) break;
                Thread.sleep(100);
            }
            ImageIO.write(new Robot().createScreenCapture(frame.getBounds()), "png", new File(args[1]));
            if (position < 800 || duration < 1800) {
                throw new AssertionError("Video never progressed: position=" + position + ", duration=" + duration);
            }
            System.out.println("PASS: native MP4 playback, position=" + position + ", duration=" + duration);
        } catch (InvocationTargetException failure) {
            throw new RuntimeException("Packaged native player failed", failure.getCause());
        } finally {
            if (handle != 0) type.getMethod("dispose", long.class).invoke(bridge, handle);
            SwingUtilities.invokeAndWait(frame::dispose);
        }
    }
}
