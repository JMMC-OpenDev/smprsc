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
import java.util.HashMap;
import java.util.List;
import javax.swing.ImageIcon;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;
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
    /** Category's application names cache */
    private HashMap<Category, List<String>> _categoryApplicationNames;

    /**
     * Private constructor that must be empty.
     */
    private StubRegistry() {
        // Start JAXB
        jf = JAXBFactory.getInstance(SAMP_STUB_LIST_JAXB_PACKAGE);

        // Try to load __index__.xml resource
        final URL fileURL = FileUtils.getResource(SAMP_STUB_DATA_FILE_PATH + SAMP_STUB_LIST_FILENAME);
        final SampStubList sampStubList = loadData(fileURL);

        // Cache all application names for each category
        _categoryApplicationNames = new HashMap<Category, List<String>>();
        for (Family family : sampStubList.getFamilies()) {
            _categoryApplicationNames.put(family.getCategory(), family.getApplications());
        }
    }

    /**
     * Return the singleton instance of StubRegistry.
     *
     * @return the singleton preference instance
     */
    public static StubRegistry getInstance() {
        // Build new reference if singleton does not already exist or return previous reference
        if (_singleton == null) {
            _logger.debug("StubRegistry.getInstance()");
            
            _singleton = new StubRegistry();
        }
        
        return _singleton;
    }

    /**
     * Try to load index file content.
     * @return the list of SAMP stub application names, null otherwise.
     */
    public List<String> getCategoryApplicationNames(Category category) {
        return _categoryApplicationNames.get(category);
    }

    /**
     * @return the list of SAMP stub application resource paths, null otherwise.
     */
    public List<String> getCategoryApplicationResourcePathes(Category category) {

        // Get category's application names
        final List<String> applicationNameList = getCategoryApplicationNames(category);
        if (applicationNameList == null) {
            return null;
        }

        // Forge each application description file resource path
        for (int i = 0; i < applicationNameList.size(); i++) {
            applicationNameList.set(i, SAMP_STUB_DATA_FILE_PATH + applicationNameList.get(i) + SAMP_STUB_DATA_FILE_EXTENSION);
        }
        return applicationNameList;
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

    /**
     * Print the given name list on the standard output.
     * @param names string list to output
     */
    public static void printList(List<String> names) {
        int i = 1;
        for (String name : names) {
            System.out.println("stub[" + i + "/" + names.size() + "] = " + name);
            i++;
        }
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
        for (Category category : Category.values()) {
            System.out.println("-------------------------------------------------------");
            System.out.println("category = " + category.value());
            System.out.println("-------------------------------------------------------");
            
            List<String> names = StubRegistry.getInstance().getCategoryApplicationNames(category);
            for (String name : names) {
                final ImageIcon iconResourcePath = StubRegistry.getEmbeddedApplicationIcon(name);
                System.out.println("iconResourcePath[" + name + "] = " + (iconResourcePath == null ? "'null'" : iconResourcePath.getDescription()));
            }
            
            names = StubRegistry.getInstance().getCategoryApplicationResourcePathes(category);
            printList(names);
        }
    }
}
/*___oOo___*/
