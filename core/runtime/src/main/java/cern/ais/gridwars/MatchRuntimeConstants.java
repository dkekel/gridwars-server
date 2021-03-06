package cern.ais.gridwars;

public final class MatchRuntimeConstants {

    public static final String BOT_1_JAR_PATH_SYS_PROP_KEY = "gridwars.runtime.bot1JarPath";
    public static final String BOT_2_JAR_PATH_SYS_PROP_KEY = "gridwars.runtime.bot2JarPath";
    public static final String BOT_1_CLASS_NAME_SYS_PROP_KEY = "gridwars.runtime.bot1ClassName";
    public static final String BOT_2_CLASS_NAME_SYS_PROP_KEY = "gridwars.runtime.bot2ClassName";
    public static final String MATCH_RUNTIME_DIR = "gridwars.runtime.dir";
    public static final String GRIDWARS_SECURITY_POLICY_FILE_NAME = "gridwars.policy";
    public static final String MATCH_RUNTIME_MAIN_CLASS_NAME = MatchRuntime.class.getName();

    private MatchRuntimeConstants() {
    }
}
