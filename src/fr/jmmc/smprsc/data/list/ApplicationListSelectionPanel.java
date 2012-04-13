/*******************************************************************************
 * JMMC project ( http://www.jmmc.fr ) - Copyright (C) CNRS.
 ******************************************************************************/
package fr.jmmc.smprsc.data.list;

import com.jidesoft.swing.CheckBoxTree;
import com.jidesoft.swing.CheckBoxTreeSelectionModel;
import fr.jmmc.jmcs.gui.PreferencesView;
import fr.jmmc.jmcs.network.interop.SampMetaData;
import fr.jmmc.jmcs.util.ImageUtils;
import fr.jmmc.smprsc.StubRegistry;
import fr.jmmc.smprsc.data.list.model.Category;
import fr.jmmc.smprsc.data.stub.SampApplicationMetaData;
import fr.jmmc.smprsc.data.stub.model.Metadata;
import fr.jmmc.smprsc.data.stub.model.SampStub;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import javax.swing.*;
import javax.swing.event.TreeSelectionEvent;
import javax.swing.event.TreeSelectionListener;
import javax.swing.tree.*;
import org.ivoa.util.CollectionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 
 * @author lafrasse
 */
public class ApplicationListSelectionPanel extends JPanel {

    /** Logger - get from given class name */
    private static final Logger _logger = LoggerFactory.getLogger(ApplicationListSelectionPanel.class.getName());
    // CONSTANTS
    private static final int PANEL_HEIGHT = PreferencesView.FRAME_HEIGHT;
    // Cached application data
    private final HashMap<String, ImageIcon> _cachedApplicationIcons = new HashMap<String, ImageIcon>();
    private final HashMap<String, String> _cachedApplicationDescriptions = new HashMap<String, String>();
    private final HashMap<String, Boolean> _cachedBetaCheckBoxStates = new HashMap<String, Boolean>();
    private String _currentlySelectedApplicationName = null;
    private boolean _programaticCheckingUnderway = true;
    // Tree stuff
    private static final int TREE_WIDTH = 200;
    private final DefaultMutableTreeNode _treeDataModel;
    private static final List<String> ALL = null;
    private static final String ROOT_NODE_NAME = "Root";
    private final CheckBoxTree _checkBoxTree;
    private static final int ICON_SIZE = 16;
    // Description stuff
    private static final int EDITOR_PANE_WIDTH = PreferencesView.FRAME_WIDTH - TREE_WIDTH;
    private final JEditorPane _descriptionEditorPane;
    private final JScrollPane _descriptionScrollPane;
    private final JCheckBox _betaCheckBox;

    public ApplicationListSelectionPanel() {

        super();

        setLayout(new BorderLayout());

        // Setup tree pane
        _treeDataModel = populateTreeDataModel();
        _checkBoxTree = setupCheckBoxTree();
        setCheckedApplicationNames(ALL);
        add(new JScrollPane(_checkBoxTree), BorderLayout.WEST);

        // Setup description pane
        _descriptionEditorPane = setupDescriptionEditorPane();
        _descriptionScrollPane = setupDescriptionScrollPane();
        _betaCheckBox = setupBetaCheckBox();
        JPanel descriptionPanel = new JPanel();
        descriptionPanel.setLayout(new BoxLayout(descriptionPanel, BoxLayout.PAGE_AXIS));
        descriptionPanel.add(_descriptionScrollPane);
        descriptionPanel.add(_betaCheckBox);
        add(descriptionPanel, BorderLayout.CENTER);
    }

    private DefaultMutableTreeNode populateTreeDataModel() {

        DefaultMutableTreeNode rootNode = new DefaultMutableTreeNode(ROOT_NODE_NAME);

        // For each known application category
        for (Category applicationCategory : Category.values()) {

            final String categoryName = applicationCategory.value();
            DefaultMutableTreeNode categoryNode = new DefaultMutableTreeNode(categoryName);
            rootNode.add(categoryNode);
            _logger.trace("Loading applications for category '{}' :", categoryName);

            // Gets all category's visible application names
            for (String applicationName : StubRegistry.getCategoryVisibleApplicationNames(applicationCategory)) {

                // Add the application node only if it is a visible one
                ImageIcon applicationIcon = StubRegistry.getEmbeddedApplicationIcon(applicationName);
                // Load application icons once and for all
                ImageIcon resizedApplicationIcon = ImageUtils.getScaledImageIcon(applicationIcon, ICON_SIZE, ICON_SIZE);
                _cachedApplicationIcons.put(applicationName, resizedApplicationIcon);

                // Create application node
                DefaultMutableTreeNode applicationNode = new DefaultMutableTreeNode(applicationName);
                categoryNode.add(applicationNode);
                _logger.trace("\t- found application '{}' with icon.", applicationName);
            }
        }

        return rootNode;
    }

