package danielf;

import org.jf.dexlib2.DexFileFactory;
import org.jf.dexlib2.dexbacked.DexBackedDexFile;
import org.jf.dexlib2.iface.MultiDexContainer;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;

public class Main {
    public static void main(String[] args) {
        try {
            MultiDexContainer<? extends DexBackedDexFile> multiDexContainer = DexFileFactory.loadDexContainer(new File(args[0]), null);
            var protoverse = new Protoverse(multiDexContainer);
            protoverse.dump(System.out);
        } catch (IOException e) {
            System.out.println(e);
            e.printStackTrace();
        }
    }
}