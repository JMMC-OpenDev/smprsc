/*******************************************************************************
 * JMMC project ( http://www.jmmc.fr ) - Copyright (C) CNRS.
 ******************************************************************************/
package fr.jmmc.smprsc.data.stub;

import fr.jmmc.jmcs.gui.util.SwingUtils;
import fr.jmmc.jmcs.jaxb.JAXBFactory;
import fr.jmmc.jmcs.jaxb.XmlBindException;
import fr.jmmc.jmcs.network.Http;
import fr.jmmc.jmcs.network.PostQueryProcessor;
import fr.jmmc.jmcs.network.interop.SampMetaData;
import fr.jmmc.jmcs.util.FileUtils;
import fr.jmmc.smprsc.data.stub.model.SampStub;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.StringWriter;
import java.net.URI;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.swing.ImageIcon;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.bind.Unmarshaller;
import org.apache.commons.httpclient.methods.PostMethod;
import org.astrogrid.samp.Metadata;
import org.astrogrid.samp.Subscriptions;
import org.ivoa.util.concurrent.ThreadExecutors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Real SAMP application meta data older, that can report to JMMC central registry if not referenced yet.
 *
 * @author Sylvain LAFRASSE
 */
public class StubMetaData {

    // Constants
    /** Package name for JAXB generated code */
    private final static String STUB_DATA_MODEL_JAXB_PATH = "fr.jmmc.smprsc.data.stub.model";
    /** URL of the JMMC SAMP application meta data repository */
    private final static String REGISTRY_ROOT_URL = "http://jmmc.fr/~smprun/stubs/";
    //private static final String REGISTRY_ROOT_URL = "http://jmmc.fr/~lafrasse/stubs/";
    /** Directory containing all JMMC SAMP application meta data files*/
    private final static String SAMP_STUB_FILE_DIRECTORY = REGISTRY_ROOT_URL + "registry/";
    /** File extension of the JMMC SAMP application meta data file format */
    private final static String SAMP_STUB_FILE_EXTENSION = ".xml";
    /** Application icon files extension */
    private final static String SAMP_STUB_ICON_FILE_EXTENSION = ".png";
    /** Submission form name */
    private final static String SUBMISSION_FORM = "push.php";
    /** Logger */
    private final static Logger _logger = LoggerFactory.getLogger(StubMetaData.class.getName());
    /** JAXB initialization */
    private final static JAXBFactory _jaxbFactory = JAXBFactory.getInstance(STUB_DATA_MODEL_JAXB_PATH);
    /** Loaded SampStub cache */
    private final static Map<String, SampStub> _cachedSampStubs = new HashMap<String, SampStub>();
    // Members
    /** SAMP application meta data container */
    private final SampStub _data = new SampStub();
    /** Real application exact name */
    private final String _applicationName;
    /** Real application SAMP meta data */
    private final Metadata _sampMetaData;
    /** Real application SAMP mTypes */
    private final Subscriptions _sampSubscriptions;

    /**
     * Constructor.
     *
     * @param metadata SAMP Meta data
     * @param subscriptions SAMP mTypes
     */
    public StubMetaData(Metadata metadata, Subscriptions subscriptions) {

        _logger.debug("Serializing SAMP application meta-data.");

        _applicationName = metadata.getName();
        _data.setUid(_applicationName);
        _sampMetaData = metadata; // Shoyld clone it instead, but clone() is not implemented in jSAMP
        _sampSubscriptions = subscriptions;
    }

