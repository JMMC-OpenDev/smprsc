/*******************************************************************************
 * JMMC project ( http://www.jmmc.fr ) - Copyright (C) CNRS.
 ******************************************************************************/
package fr.jmmc.smprsc.data.list;

import com.jidesoft.swing.CheckBoxTree;
import com.jidesoft.swing.CheckBoxTreeSelectionModel;
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
    private static final int PANEL_HEIGHT = 400;
    // Cached application data
    private final HashMap<String, ImageIcon> _cachedApplicationIcons = new HashMap<String, ImageIcon>();
    private final HashMap<String, String> _cachedApplicationDescriptions = new HashMap<String, String>();
    private boolean _programaticCheckingUnderway = true;
    // Tree stuff
    private static final int TREE_WIDTH = 200;
    private final DefaultMutableTreeNode _treeDataModel;
    private static final List<String> ALL = null;
    private static final String ROOT_NODE_NAME = "Root";
    private final CheckBoxTree _checkBoxTree;
    private static final int ICON_SIZE = 16;
    // Description stuff
    private static final int EDITOR_PANE_WIDTH = 400;
    private final JEditorPane _descriptionEditorPane;
    private final JScrollPane _descriptionScrollPane;

    public ApplicationListSelectionPanel() {

        super();

        _treeDataModel = populateTreeDataModel();
        _checkBoxTree = setupCheckBoxTree();
        setCheckedApplicationNames(ALL);
        // @TODO : Find why the tri-state checkboxes do not work !

        _descriptionEditorPane = setupDescriptionEditorPane();
        _descriptionScrollPane = setupDescriptionScroolPane();

        setLayout(new BorderLayout());
        add(new JScrollPane(_checkBoxTree), BorderLayout.WEST);
        add(_descriptionEditorPane, BorderLayout.CENTER);
    }

    private DefaultMutableTreeNode populateTreeDataModel() {

        DefaultMutableTreeNode rootNode = new DefaultMutableTreeNode(ROOT_NODE_NAME);

        // For each known application category
        for (Category applicationCategory : Category.values()) {

            final String categoryName = applicationCategory.value();
            DefaultMutableTreeNode categoryNode = new DefaultMutableTreeNode(categoryName);
            rootNode.add(categoryNode);
            _logger.trace("Loading applications for category '" + categoryName + "':");

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
                _logger.trace("\t- found application '" + applicationName + "' with icon.");
            }
        }

        return rootNode;
    }

    private CheckBoxTree setupCheckBoxTree() {

        CheckBoxTree checkBoxTree = new CheckBoxTree(_treeDataModel) {

            @Override
            public Dimension getPreferredScrollableViewportSize() {
                return new Dimension(TREE_WIDTH, HEIGHT);
            }
        };

        checkBoxTree.setRootVisible(false); // Hide root node
        checkBoxTree.setShowsRootHandles(true); // Node handles should be displayed

        // Restrict row selection to one item at a time
        DefaultTreeSelectionModel treeSelectionModel = new DefaultTreeSelectionModel();
        treeSelectionModel.setSelectionMode(TreeSelectionModel.SINGLE_TREE_SELECTION);
        checkBoxTree.setSelectionModel(treeSelectionModel);

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
                    String selectedApplicationName = node.getUserObject().toString();

                    _logger.debug("Application '" + selectedApplicationName + "' is selected.");

                    // Fill information pane accordinaly
                    fillApplicationDescriptionPane(selectedApplicationName);
                }
            }
        });
    }

    private void listenToChecks(final CheckBoxTree tree) {

        tree.getCheckBoxTreeSelectionModel().addTreeSelectionListener(new TreeSelectionListener() {

            @Override
            public void valueChanged(TreeSelectionEvent e) {

                List<String> checkedApplicationList = new ArrayList<String>();

                // Get the currently checked paths list
                TreePath[] checkedPaths = tree.getCheckBoxTreeSelectionModel().getSelectionPaths();
                if (checkedPaths != null) {

                    // For each checked box
                    for (TreePath checkedPath : checkedPaths) {

                        final Object checkedBox = checkedPath.getLastPathComponent();
                        final String checkedBoxName = checkedBox.toString();
                        _logger.debug("Discovered that '" + checkedBoxName + "' is checked.");

                        // If the current checked box is not a leaf
                        DefaultTreeModel treeModel = new DefaultTreeModel(_treeDataModel);
                        if (!treeModel.isLeaf(checkedBox)) {

                            _logger.trace("But '" + checkedBoxName + "' is NOT a LEAF - Going deeper :");

                            // Get all the applications in the current 'directory' node
                            for (Category category : Category.values()) {

                                // List category applications (or all applications if ROOT node)
                                if ((checkedBoxName.equals(ROOT_NODE_NAME)) || (checkedBoxName.equals(category.value()))) {

                                    // Retrieve all the current category visible applications
                                    for (String applicationName : StubRegistry.getCategoryVisibleApplicationNames(category)) {

                                        _logger.trace("\t- made of visible application = " + applicationName);
                                        checkedApplicationList.add(applicationName);
                                    }
                                }
                            }
                        } else {
                            checkedApplicationList.add(checkedBoxName);
                        }
                    }
                } else {
                    _logger.debug("Discovered that NOTHING is checked.");

                }

                if (!_programaticCheckingUnderway) {
                    checkedApplicationChanged(checkedApplicationList);
                }
            }
        });
    }

    protected void checkedApplicationChanged(List<String> checkedApplicationList) {
        System.out.println("Selected applications : " + CollectionUtils.toString(checkedApplicationList, ", ", "{", "}") + ".");
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
                _logger.trace("Rendered '" + applicationName + "' application icon.");
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

    private JScrollPane setupDescriptionScroolPane() {

        JScrollPane descriptionScrollPane = new JScrollPane();

        descriptionScrollPane.add(_descriptionEditorPane);
        final int minimumEditorPaneWidth = _descriptionEditorPane.getMinimumSize().width;
        final int minimumEditorPaneHeight = _descriptionEditorPane.getMinimumSize().height;

        // Try not to display windows bigger than screen for huge messages
        // 40px margins are here to avoid some scrollbars...
        final int maximumScrollPaneWidth = Math.min(minimumEditorPaneWidth + 50, 500);
        final int maximumScrollPaneHeight = Math.min(minimumEditorPaneHeight + 50, 100);
        final Dimension maximumScrollPaneDimension = new Dimension(maximumScrollPaneWidth, maximumScrollPaneHeight);
        descriptionScrollPane.setMaximumSize(maximumScrollPaneDimension);
        descriptionScrollPane.setPreferredSize(maximumScrollPaneDimension);

        // Ensure background color consistency
        boolean editorPaneBackgroundShouldBeOpaque = (minimumEditorPaneWidth > maximumScrollPaneWidth) || (minimumEditorPaneHeight > maximumScrollPaneHeight);
        _descriptionEditorPane.setOpaque(editorPaneBackgroundShouldBeOpaque);
        descriptionScrollPane.setOpaque(editorPaneBackgroundShouldBeOpaque);
        descriptionScrollPane.getViewport().setOpaque(editorPaneBackgroundShouldBeOpaque);
        if (!editorPaneBackgroundShouldBeOpaque) {
            descriptionScrollPane.setBorder(null);
        }

        return descriptionScrollPane;
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
            String applicationMetaDataResourcePath = StubRegistry.getApplicationResourcePath(applicationName);
            _logger.trace("Loading '" + applicationName + "' meta data from path '" + applicationMetaDataResourcePath + "':");
            SampStub applicationData = SampApplicationMetaData.loadSampSubFromResourcePath(applicationMetaDataResourcePath);
            HashMap<String, String> metaDataMap = new HashMap<String, String>();
            for (Metadata applicationMetaData : applicationData.getMetadatas()) {
                final String metaDataKey = applicationMetaData.getKey();
                final String metaDataValue = applicationMetaData.getValue();
                metaDataMap.put(metaDataKey, metaDataValue);
                _logger.trace("\t- found meta data ['" + metaDataKey + "' -> '" + metaDataValue + "'].");
            }

            // HTML generation
            _logger.debug("Generating HTML for '" + applicationName + "' aplication:");
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
                _logger.trace("\t- found meta data for '" + label + "' : '" + value + "'.");
            }

            generatedHtml.append("</BODY></HTML>");

            // Cache application description for later use
            applicationDescription = generatedHtml.toString();
            _cachedApplicationDescriptions.put(applicationName, applicationDescription);
        } else {
            _logger.debug("Retrieved cached '" + applicationName + "' application description.");
        }

        // Set application description in editor pane
        _descriptionEditorPane.setText(applicationDescription);

        // Show first line of editor pane, and not its last line as by default !
        _descriptionEditorPane.setCaretPosition(0);
    }

    /** @return the list of application names selected by the user */
    public final List<String> getCheckedApplicationNames() {
        List<String> selectedApplicationNames = new ArrayList<String>();

        for (TreePath selectedPath : _checkBoxTree.getCheckBoxTreeSelectionModel().getSelectionPaths()) {
            final String applicationName = selectedPath.getLastPathComponent().toString();
            selectedApplicationNames.add(applicationName);
        }

        return selectedApplicationNames;
    }

    /** @applicationNames the list of application names to select, or null for all. */
    public final void setCheckedApplicationNames(List<String> applicationNames) {

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
                _logger.debug("Checked '" + currentRowApplicationName + "' application.");
            } else {
                checkBoxTreeSelectionModel.removeSelectionPath(pathForCurrentRow);
                _logger.trace("Unchecked '" + currentRowApplicationName + "' application.");
            }
        }

        _programaticCheckingUnderway = false;
    }

    public static void main(String[] args) {
        JFrame frame = new JFrame();
        final ApplicationListSelectionPanel applicationListSelectionPanel = new ApplicationListSelectionPanel();
        frame.add(applicationListSelectionPanel);
        frame.pack();
        frame.setVisible(true);

        try {
            Thread.sleep(5000);
        } catch (InterruptedException ex) {
            _logger.error("", ex);
        }

        List<String> specificApplicationNames = new ArrayList<String>();
        specificApplicationNames.add("Aspro2");
        specificApplicationNames.add("SearchCal");
        specificApplicationNames.add("topcat");
        applicationListSelectionPanel.setCheckedApplicationNames(specificApplicationNames);
    }
}