    private CheckBoxTree setupCheckBoxTree() {

        CheckBoxTree checkBoxTree = new CheckBoxTree(_treeDataModel) {

            @Override
            public Dimension getPreferredScrollableViewportSize() {
                return new Dimension(TREE_WIDTH, PANEL_HEIGHT);
            }
        };

        checkBoxTree.setRootVisible(false); // Hide root node
        checkBoxTree.setShowsRootHandles(true); // Node handles should be displayed

        // Restrict row selection to one item at a time
        DefaultTreeSelectionModel treeSelectionModel = new DefaultTreeSelectionModel();
        treeSelectionModel.setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
        checkBoxTree.setSelectionModel(treeSelectionModel);
        checkBoxTree.getCheckBoxTreeSelectionModel().setSingleEventMode(true);

        checkBoxTree.setDigIn(true); // If a category is clicked, all its applications are also checked
        checkBoxTree.setClickInCheckBoxOnly(true); // Allow selection of items whithout setting them at the same time
        checkBoxTree.setCellRenderer(new ApplicationIconRenderer());

        listenToSelections(checkBoxTree);
        listenToChecks(checkBoxTree);

        return checkBoxTree;
    }

    private void listenToSelections(final CheckBoxTree tree) {

        tree.addTreeSelectionListener(new TreeSelectionListener() {

            @Override
            public void valueChanged(TreeSelectionEvent e) {
                _currentlySelectedApplicationName = null; // Reset selection memory
                DefaultMutableTreeNode node = (DefaultMutableTreeNode) tree.getLastSelectedPathComponent();

                // if nothing is selected
                if (node == null) {

                    _logger.trace("Nothing selected.");

                    // Clear information pane
                    fillApplicationDescriptionPane(null);

                } else if (!node.isLeaf()) { // i.e a category is selected

                    _logger.trace("Category selected.");

                    // Clear information pane
                    fillApplicationDescriptionPane(null);

                } else { // i.e an application is selected

                    // Retrieve the selected application name
                    _currentlySelectedApplicationName = node.getUserObject().toString();

                    _logger.debug("Application '{}' is selected.", _currentlySelectedApplicationName);

                    // Fill information pane accordinaly
                    fillApplicationDescriptionPane(_currentlySelectedApplicationName);
                }
            }
        });
    }

    private void listenToChecks(final CheckBoxTree tree) {

        tree.getCheckBoxTreeSelectionModel().addTreeSelectionListener(new TreeSelectionListener() {

            @Override
            public void valueChanged(TreeSelectionEvent e) {

                if (_programaticCheckingUnderway) {
                    _logger.trace("Skipping programatically checked applications.");
                    return;
                }

                // Get the currently checked paths list
                TreePath[] checkedPaths = tree.getCheckBoxTreeSelectionModel().getSelectionPaths();
                if (checkedPaths == null) {
                    _logger.debug("Discovered that NOTHING is checked - ignoring event.");
                    return;
                }

                // Retrieve application names for each checked box
                List<String> checkedApplicationList = new ArrayList<String>();
                for (TreePath checkedPath : checkedPaths) {

                    final Object checkedBox = checkedPath.getLastPathComponent();
                    final String checkedBoxName = checkedBox.toString();
                    _logger.debug("Discovered that '{}' is checked.", checkedBoxName);

                    // If the current checked box is a leaf
                    DefaultTreeModel treeModel = new DefaultTreeModel(_treeDataModel);
                    if (treeModel.isLeaf(checkedBox)) {
                        // Simply add it to the list
                        checkedApplicationList.add(checkedBoxName);
                        continue;
                    }

                    _logger.trace("But '{}' is NOT a LEAF - Going deeper :", checkedBoxName);

                    // Get all the applications in the current 'directory' node
                    for (Category category : Category.values()) {

                        // List category applications (or all applications if ROOT node)
                        if ((checkedBoxName.equals(ROOT_NODE_NAME)) || (checkedBoxName.equals(category.value()))) {

                            // Retrieve all the current category visible applications
                            for (String applicationName : StubRegistry.getCategoryVisibleApplicationNames(category)) {

                                _logger.trace("\t- made of visible application = '{}'.", applicationName);
                                checkedApplicationList.add(applicationName);
                            }
                        }
                    }
                }

                _logger.debug("Notifying manually checked applications : {}", checkedApplicationList);
                checkedApplicationChanged(checkedApplicationList);
            }
        });
    }

    private class ApplicationIconRenderer extends DefaultTreeCellRenderer {