    public static SampStub retrieveSampStubForApplication(String applicationName) {

        SampStub sampStub = _cachedSampStubs.get(applicationName);
        if (sampStub == null) {

            sampStub = loadSampStubForApplication(applicationName);
            _cachedSampStubs.put(applicationName, sampStub);
        }

        return sampStub;
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
            final String iconResourcePath = SAMP_STUB_FILE_DIRECTORY + applicationName + SAMP_STUB_ICON_FILE_EXTENSION;

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
     * Upload application complete description to JMMC central repository (only if not known yet).
     */
    public void reportToCentralRepository() {

        // Make all the JERSEY network stuff run in the background
        ThreadExecutors.getGenericExecutor().submit(new Runnable() {

            AtomicBoolean shouldPhoneHome = new AtomicBoolean(false);
            ApplicationReportingForm dialog;

            public void run() {

                // If the current application does not exist in the central repository
                if (isNotKnownYet()) {

                    // TODO : Use dismissable message pane to always skip report ?

                    // Ask user if it is OK to phone application description back home
                    SwingUtils.invokeAndWaitEDT(new Runnable() {

                        /** Synchronized by EDT */
                        @Override
                        public void run() {
                            dialog = new ApplicationReportingForm(_applicationName);
                            shouldPhoneHome.set(dialog.shouldSubmit());
                        }
                    });

                    // If the user agreed to report unknown app
                    if (shouldPhoneHome.get()) {
                        serializeMetaData(dialog.getUserEmail(), dialog.getJnlpURL());
                        final String xmlRepresentation = marshallApplicationDescription();
                        postXMLToRegistry(xmlRepresentation);
                    }
                }
            }
        });
    }

    /**
     * @return true if the 'name' application is unknown, false otherwise.
     */
    private boolean isNotKnownYet() {

        _logger.info("Querying JMMC SAMP application registry for '{}' application ...", _applicationName);
        boolean unknownApplicationFlag = false; // In order to skip later application reporting if registry querying goes wrong

        try {
            final String path = computeResourcePathForApplication(_applicationName);
            final URI applicationDescriptionFileURI = Http.validateURL(path);
            final String result = Http.download(applicationDescriptionFileURI, false); // Use the multi-threaded HTTP client
            _logger.debug("HTTP response : '" + result + "'.");

            // Decipher whether the meta-data is alredy registered or not
            unknownApplicationFlag = (result == null) || (result.length() == 0);
            _logger.info("SAMP application '{}' {}found in JMMC registry.", _applicationName, (unknownApplicationFlag ? "not " : ""));

        } catch (IOException ioe) {
            _logger.error("Cannot get SAMP application meta-data : ", ioe);
        }

        return unknownApplicationFlag;
    }

    private static SampStub loadSampStubForApplication(final String applicationName) {

        final String path = computeResourcePathForApplication(applicationName);
        final URL resourceURL = FileUtils.getResource(path);
        // Note : use input stream to avoid JNLP offline bug with URL (Unknown host exception)
        try {
            final Unmarshaller u = _jaxbFactory.createUnMarshaller();
            return (SampStub) u.unmarshal(new BufferedInputStream(resourceURL.openStream()));
        } catch (IOException ioe) {
            throw new IllegalStateException("Load failure on " + resourceURL, ioe);
        } catch (JAXBException je) {
            throw new IllegalArgumentException("Load failure on " + resourceURL, je);
        }
    }

    private static String computeResourcePathForApplication(String applicationName) {
        if (applicationName == null) {
            throw new IllegalArgumentException("applicationName");
        }
        return SAMP_STUB_FILE_DIRECTORY + applicationName + SAMP_STUB_FILE_EXTENSION;
    }

    /**
     * @param userEmail 
     * @param jnlpURL 
     */
    private void serializeMetaData(String userEmail, String jnlpURL) {

        fr.jmmc.smprsc.data.stub.model.Metadata tmp;

        // Add user given inputs
        if ((userEmail != null) && (userEmail.length() > 0)) {
            tmp = new fr.jmmc.smprsc.data.stub.model.Metadata("email", userEmail);
            _data.getMetadatas().add(tmp);
        }
        if ((jnlpURL != null) && (jnlpURL.length() > 0)) {
            tmp = new fr.jmmc.smprsc.data.stub.model.Metadata(SampMetaData.JNLP_URL.id(), jnlpURL);
            _data.getMetadatas().add(tmp);
        }

        // Serialize all SAMP meta data
        for (Object key : _sampMetaData.keySet()) {
            tmp = new fr.jmmc.smprsc.data.stub.model.Metadata(key.toString(), _sampMetaData.get(key).toString());
            _data.getMetadatas().add(tmp);
        }

        // Serialize all SAMP mTypes
        for (Object subscription : _sampSubscriptions.keySet()) {
            _data.getSubscriptions().add(subscription.toString());
        }
    }

    /**
     * @param xml
     */
    private void postXMLToRegistry(final String xml) {

        // Check parameter vailidty
        if (xml == null) {
            _logger.warn("Something went wrong while serializing SAMP application '{}' meta-data ... aborting report.", _applicationName);
            return;
        }

        _logger.info("Sending JMMC SAMP application '{}' XML description to JMMC registry ...", _applicationName);

        try {
            final URI uri = Http.validateURL(REGISTRY_ROOT_URL + SUBMISSION_FORM);
            // use the multi threaded HTTP client
            final String result = Http.post(uri, false, new PostQueryProcessor() {

                /**
                 * Process the given post method to define its HTTP input fields
                 * @param method post method to complete
                 * @throws IOException if any IO error occurs
                 */
                @Override
                public void process(final PostMethod method) throws IOException {
                    method.addParameter("uid", _applicationName);
                    method.addParameter("xmlSampStub", xml);
                }
            });

            _logger.debug("HTTP response : '{}'.", result);

            // Parse result for failure
            if (result != null) {
                _logger.info("Sent SAMP application '{}' XML description to JMMC regitry.", _applicationName);
            } else {
                _logger.warn("SAMP application meta-data were not sent properly.");
            }

        } catch (IOException ioe) {
            _logger.error("Cannot send SAMP application meta-data : ", ioe);
        }
    }

    private String marshallApplicationDescription() throws XmlBindException {

        // Start JAXB
        final Marshaller marshaller = _jaxbFactory.createMarshaller();
        final StringWriter stringWriter = new StringWriter();

        // Serialize application description in XML
        try {
            marshaller.setProperty(Marshaller.JAXB_ENCODING, "UTF-8");
            marshaller.marshal(_data, stringWriter);

        } catch (JAXBException je) {
            _logger.error("Cannot marshall SAMP application meta-data : ", je);
            return null;
        }

        final String xml = stringWriter.toString();
        _logger.debug("Generated SAMP application '{}' XML description :\n{}", _applicationName, xml);
        return xml;
    }
}
