/*******************************************************************************
 * JMMC project ( http://www.jmmc.fr ) - Copyright (C) CNRS.
 ******************************************************************************/
package fr.jmmc.smprsc;

import fr.jmmc.jmcs.jaxb.JAXBFactory;
import fr.jmmc.jmcs.jaxb.XmlBindException;
import fr.jmmc.jmcs.util.FileUtils;
import fr.jmmc.smprsc.data.model.SampStubList;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.net.URL;
import java.util.List;
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
    private final static String SAMP_STUB_LIST_JAXB_PATH = "fr.jmmc.smprsc.data.model";
    /** SAMP stub list file i.e. "index.xml" */
    public static final String SAMP_STUB_LIST_DATA_FILE_PATH = "fr/jmmc/smprsc/registry/";
    /** SAMP stub list file i.e. "index.xml" */
    public static final String SAMP_STUB_LIST_DATA_FILE_NAME = "__index__.xml";
    /** internal JAXB Factory */
    private final JAXBFactory jf;

    /**
     * Private constructor that must be empty.
     */
    private StubRegistry() {
        // Start JAXB
        jf = JAXBFactory.getInstance(SAMP_STUB_LIST_JAXB_PATH);
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
     * Try to load index file content.
     * @return the list of SAMP stub application names.
     */
    public List<String> getKnownApplications() {
        final URL fileURL = FileUtils.getResource(SAMP_STUB_LIST_DATA_FILE_PATH + SAMP_STUB_LIST_DATA_FILE_NAME);
        SampStubList list = loadData(fileURL);
        return list.getNames();
    }

    /**
     * @return the list of SAMP stub application resource pathes.
     */
    public List<String> getKnownApplicationResourcePathes() {
        List<String> list = getKnownApplications();
        for (int i = 0; i < list.size(); i++) {
            list.set(i, SAMP_STUB_LIST_DATA_FILE_PATH + list.get(i) + ".xml");
        }
        return list;
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

    /**
     * Main entry point
     *
     * @param args command line arguments (open file ...)
     */
    @SuppressWarnings("ResultOfObjectAllocationIgnored")
    public static void main(final String[] args) {
        List<String> names = StubRegistry.getInstance().getKnownApplications();
        printList(names);

        names = StubRegistry.getInstance().getKnownApplicationResourcePathes();
        printList(names);
    }
}
/*___oOo___*/
