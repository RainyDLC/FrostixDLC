package mod.runtime;

import java.io.IOException;










public interface IClassBytecodeProvider {











    byte[] getClassBytes(String className, boolean runTransformers) throws IOException;
}
