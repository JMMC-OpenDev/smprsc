/*******************************************************************************
 * JMMC project ( http://www.jmmc.fr ) - Copyright (C) CNRS.
 ******************************************************************************/
package fr.jmmc.smprun.preference;

/**
 * Enumeration of all AppLauncher preference keys.
 * @author Sylvain LAFRASSE
 */
public enum PreferenceKey {

    FIRST_START_FLAG("first.start.flag"),
    SHOW_DOCK_WINDOW("show.dock.window"),
    START_SELECTED_STUBS("start.selected.stubs"),
    SHOW_EXIT_WARNING("show.exit.warning"),
    SILENTLY_REPORT_FLAG("silently.report.flag"),
    APPLICATION_CLI_PATH_PREFIX("command.line.path.for."),
    SELECTED_APPLICATION_LIST("selected.application.list"),
    BETA_APPLICATION_LIST("beta.application.list");
    /** the preferenced value identifying token */
    private final String _key;

    /**
     * Constructor
     * @param key the preferenced value identifying token
     */
    PreferenceKey(String key) {
        _key = key;
    }

    /**
     * @return the preferenced value identifying token
     */
    @Override
    public String toString() {
        return _key;
    }

    /**
     * For unit testing purpose only.
     * @param args
     */
    public static void main(String[] args) {
        for (PreferenceKey k : PreferenceKey.values()) {
            System.out.println("Key '" + k.name() + "' = ['" + k + "'].");
        }
    }
}
