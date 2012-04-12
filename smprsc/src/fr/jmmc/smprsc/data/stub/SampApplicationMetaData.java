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
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Marshaller;
import javax.xml.bind.Unmarshaller;
import org.apache.commons.httpclient.methods.PostMethod;
import org.astrogrid.samp.Metadata;
import org.astrogrid.samp.Subscriptions;
import org.ivoa.util.concurrent.ThreadExecutors;

/**
 * Real SAMP application meta data older, that can report to JMMC central registry if not referenced yet.
 *
 * @author Sylvain LAFRASSE
 */
public class SampApplicationMetaData {

    /** Logger */
    private static final Logger _logger = Logger.getLogger(SampApplicationMetaData.class.getName());
    /** Package name for JAXB generated code */
    private final static String STUB_DATA_MODEL_JAXB_PATH = "fr.jmmc.smprsc.data.stub.model";
    /** JAXB initialization */
    private final static JAXBFactory _jaxbFactory = JAXBFactory.getInstance(STUB_DATA_MODEL_JAXB_PATH);
    /** URL of the JMMC SAMP application meta data repository */
    private static final String REGISTRY_ROOT_URL = "http://jmmc.fr/~smprun/stubs/";
    //private static final String REGISTRY_ROOT_URL = "http://jmmc.fr/~lafrasse/stubs/";
    /** Directory containing all JMMC SAMP application meta data files*/
    private static final String FILE_DIRECTORY = "registry/";
    /** File extension of the JMMC SAMP application meta data file format */
    private static final String FILE_EXTENSION = ".xml";
    /** Submission form name */
    private static final String SUBMISSION_FORM = "push.php";
    /** SAMP application meta data container */
    private SampStub _data = new SampStub();
    /** Real application exact name */
    private String _applicationName;
    /** Real application SAMP meta data */
    private Metadata _sampMetaData;
    /** Real application SAMP mTypes */
    private Subscriptions _sampSubscriptions;

    /**
     * Constructor.
     *
     * @param metadata SAMP Meta data
     * @param subscriptions SAMP mTypes
     */
    public SampApplicationMetaData(Metadata metadata, Subscriptions subscriptions) {

        _logger.fine("Serializing SAMP application meta-data.");

        _applicationName = metadata.getName();
        _data.setUid(_applicationName);
        _sampMetaData = metadata; // Shoyld clone it instead, but clone() is not implemented in jSAMP
        _sampSubscriptions = subscriptions;
    }

    public static SampStub loadSampSubFromResourcePath(final String path) {
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

        _logger.info("Querying JMMC SAMP application registry for '" + _applicationName + "' application ...");
        boolean unknownApplicationFlag = false; // In order to skip later application reporting if registry querying goes wrong

        try {
            final URI applicationDescriptionFileURI = Http.validateURL(REGISTRY_ROOT_URL + FILE_DIRECTORY + _applicationName + FILE_EXTENSION);
            final String result = Http.download(applicationDescriptionFileURI, false); // Use the multi-threaded HTTP client
            _logger.fine("HTTP response : '" + result + "'.");

            // Decipher whether the meta-data is alredy registered or not
            unknownApplicationFlag = (result == null) || (result.length() == 0);
            _logger.info("SAMP application '" + _applicationName + "'" + (unknownApplicationFlag ? " not " : " ") + "found in JMMC registry.");

        } catch (IOException ioe) {
            _logger.log(Level.SEVERE, "Cannot get SAMP application meta-data : ", ioe);
        }

        return unknownApplicationFlag;
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
            _logger.warning("Something went wrong while serializing SAMP application '" + _applicationName + "' meta-data ... aborting report.");
            return;
        }

        _logger.info("Sending JMMC SAMP application '" + _applicationName + "' XML description to JMMC registry ...");

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

            _logger.fine("HTTP response : '" + result + "'.");

            // Parse result for failure
            if (result != null) {
                _logger.info("Sent SAMP application '" + _applicationName + "' XML description to JMMC regitry.");
            } else {
                _logger.warning("SAMP application meta-data were not sent properly.");
            }

        } catch (IOException ioe) {
            _logger.log(Level.SEVERE, "Cannot send SAMP application meta-data : ", ioe);
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
            _logger.log(Level.SEVERE, "Cannot marshall SAMP application meta-data : ", je);
            return null;
        }

        final String xml = stringWriter.toString();
        _logger.fine("Generated SAMP application '" + _applicationName + "' XML description :\n" + xml);
        return xml;
    }
}