/*******************************************************************************
 * JMMC project ( http://www.jmmc.fr ) - Copyright (C) CNRS.
 ******************************************************************************/
package fr.jmmc.smprsc;

import fr.jmmc.jmcs.jaxb.JAXBFactory;
import fr.jmmc.jmcs.jaxb.XmlBindException;
import fr.jmmc.jmcs.util.FileUtils;
import fr.jmmc.smprsc.data.list.model.Category;
import fr.jmmc.smprsc.data.list.model.Family;
import fr.jmmc.smprsc.data.list.model.SampStubList;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import javax.swing.ImageIcon;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;
import org.ivoa.util.CollectionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * smprsc access singleton.
 * 
 * @author Sylvain LAFRASSE
 */
public class StubRegistry {

    /** Logger - get from given class name */
    private static final Logger _logger = LoggerFactory.getLogger(StubRegistry.class.getName());
    /** Internal singleton instance holder */
    private static StubRegistry _singleton = null;
    /** package name for JAXB generated code */
    private final static String SAMP_STUB_LIST_JAXB_PACKAGE = "fr.jmmc.smprsc.data.list.model";
    /** SAMP stub list file name */
    public static final String SAMP_STUB_LIST_FILENAME = "__index__.xml";
    /** SAMP stub application files path */
    public static final String SAMP_STUB_DATA_FILE_PATH = "fr/jmmc/smprsc/registry/";
    /** SAMP stub application files extension */
    public static final String SAMP_STUB_DATA_FILE_EXTENSION = ".xml";
    /** Application icon files extension */
    public static final String SAMP_STUB_ICON_FILE_EXTENSION = ".png";
    /** internal JAXB Factory */
    private final JAXBFactory jf;
    /** Known application names list */
    private static List<String> _knownApplicationNames;
    /** Category's application names cache */
    private static Map<Category, List<String>> _categoryApplicationNames;
    /** Category's visible application names cache */
    private static Map<Category, List<String>> _categoryVisibleApplicationNames;

    /**
     * Private constructor that must be empty.
     */
    private StubRegistry() {
        // Start JAXB
        jf = JAXBFactory.getInstance(SAMP_STUB_LIST_JAXB_PACKAGE);

        // Try to load __index__.xml resource
        final URL fileURL = FileUtils.getResource(SAMP_STUB_DATA_FILE_PATH + SAMP_STUB_LIST_FILENAME);
        final SampStubList sampStubList = loadData(fileURL);

        // Members creation
        _knownApplicationNames = new ArrayList<String>();
        _categoryApplicationNames = new EnumMap<Category, List<String>>(Category.class);
        _categoryVisibleApplicationNames = new EnumMap<Category, List<String>>(Category.class);

        // Cache all application names for each category
        for (Family family : sampStubList.getFamilies()) {

            // Get the list of application name for the current category
            final Category currentCategory = family.getCategory();
            final List<String> fullCategoryApplicationNameList = family.getApplications();
            _categoryApplicationNames.put(currentCategory, fullCategoryApplicationNameList);

            // Build the list of visible applications for the current category
            List<String> visibleCategoryApplications = new ArrayList<String>();
            for (String applicationName : fullCategoryApplicationNameList) {

                _knownApplicationNames.add(applicationName);

                if (getEmbeddedApplicationIcon(applicationName) != null) {
                    visibleCategoryApplications.add(applicationName);
                }
            }

            if (visibleCategoryApplications.size() > 0) {
                _categoryVisibleApplicationNames.put(currentCategory, visibleCategoryApplications);
            }
        }
    }

    /**
     * Return the singleton instance of StubRegistry.
     *
     * @return the singleton preference instance
     */
    private static StubRegistry getInstance() {
        // Build new reference if singleton does not already exist or return previous reference
        if (_singleton == null) {
            _logger.debug("StubRegistry.getInstance()");

            _singleton = new StubRegistry();
        }

        return _singleton;
    }

    /**
     * @return true if the given application name is known, false otherwise
     */
    public static boolean isApplicationKnown(String applicationName) {
        getInstance();
        return _knownApplicationNames.contains(applicationName);
    }

    /**
     * @return the complete resource path of the given application.
     */
    public static String getApplicationResourcePath(final String applicationName) {
        return SAMP_STUB_DATA_FILE_PATH + applicationName + SAMP_STUB_DATA_FILE_EXTENSION;
    }

    /**
     * @return the list of SAMP stub visible application names for the given category, null otherwise.
     */
    public static List<String> getCategoryVisibleApplicationNames(Category category) {
        getInstance();
        return _categoryVisibleApplicationNames.get(category);
    }

    /**
     * @return the list of SAMP stub application resource paths for the given category.
     */
    public static List<String> getCategoryApplicationResourcePaths(Category category) {

        getInstance();

        // Get category's application names
        final List<String> applicationPathList = new ArrayList<String>();

        // Forge each application description file resource path
        for (String applicationName : _categoryApplicationNames.get(category)) {
            applicationPathList.add(getApplicationResourcePath(applicationName));
        }

        return applicationPathList;
    }

    /**
     * Try to load embedded icon for given application name.
     * 
     * @param applicationName the application name of the sought icon.
     * @return the icon if found, null otherwise.
     */
    public static ImageIcon getEmbeddedApplicationIcon(String applicationName) {

        ImageIcon icon = null;

        try {
            // Forge icon resource path
            final String iconResourcePath = SAMP_STUB_DATA_FILE_PATH + applicationName + SAMP_STUB_ICON_FILE_EXTENSION;

            // Try to load application icon resource
            final URL fileURL = FileUtils.getResource(iconResourcePath);
            if (fileURL != null) {
                icon = new ImageIcon(fileURL);
            }
        } catch (IllegalStateException ise) {
            _logger.warn("Could not find '{}' embedded icon.", applicationName);
        }

        return icon;
    }

    /** Invoke JAXB to load XML file */
    private SampStubList loadData(final URL dataModelURL) throws XmlBindException, IllegalArgumentException, IllegalStateException {

        // Note : use input stream to avoid JNLP offline bug with URL (Unknown host exception)
        try {
            final Unmarshaller u = jf.createUnMarshaller();
            return (SampStubList) u.unmarshal(new BufferedInputStream(dataModelURL.openStream()));
        } catch (IOException ioe) {
            throw new IllegalStateException("Load failure on " + dataModelURL, ioe);
        } catch (JAXBException je) {
            throw new IllegalArgumentException("Load failure on " + dataModelURL, je);
        }
    }

    /**
     * Main entry point
     *
     * @param args command line arguments (open file ...)
     */
    @SuppressWarnings("ResultOfObjectAllocationIgnored")
    public static void main(final String[] args) {

        List<String> list = null;

        for (Category category : Category.values()) {

            System.out.println("-------------------------------------------------------");
            System.out.println("category = " + category.value());
            System.out.println("-------------------------------------------------------");

            list = StubRegistry.getCategoryVisibleApplicationNames(category);
            System.out.println("Visible apps : " + CollectionUtils.toString(list, ", ", "{", "}"));

            list = StubRegistry.getCategoryApplicationResourcePaths(category);
            System.out.println("Application paths : " + CollectionUtils.toString(list, ", ", "{", "}"));

            System.out.println("");
        }

        System.out.println("-------------------------------------------------------");
        String[] applicationNames = {"SearchCal", "Aladin", "toto"};
        for (String string : applicationNames) {
            System.out.println("Application '" + string + "' is" + (isApplicationKnown(string) ? " " : " NOT ") + "known.");
        }
        System.out.println("-------------------------------------------------------");
    }
}
/*___oOo___*/
