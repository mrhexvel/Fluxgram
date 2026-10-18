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
                        Boolean.parseBoolean(args[7]),
                        args.length > 8 && Boolean.parseBoolean(args[8]));
            }

            if (args[0].equals("debugreload")) {
                store.scheduleDebugReload(
                        new File(args[3]),
                        args[4],
                        args[5],
                        args[6],
                        Boolean.parseBoolean(args[7]));
            }

            if (args[0].equals("cancel")) {
                store.cancelPendingUpdate(args[3]);
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

            if (args[0].equals("applyerrors")) {
                store.applyPending();

                for (java.util.Map.Entry<String, String> error
                        : store.pendingErrors().entrySet()) {
                    System.out.println(error.getKey() + ":" + error.getValue());
                }

                return;
            }

            if (args[0].equals("activate")) {
                store.activatePendingUpdate(args[3]);
            }

            if (args[0].equals("finish")) {
                store.finishPendingUpdate(args[3]);
            }

            if (args[0].equals("markmigrated")) {
                store.markPendingMigrated(args[3]);
            }

            if (args[0].equals("rollback")) {
                store.rollbackPending(args[3]);
                store.finishPendingRollback(args[3]);
            }

            if (args[0].equals("rollbackprepare")) {
                store.rollbackPending(args[3]);
            }

            if (args[0].equals("rollbackfinish")) {
                store.finishPendingRollback(args[3]);
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

            if (args[0].equals("pendingfull")) {
                for (PluginPackageStore.Pending operation : store.pending().values()) {
                    System.out.println(
                            operation.id
                                    + ":"
                                    + operation.enableAfterUpdate
                                    + ":"
                                    + operation.enabledBeforeUpdate
                                    + ":"
                                    + operation.rollback);
                }

                return;
            }

            if (args[0].equals("pendingerrors")) {
                store.pending();

                for (java.util.Map.Entry<String, String> error
                        : store.pendingErrors().entrySet()) {
                    System.out.println(error.getKey() + ":" + error.getValue());
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
