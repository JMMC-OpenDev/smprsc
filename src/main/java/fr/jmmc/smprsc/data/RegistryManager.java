/*******************************************************************************
 * JMMC project ( http://www.jmmc.fr ) - Copyright (C) CNRS.
 ******************************************************************************/
package fr.jmmc.smprsc.data;

import fr.jmmc.jmcs.data.app.ApplicationDescription;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class manages configuration files for the Interferometer configurations
 * @author Sylvain LAFRASSE
 */
public class RegistryManager {

    /** Configurations file name */
    private static final String CONF_FILE = "fr/jmmc/smprsc/resource/ApplicationData.xml";
    /** Class logger */
    private static final Logger _logger = LoggerFactory.getLogger(RegistryManager.class.getName());
    /** singleton pattern */
    private static volatile RegistryManager _instance = null;

    /* Members */
    /** Registry description (version and release notes) */
    private ApplicationDescription _description = null;

    /**
     * @return RegistryManager singleton
     *
     * @throws IllegalStateException if the configuration files are not found or IO failure
     * @throws IllegalArgumentException if the load configuration failed
     */
    public static synchronized RegistryManager getInstance()
            throws IllegalStateException, IllegalArgumentException {

        if (_instance == null) {
            final RegistryManager singleton = new RegistryManager();

            // Can throw RuntimeException
            singleton.initialize();
            _instance = singleton;
        }
        return _instance;
    }

    /**
     * Private constructor
     */
    private RegistryManager() {
        super();
    }

    /**
     * @return registry description (version and release notes)
     */
    public ApplicationDescription getDescription() {
        return _description;
    }

    /**
     * Initialize the singleton by loading the registry description file.
     *
     * @throws IllegalStateException if the configuration files are not found or IO failure
     * @throws IllegalArgumentException if the load configuration failed
     */
    private void initialize() throws IllegalStateException, IllegalArgumentException {

        _description = ApplicationDescription.loadDescription(CONF_FILE);
        _logger.info("Loaded AppLauncher Registry version '{}'.", _description.getProgramVersion());
    }
}
