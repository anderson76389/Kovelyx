import java.awt.*;
import java.io.*;
import java.lang.reflect.*;
import java.nio.file.*;
import javax.imageio.ImageIO;
import javax.swing.SwingUtilities;
import com.sun.net.httpserver.HttpServer;
import java.net.*;
import java.util.concurrent.atomic.AtomicInteger;

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
        HttpServer server = null;
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
            type.getMethod("dispose", long.class).invoke(bridge, handle);
            handle = 0;

            // Online sources also require forwarding provider headers and resuming
            // a loadfile request. Use only a private loopback fixture, never real tokens.
            byte[] video = Files.readAllBytes(Path.of(URI.create(args[0])));
            AtomicInteger served = new AtomicInteger();
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/video.mp4", exchange -> {
                try {
                    if (!"ok".equals(exchange.getRequestHeaders().getFirst("X-Kovelyx-Smoke"))) {
                        exchange.sendResponseHeaders(403, -1);
                        return;
                    }
                    int start = 0;
                    int end = video.length - 1;
                    int status = 200;
                    String range = exchange.getRequestHeaders().getFirst("Range");
                    if (range != null && range.matches("bytes=\\d+-\\d*")) {
                        String[] pieces = range.substring(6).split("-", -1);
                        start = Integer.parseInt(pieces[0]);
                        if (!pieces[1].isEmpty()) end = Math.min(end, Integer.parseInt(pieces[1]));
                        if (start > end) {
                            exchange.sendResponseHeaders(416, -1);
                            return;
                        }
                        status = 206;
                        exchange.getResponseHeaders().set("Content-Range", "bytes " + start + "-" + end + "/" + video.length);
                    }
                    exchange.getResponseHeaders().set("Content-Type", "video/mp4");
                    exchange.getResponseHeaders().set("Accept-Ranges", "bytes");
                    exchange.sendResponseHeaders(status, end - start + 1);
                    exchange.getResponseBody().write(video, start, end - start + 1);
                    served.incrementAndGet();
                } finally { exchange.close(); }
            });
            server.start();
            String url = "http://127.0.0.1:" + server.getAddress().getPort() + "/video.mp4";
            handle = ((Number) type.getMethod("create", long.class, String.class, String[].class,
                boolean.class, long.class, String.class, int.class, boolean.class, sinkType)
                .invoke(bridge, hwnd, url, new String[]{"X-Kovelyx-Smoke: ok"}, true, 500L, controls, 1, false, sink)).longValue();
            deadline = System.nanoTime() + 30_000_000_000L;
            position = 0;
            duration = 0;
            while (System.nanoTime() < deadline) {
                position = ((Number) type.getMethod("positionMs", long.class).invoke(bridge, handle)).longValue();
                duration = ((Number) type.getMethod("durationMs", long.class).invoke(bridge, handle)).longValue();
                if (position >= 1000 && duration >= 1800) break;
                Thread.sleep(100);
            }
            if (served.get() == 0 || position < 1000 || duration < 1800) {
                throw new AssertionError("HTTP video/headers/resume failed: requests=" + served.get() + ", position=" + position + ", duration=" + duration);
            }
            System.out.println("PASS: HTTP MP4, provider header, resume and second player creation, position=" + position);
        } catch (InvocationTargetException failure) {
            throw new RuntimeException("Packaged native player failed", failure.getCause());
        } finally {
            if (handle != 0) type.getMethod("dispose", long.class).invoke(bridge, handle);
            if (server != null) server.stop(0);
            SwingUtilities.invokeAndWait(frame::dispose);
        }
    }
}
