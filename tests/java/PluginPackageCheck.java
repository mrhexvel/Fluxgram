import org.telegram.messenger.plugins.packages.PluginPackageValidator;

import java.io.File;
import java.io.IOException;

public final class PluginPackageCheck {
    public static void main(String[] args) throws Exception {
        try {
            PluginPackageValidator.Result result =
                    PluginPackageValidator.inspect(new File(args[0]), new File(args[1]));
            System.out.println(result.manifest.id);
            System.out.println(result.sha256);
            System.out.println(result.fileCount);
            System.out.println(result.unpackedBytes);
        } catch (IOException expected) {
            System.err.println(expected.getMessage());
            System.exit(2);
        }
    }
}