        @Override
        public Component getTreeCellRendererComponent(JTree tree, Object value, boolean sel, boolean expanded, boolean leaf, int row, boolean hasFocus) {

            super.getTreeCellRendererComponent(tree, value, sel, expanded, leaf, row, hasFocus);

            // Leave category icons alone
            if (leaf) {

                // Get application's name
                final String applicationName = value.toString();

                // Get application's icon
                final ImageIcon icon = _cachedApplicationIcons.get(applicationName);

                if (icon != null) {
                    setIcon(icon);
                }

                _logger.trace("Rendered '{}' application icon.", applicationName);
            }

            return this;
        }
    }

    private JEditorPane setupDescriptionEditorPane() {

        JEditorPane descriptionEditorPane = new JEditorPane();

        descriptionEditorPane.setPreferredSize(new Dimension(EDITOR_PANE_WIDTH, PANEL_HEIGHT));
        descriptionEditorPane.setEditable(false);
        descriptionEditorPane.setMargin(new Insets(5, 5, 5, 5));
        descriptionEditorPane.setContentType("text/html");

        return descriptionEditorPane;
    }

    private JScrollPane setupDescriptionScrollPane() {

        JScrollPane descriptionScrollPane = new JScrollPane(_descriptionEditorPane);
        final Dimension scrollPaneDimension = new Dimension(EDITOR_PANE_WIDTH, PANEL_HEIGHT);
        descriptionScrollPane.setMaximumSize(scrollPaneDimension);
        descriptionScrollPane.setPreferredSize(scrollPaneDimension);
        descriptionScrollPane.setAlignmentX(CENTER_ALIGNMENT);

        // Ensure background color consistency
        _descriptionEditorPane.setOpaque(false);
        descriptionScrollPane.setOpaque(false);
        descriptionScrollPane.getViewport().setOpaque(false);
        descriptionScrollPane.setBorder(null);

        return descriptionScrollPane;
    }

    private JCheckBox setupBetaCheckBox() {

        final JCheckBox betaCheckBox = new JCheckBox("Use beta version");

        betaCheckBox.addActionListener(new ActionListener() {

            @Override
            public void actionPerformed(ActionEvent e) {
                final boolean betaCheckBoxIsSelected = betaCheckBox.isSelected();
                _logger.debug("Beta JNLP Checkbox has been '" + (betaCheckBoxIsSelected ? "" : "de") + "selected' for application '" + _currentlySelectedApplicationName + "', saving state.");
                _cachedBetaCheckBoxStates.put(_currentlySelectedApplicationName, betaCheckBoxIsSelected);

                // Get all desired beta applications
                List<String> betaApplicationList = new ArrayList<String>();
                for (String string : _cachedBetaCheckBoxStates.keySet()) {
                    if (_cachedBetaCheckBoxStates.get(string) == true) {
                        betaApplicationList.add(string);
                    }
                }
                betaApplicationChanged(betaApplicationList);
            }
        });

        betaCheckBox.setEnabled(false);
        betaCheckBox.setAlignmentX(CENTER_ALIGNMENT);

        return betaCheckBox;
    }

    private void fillApplicationDescriptionPane(String applicationName) {

        // Clear the description if no application name provided
        if (applicationName == null) {
            _descriptionEditorPane.setText("");
            _logger.trace("Cleared description pane.");
            return;
        }

        // if the application description is not yet cached
        String applicationDescription = _cachedApplicationDescriptions.get(applicationName);
        if (applicationDescription == null) {

            // Load application's SAMP meta data from JAR
            String applicationMetaDataResourcePath = StubRegistry.forgeApplicationResourcePath(applicationName);

            _logger.trace("Loading '{}' meta data from path '{}' :", applicationName, applicationMetaDataResourcePath);
            SampStub applicationData = SampApplicationMetaData.loadSampSubFromResourcePath(applicationMetaDataResourcePath);

            HashMap<String, String> metaDataMap = new HashMap<String, String>();
            for (Metadata applicationMetaData : applicationData.getMetadatas()) {
                final String metaDataKey = applicationMetaData.getKey();
                final String metaDataValue = applicationMetaData.getValue();
                metaDataMap.put(metaDataKey, metaDataValue);
                _logger.trace("\t- found meta data ['{}' -> '{}'].", metaDataKey, metaDataValue);
            }

            // HTML generation
            _logger.debug("Generating HTML for '{}' application :", applicationName);
            final StringBuilder generatedHtml = new StringBuilder(4096);
            generatedHtml.append("<HTML><HEAD></HEAD><BODY>");
            for (SampMetaData metaData : SampMetaData.values()) {

                // Label
                final String label = metaData.getLabel();
                if (label == null) {
                    continue;
                }

                // Value
                String value = metaDataMap.get(metaData.id());
                if (value != null) {
                    generatedHtml.append("<B>").append(label).append(":</B> ");
                    generatedHtml.append(value).append("<BR/><BR/>");
                }
                _logger.trace("\t- found meta data for '{}' = '{}'.", label, value);
            }
            generatedHtml.append("</BODY></HTML>");

            // Retrieve and cache wether the current application has a beta JNLP URL or not
            final String betaJnlpUrl = metaDataMap.get(SampMetaData.JNLP_BETA_URL.id());
            if (betaJnlpUrl != null) {
                final boolean betaIsEnabled = isApplicationBetaJnlpUrlInUse(applicationName);
                _cachedBetaCheckBoxStates.put(applicationName, betaIsEnabled);
                _logger.trace("\t- found beta JNLP URL, retrieveing saved checkbox state.");
            }

            // Cache application description for later use
            applicationDescription = generatedHtml.toString();
            _cachedApplicationDescriptions.put(applicationName, applicationDescription);
        } else {
            _logger.debug("Retrieved cached '{}' application description.", applicationName);
        }

        // Set application description in editor pane
        _descriptionEditorPane.setText(applicationDescription);

        // Show first line of editor pane, and not its last line as by default !
        _descriptionEditorPane.setCaretPosition(0);

        Boolean checkBoxState = _cachedBetaCheckBoxStates.get(applicationName);
        if (checkBoxState == null) {
            _betaCheckBox.setEnabled(false);
            _betaCheckBox.setSelected(false);
            _logger.debug("No beta JLNP URL found for '{}', disabled beta checkbox.", applicationName);
        } else {
            _betaCheckBox.setEnabled(true);
            _betaCheckBox.setSelected(checkBoxState);
            _logger.debug("Found beta JLNP URL for '{}', enabled beta checkbox to saved state of '{}'.", applicationName, checkBoxState);
        }
    }

