import org.telegram.messenger.plugins.packages.PluginPackageStore;

import java.io.File;
import java.io.IOException;

public final class PluginStoreCheck {
    public static void main(String[] args) throws Exception {
        try {
            PluginPackageStore store = new PluginPackageStore(new File(args[1]), new File(args[2]));

            if (args[0].equals("install")) {
                store.install(new File(args[3]), args[4]);
            }

            if (args[0].equals("update")) {
                store.scheduleUpdate(
                        new File(args[3]),
                        args[4],
                        args[5],
                        args[6],
                        Boolean.parseBoolean(args[7]));
            }

            if (args[0].equals("remove")) {
                store.remove(args[3]);
            }

            if (args[0].equals("delete")) {
                store.scheduleDelete(args[3]);
            }

            if (args[0].equals("apply")) {
                store.applyPending();
            }

            if (args[0].equals("export")) {
                System.out.println(store.export(args[3], new File(args[4])).getAbsolutePath());
                return;
            }

            if (args[0].equals("pending")) {
                for (PluginPackageStore.Pending operation : store.pending().values()) {
                    System.out.println(
                            operation.id
                                    + ":"
                                    + operation.delete
                                    + ":"
                                    + operation.enableAfterUpdate);
                }

                return;
            }

            for (PluginPackageStore.Installed item : store.list()) {
                if (args[0].equals("listcompat")) {
                    System.out.println(
                            item.manifest.id
                                    + ":"
                                    + item.manifest.isCompatible()
                                    + ":"
                                    + item.manifest.sdkVersion
                                    + ":"
                                    + item.manifest.minSdkMinor);
                } else {
                    System.out.println(item.manifest.id);
                }
            }
        } catch (IOException error) {
            System.err.println(error.getMessage());
            System.exit(2);
        }
    }
}
