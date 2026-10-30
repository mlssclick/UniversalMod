package universalmod.render;

import java.util.HashSet;
import java.util.Set;

public final class ShaderDiagnostics {

    private static final Set<String> REPORTED = new HashSet<>();

    private ShaderDiagnostics() {
    }

    public static void missing(String name) {
        report(name, "program failed to load");
    }

    public static void invalid(String name, String reason) {
        report(name, reason == null ? "invalid shader program" : reason);
    }

    public static synchronized void failure(String name, Throwable throwable) {
        String reason = throwable == null ? "shader load failed" : throwable.getClass().getSimpleName() + ": " + String.valueOf(throwable.getMessage());
        String key = name + ':' + reason;
        if (REPORTED.add(key)) {
            ;
        }
    }

    private static synchronized void report(String name, String reason) {
        String key = name + ':' + reason;
        if (REPORTED.add(key)) {
            ;
        }
    }
}
