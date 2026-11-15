package dopajshell;

public final class Version {
    private Version() {}

    /** From the jar manifest (set from `version` in build.gradle); "dev" when run from class files. */
    public static final String VERSION = resolve();

    private static String resolve() {
        String v = Version.class.getPackage().getImplementationVersion();
        return v != null ? v : "dev";
    }
}
