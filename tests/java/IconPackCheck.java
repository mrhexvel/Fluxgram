import org.telegram.messenger.fluxgram.icons.IconPackArchiveValidator;
import org.telegram.messenger.fluxgram.icons.IconPackManifest;
import org.telegram.messenger.fluxgram.icons.IconPackResolver;
import org.telegram.messenger.fluxgram.icons.IconPackStore;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.imageio.ImageIO;

public final class IconPackCheck {
    public static void main(String[] args) throws Exception {
        String command = args[0];

        if (command.equals("inspect")) {
            IconPackArchiveValidator.Result result = IconPackArchiveValidator.inspect(
                    new File(args[1]),
                    new File(args[2]),
                    catalog(args[3]));

            System.out.println(result.manifest.id + "\t" + result.manifest.name + "\t" + result.sha256);
        } else if (command.equals("install")) {
            IconPackStore store = store(args);
            IconPackStore.Installed installed = store.install(new File(args[4]), args[5]);

            System.out.println(installed.manifest.id + "\t" + installed.manifest.version);
        } else if (command.equals("list")) {
            IconPackStore store = store(args);
            ArrayList<String> ids = new ArrayList<>();

            for (IconPackStore.Installed installed : store.list()) {
                ids.add(installed.manifest.id + ":" + installed.manifest.version);
            }

            System.out.println(String.join(",", ids));
        } else if (command.equals("resolve")) {
            IconPackStore store = store(args);
            List<String> active = args[4].isEmpty()
                    ? Collections.emptyList()
                    : Arrays.asList(args[4].split(","));
            IconPackResolver resolver = new IconPackResolver(store, store.list(), active);
            byte[] icon = resolver.resolve(args[5]);

            System.out.println(String.join(",", resolver.activeIds()));
            System.out.println(icon == null
                    ? "NONE"
                    : new String(icon, StandardCharsets.UTF_8));
        } else if (command.equals("export")) {
            IconPackStore store = store(args);
            File exported = store.export(args[4], new File(args[5]));

            System.out.println(exported.length());
        } else if (command.equals("delete")) {
            IconPackStore store = store(args);
            store.delete(args[4]);

            System.out.println(store.list().size());
        } else if (command.equals("stale-update")) {
            IconPackStore store = store(args);
            IconPackResolver resolver = new IconPackResolver(
                    store,
                    store.list(),
                    Collections.singletonList(args[4]));

            store.install(new File(args[6]), args[7]);

            byte[] icon = resolver.resolve(args[5]);

            System.out.println(icon == null
                    ? "NONE"
                    : new String(icon, StandardCharsets.UTF_8));
        } else if (command.equals("stale-reinstall")) {
            IconPackStore store = store(args);
            IconPackResolver resolver = new IconPackResolver(
                    store,
                    store.list(),
                    Collections.singletonList(args[4]));

            store.delete(args[4]);
            store.install(new File(args[6]), args[7]);

            byte[] icon = resolver.resolve(args[5]);

            System.out.println(icon == null ? "NONE" : icon.length);
        } else if (command.equals("invalid-manifest")) {
            try {
                IconPackManifest.parse(args[1]);
                throw new AssertionError("Invalid manifest was accepted");
            } catch (IOException expected) {
                System.out.println("IO");
            }
        } else if (command.equals("cross-store")) {
            IconPackStore first = store(args);
            IconPackStore.Installed installed = first.get(args[4]);
            IconPackStore second = new IconPackStore(
                    new File(args[6]),
                    new File(args[7]),
                    catalog(args[3]));
            byte[] icon = second.resolve(installed, args[5]);

            System.out.println(icon == null ? "NONE" : icon.length);
        } else {
            throw new IllegalArgumentException(command);
        }
    }

    private static IconPackStore store(String[] args) throws Exception {
        return new IconPackStore(new File(args[1]), new File(args[2]), catalog(args[3]));
    }

    private static IconPackArchiveValidator.Catalog catalog(String value) {
        Set<String> allowed = new HashSet<>(Arrays.asList(value.split(",")));

        return new IconPackArchiveValidator.Catalog() {
            @Override
            public boolean contains(String resourceName) {
                return allowed.contains(resourceName);
            }

            @Override
            public boolean decodesRaster(byte[] bytes, String path, int width, int height) {
                try {
                    BufferedImage image = ImageIO.read(new ByteArrayInputStream(bytes));

                    return image != null
                            && image.getWidth() == width
                            && image.getHeight() == height;
                } catch (IOException ignored) {
                    return false;
                }
            }
        };
    }
}