    /**
     * Called each time the selection of applications changed.
     * Should be override to save selected application list.
     */
    protected void checkedApplicationChanged(List<String> checkedApplicationList) {
        System.out.println("Selected applications : " + CollectionUtils.toString(checkedApplicationList, ", ", "{", "}") + ".");
    }

    /**
     * Define the current application selection.
     * @param applicationNames the list of application names to select, or null for all.
     */
    public final synchronized void setCheckedApplicationNames(List<String> applicationNames) {

        _programaticCheckingUnderway = true;

        final CheckBoxTreeSelectionModel checkBoxTreeSelectionModel = _checkBoxTree.getCheckBoxTreeSelectionModel();

        // For each tree row
        for (int currentRow = 0; currentRow < _checkBoxTree.getRowCount(); currentRow++) {

            // Select the check box for current row
            final TreePath pathForCurrentRow = _checkBoxTree.getPathForRow(currentRow);
            // Expand all rows
            _checkBoxTree.expandRow(currentRow);

            // Select all or only the desired applications
            final String currentRowApplicationName = pathForCurrentRow.getLastPathComponent().toString();
            if ((applicationNames == null) || (applicationNames.contains(currentRowApplicationName))) {
                checkBoxTreeSelectionModel.addSelectionPath(pathForCurrentRow);
                _logger.debug("Checked '{}' application.", currentRowApplicationName);
            } else {
                checkBoxTreeSelectionModel.removeSelectionPath(pathForCurrentRow);
                _logger.trace("Unchecked '{}' application.", currentRowApplicationName);
            }
        }

        _programaticCheckingUnderway = false;
    }

    /**
     * Called each time the beta state of any application changed.
     * Should be override to save beta application list.
     */
    protected void betaApplicationChanged(List<String> betaApplicationList) {
        System.out.println("Beta applications : " + CollectionUtils.toString(betaApplicationList, ", ", "{", "}") + ".");
    }

    /**
     * Called each time any application is selected, to retrieve whether it has a beta release or not.
     * By default force use of production JNLP URL.
     * 
     * Should be override to return beta application status.
     * @return true if the given application has a beta JNLP URL, false otherwise.
     */
    protected boolean isApplicationBetaJnlpUrlInUse(String applicationName) {
        return false; // By default use production JNLP URL.
    }

    public static void main(String[] args) {
        JFrame frame = new JFrame();
        final ApplicationListSelectionPanel applicationListSelectionPanel = new ApplicationListSelectionPanel();
        frame.add(applicationListSelectionPanel);
        frame.pack();
        frame.setVisible(true);

        try {
            Thread.sleep(1000);
        } catch (InterruptedException ex) {
            _logger.error("", ex);
        }

        List<String> specificApplicationNames = new ArrayList<String>();
        specificApplicationNames.add("Aspro2");
        specificApplicationNames.add("SearchCal");
        specificApplicationNames.add("LITpro");
        specificApplicationNames.add("topcat");
        applicationListSelectionPanel.setCheckedApplicationNames(specificApplicationNames);
    }
}
