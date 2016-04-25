/*
 * The contents of this file are subject to the terms of the Common Development and
 * Distribution License (the License). You may not use this file except in compliance with the
 * License.
 *
 * You can obtain a copy of the License at legal/CDDLv1.0.txt. See the License for the
 * specific language governing permission and limitations under the License.
 *
 * When distributing Covered Software, include this CDDL Header Notice in each file and include
 * the License file at legal/CDDLv1.0.txt. If applicable, add the following below the CDDL
 * Header, with the fields enclosed by brackets [] replaced by your own identifying
 * information: "Portions Copyright [year] [name of copyright owner]".
 *
 * Copyright 2008-2010 Sun Microsystems, Inc.
 * Portions Copyright 2011-2016 ForgeRock AS.
 */
package org.opends.guitools.controlpanel.ui;

import static com.forgerock.opendj.cli.Utils.*;

import static org.opends.messages.AdminToolMessages.*;
import static org.opends.messages.QuickSetupMessages.*;

import java.awt.Component;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Window;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.net.URI;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

import javax.naming.NamingException;
import javax.naming.ldap.InitialLdapContext;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.DefaultComboBoxModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.JSeparator;
import javax.swing.JTree;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import javax.swing.event.TreeModelEvent;
import javax.swing.event.TreeModelListener;
import javax.swing.tree.DefaultMutableTreeNode;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeNode;
import javax.swing.tree.TreePath;

import org.forgerock.i18n.LocalizableMessage;
import org.forgerock.i18n.LocalizableMessageBuilder;
import org.forgerock.i18n.slf4j.LocalizedLogger;
import org.forgerock.opendj.ldap.ByteString;
import org.forgerock.opendj.ldap.DN;
import org.forgerock.opendj.ldap.schema.AttributeType;
import org.opends.admin.ads.util.ApplicationTrustManager;
import org.opends.admin.ads.util.ConnectionUtils;
import org.opends.guitools.controlpanel.browser.BrowserController;
import org.opends.guitools.controlpanel.datamodel.BackendDescriptor;
import org.opends.guitools.controlpanel.datamodel.BaseDNDescriptor;
import org.opends.guitools.controlpanel.datamodel.CategorizedComboBoxElement;
import org.opends.guitools.controlpanel.datamodel.ConfigReadException;
import org.opends.guitools.controlpanel.datamodel.ControlPanelInfo;
import org.opends.guitools.controlpanel.datamodel.IndexDescriptor;
import org.opends.guitools.controlpanel.datamodel.ServerDescriptor;
import org.opends.guitools.controlpanel.event.BackendPopulatedEvent;
import org.opends.guitools.controlpanel.event.BackendPopulatedListener;
import org.opends.guitools.controlpanel.event.BrowserEvent;
import org.opends.guitools.controlpanel.event.BrowserEventListener;
import org.opends.guitools.controlpanel.event.ConfigurationChangeEvent;
import org.opends.guitools.controlpanel.ui.components.FilterTextField;
import org.opends.guitools.controlpanel.ui.components.TreePanel;
import org.opends.guitools.controlpanel.ui.nodes.BasicNode;
import org.opends.guitools.controlpanel.ui.renderer.CustomListCellRenderer;
import org.opends.guitools.controlpanel.util.Utilities;
import org.opends.quicksetup.UserDataCertificateException;
import org.opends.quicksetup.ui.CertificateDialog;
import org.opends.quicksetup.util.UIKeyStore;
import org.opends.server.protocols.ldap.LDAPFilter;
import org.opends.server.types.DirectoryException;
import org.opends.server.types.LDAPException;
import org.opends.server.types.SearchFilter;
import org.opends.server.util.ServerConstants;

/**
 * The abstract class used to refactor some code. The classes that extend this
 * class are the 'Browse Entries' panel and the panel of the dialog we display
 * when the user can choose a set of entries (for instance when the user adds a
 * member to a group in the 'New Group' dialog).
 */
abstract class AbstractBrowseEntriesPanel extends StatusGenericPanel implements BackendPopulatedListener
{
  private static final long serialVersionUID = -6063927039968115236L;
  private static final LocalizedLogger logger = LocalizedLogger.getLoggerForThisClass();

  /** LDAP filter message. */
  protected static final LocalizableMessage LDAP_FILTER = INFO_CTRL_PANEL_LDAP_FILTER.get();
  /** User filter message. */
  protected static final LocalizableMessage USER_FILTER = INFO_CTRL_PANEL_USERS_FILTER.get();
  /** Group filter message. */
  protected static final LocalizableMessage GROUP_FILTER = INFO_CTRL_PANEL_GROUPS_FILTER.get();
  private static final LocalizableMessage OTHER_BASE_DN = INFO_CTRL_PANEL_OTHER_BASE_DN.get();

  private static final String ALL_BASE_DNS = "All Base DNs";
  private static final int MAX_NUMBER_ENTRIES = 5000;
  private static final int MAX_NUMBER_OTHER_BASE_DNS = 10;
  private static final String[] CONTAINER_CLASSES = { "organization", "organizationalUnit" };
  private static final String[] SYSTEM_INDEXES =
    { "aci", "dn2id", "ds-sync-hist", "entryUUID", "id2children", "id2subtree" };


  private JComboBox<String> baseDNs;

  /** The combo box containing the different filter types. */
  protected JComboBox<CharSequence> filterAttribute;
  /** The text field of the filter. */
  protected FilterTextField filter;

  private JButton applyButton;
  private JButton okButton;
  private JButton cancelButton;
  private JButton closeButton;

  private JLabel lBaseDN;
  private JLabel lFilter;
  private JLabel lLimit;
  private JLabel lNumberOfEntries;
  private JLabel lNoMatchFound;

  private InitialLdapContext createdUserDataCtx;
  /** The tree pane contained in this panel. */
  protected TreePanel treePane;
  /** The browser controller used to update the LDAP entry tree. */
  protected BrowserController controller;
  private NumberOfEntriesUpdater numberEntriesUpdater;
  private BaseDNPanel otherBaseDNPanel;
  private GenericDialog otherBaseDNDlg;
  private boolean firstTimeDisplayed = true;
  private Object lastSelectedBaseDN;
  private boolean ignoreBaseDNEvents;

  private final List<DN> otherBaseDns = new ArrayList<>();

  /** Default constructor. */
  public AbstractBrowseEntriesPanel()
  {
    super();
    createLayout();
  }

  @Override
  public boolean requiresBorder()
  {
    return false;
  }

  @Override
  public boolean requiresScroll()
  {
    return false;
  }

  @Override
  public boolean callConfigurationChangedInBackground()
  {
    return true;
  }

  @Override
  public void setInfo(ControlPanelInfo info)
  {
    if (controller == null)
    {
      createBrowserController(info);
    }
    super.setInfo(info);
    treePane.setInfo(info);
    info.addBackendPopulatedListener(this);
  }

  @Override
  public final GenericDialog.ButtonType getButtonType()
  {
    return GenericDialog.ButtonType.NO_BUTTON;
  }

  /**
   * Since these panel has a special layout, we cannot use the layout of the
   * GenericDialog and we return ButtonType.NO_BUTTON in the method
   * getButtonType. We use this method to be able to add some progress
   * information to the left of the buttons.
   *
   * @return the button type of the panel.
   */
  protected abstract GenericDialog.ButtonType getBrowseButtonType();

  @Override
  public void toBeDisplayed(boolean visible)
  {
    super.toBeDisplayed(visible);
    Window w = Utilities.getParentDialog(this);
    if (w instanceof GenericDialog)
    {
      ((GenericDialog) w).getRootPane().setDefaultButton(null);
    }
    else if (w instanceof GenericFrame)
    {
      ((GenericFrame) w).getRootPane().setDefaultButton(null);
    }
  }

  @Override
  protected void setEnabledOK(boolean enable)
  {
    okButton.setEnabled(enable);
  }

  @Override
  protected void setEnabledCancel(boolean enable)
  {
    cancelButton.setEnabled(enable);
  }

  /** Creates the layout of the panel (but the contents are not populated here). */
  @SuppressWarnings("unchecked")
  private void createLayout()
  {
    setBackground(ColorAndFontConstants.greyBackground);
    GridBagConstraints gbc = new GridBagConstraints();
    gbc.anchor = GridBagConstraints.WEST;
    gbc.gridx = 0;
    gbc.gridy = 0;
    gbc.gridwidth = 7;
    addErrorPane(gbc);
    LocalizableMessage title = INFO_CTRL_PANEL_SERVER_NOT_RUNNING_SUMMARY.get();
    LocalizableMessageBuilder mb = new LocalizableMessageBuilder();
    mb.append(INFO_CTRL_PANEL_SERVER_NOT_RUNNING_DETAILS.get());
    mb.append("<br><br>");
    mb.append(getStartServerHTML());
    LocalizableMessage details = mb.toMessage();
    updateErrorPane(errorPane, title, ColorAndFontConstants.errorTitleFont, details, ColorAndFontConstants.defaultFont);
    errorPane.setVisible(true);
    errorPane.setFocusable(true);

    gbc.insets = new Insets(10, 10, 0, 10);
    gbc.gridy++;
    gbc.gridwidth = 1;
    gbc.weightx = 0;
    gbc.fill = GridBagConstraints.NONE;
    lBaseDN = Utilities.createPrimaryLabel(INFO_CTRL_PANEL_BASE_DN_LABEL.get());
    gbc.gridx = 0;
    gbc.fill = GridBagConstraints.HORIZONTAL;
    gbc.insets.right = 0;
    add(lBaseDN, gbc);
    gbc.insets.left = 5;
    baseDNs = Utilities.createComboBox();

    DefaultComboBoxModel<String> model = new DefaultComboBoxModel<>();
    model.addElement("dc=dn to be displayed");
    baseDNs.setModel(model);
    baseDNs.setRenderer(new CustomComboBoxCellRenderer(baseDNs));
    baseDNs.addItemListener(new ItemListener()
    {
      @SuppressWarnings("rawtypes")
      @Override
      public void itemStateChanged(ItemEvent ev)
      {
        if (ignoreBaseDNEvents || ev.getStateChange() != ItemEvent.SELECTED)
        {
          return;
        }
        Object o = baseDNs.getSelectedItem();
        if (isCategory(o))
        {
          if (lastSelectedBaseDN == null)
          {
            // Look for the first element that is not a category
            for (int i = 0; i < baseDNs.getModel().getSize(); i++)
            {
              Object item = baseDNs.getModel().getElementAt(i);
              if (item instanceof CategorizedComboBoxElement && !isCategory(item))
              {
                lastSelectedBaseDN = item;
                break;
              }
            }
            if (lastSelectedBaseDN != null)
            {
              baseDNs.setSelectedItem(lastSelectedBaseDN);
            }
          }
          else
          {
            ignoreBaseDNEvents = true;
            baseDNs.setSelectedItem(lastSelectedBaseDN);
            ignoreBaseDNEvents = false;
          }
        }
        else if (COMBO_SEPARATOR.equals(o))
        {
          ignoreBaseDNEvents = true;
          baseDNs.setSelectedItem(lastSelectedBaseDN);
          ignoreBaseDNEvents = false;
        }
        else if (!OTHER_BASE_DN.equals(o))
        {
          lastSelectedBaseDN = o;
          if (lastSelectedBaseDN != null)
          {
            applyButtonClicked();
          }
        }
        else
        {
          if (otherBaseDNDlg == null)
          {
            otherBaseDNPanel = new BaseDNPanel();
            otherBaseDNDlg = new GenericDialog(Utilities.getFrame(AbstractBrowseEntriesPanel.this), otherBaseDNPanel);
            otherBaseDNDlg.setModal(true);
            Utilities.centerGoldenMean(otherBaseDNDlg, Utilities.getParentDialog(AbstractBrowseEntriesPanel.this));
          }
          otherBaseDNDlg.setVisible(true);
          String newBaseDn = otherBaseDNPanel.getBaseDn();
          DefaultComboBoxModel model = (DefaultComboBoxModel) baseDNs.getModel();
          if (newBaseDn != null)
          {
            CategorizedComboBoxElement newElement = null;

            try
            {
              DN dn = DN.valueOf(newBaseDn);
              newElement =
                  new CategorizedComboBoxElement(Utilities.unescapeUtf8(dn.toString()),
                      CategorizedComboBoxElement.Type.REGULAR);
              if (!otherBaseDns.contains(dn))
              {
                otherBaseDns.add(0, dn);

                if (otherBaseDns.size() > MAX_NUMBER_OTHER_BASE_DNS)
                {
                  ignoreBaseDNEvents = true;
                  for (int i = otherBaseDns.size() - 1; i >= MAX_NUMBER_OTHER_BASE_DNS; i--)
                  {
                    DN dnToRemove = otherBaseDns.get(i);
                    otherBaseDns.remove(i);
                    Object elementToRemove =
                        new CategorizedComboBoxElement(Utilities.unescapeUtf8(dnToRemove.toString()),
                            CategorizedComboBoxElement.Type.REGULAR);
                    model.removeElement(elementToRemove);
                  }
                  ignoreBaseDNEvents = false;
                }
              }
              if (model.getIndexOf(newElement) == -1)
              {
                int index = model.getIndexOf(COMBO_SEPARATOR);
                model.insertElementAt(newElement, index + 1);
                if (otherBaseDns.size() == 1)
                {
                  model.insertElementAt(COMBO_SEPARATOR, index + 2);
                }
              }
            }
            catch (Throwable t)
            {
              throw new RuntimeException("Unexpected error decoding dn " + newBaseDn, t);
            }

            model.setSelectedItem(newElement);
          }
          else if (lastSelectedBaseDN != null)
          {
            ignoreBaseDNEvents = true;
            model.setSelectedItem(lastSelectedBaseDN);
            ignoreBaseDNEvents = false;
          }
        }
      }
    });
    gbc.gridx++;
    add(baseDNs, gbc);

    gbc.gridx++;
    gbc.fill = GridBagConstraints.VERTICAL;
    gbc.insets.left = 10;
    add(new JSeparator(SwingConstants.VERTICAL), gbc);
    gbc.fill = GridBagConstraints.HORIZONTAL;
    lFilter = Utilities.createPrimaryLabel(INFO_CTRL_PANEL_FILTER_LABEL.get());
    gbc.gridx++;
    add(lFilter, gbc);

    filterAttribute = Utilities.createComboBox();
    filterAttribute.setModel(new DefaultComboBoxModel<CharSequence>(new CharSequence[] {
      USER_FILTER, GROUP_FILTER, COMBO_SEPARATOR, "attributetobedisplayed", COMBO_SEPARATOR, LDAP_FILTER }));
    filterAttribute.setRenderer(new CustomListCellRenderer(filterAttribute));
    filterAttribute.addItemListener(new IgnoreItemListener(filterAttribute));
    gbc.gridx++;
    gbc.insets.left = 5;
    add(filterAttribute, gbc);

    filter = new FilterTextField();
    filter.setToolTipText(INFO_CTRL_PANEL_SUBSTRING_SEARCH_INLINE_HELP.get().toString());
    filter.addKeyListener(new KeyAdapter()
    {
      @Override
      public void keyReleased(KeyEvent e)
      {
        if (e.getKeyCode() == KeyEvent.VK_ENTER && applyButton.isEnabled())
        {
          filter.displayRefreshIcon(true);
          applyButtonClicked();
        }
      }
    });
    filter.addActionListener(new ActionListener()
    {
      @Override
      public void actionPerformed(ActionEvent ev)
      {
        filter.displayRefreshIcon(true);
        applyButtonClicked();
      }
    });

    gbc.weightx = 1.0;
    gbc.gridx++;
    add(filter, gbc);

    gbc.insets.top = 10;
    applyButton = Utilities.createButton(INFO_CTRL_PANEL_APPLY_BUTTON_LABEL.get());
    gbc.insets.right = 10;
    gbc.gridx++;
    gbc.weightx = 0.0;
    add(applyButton, gbc);
    applyButton.addActionListener(new ActionListener()
    {
      @Override
      public void actionPerformed(ActionEvent ev)
      {
        applyButtonClicked();
      }
    });
    gbc.insets = new Insets(10, 0, 0, 0);
    gbc.gridx = 0;
    gbc.gridy++;
    gbc.weightx = 1.0;
    gbc.weighty = 1.0;
    gbc.fill = GridBagConstraints.BOTH;
    gbc.gridwidth = 7;
    add(createMainPanel(), gbc);

    //  The button panel
    gbc.gridy++;
    gbc.weighty = 0.0;
    gbc.insets = new Insets(0, 0, 0, 0);
    add(createButtonsPanel(), gbc);
  }

  /**
   * Returns the panel that contains the buttons of type OK, CANCEL, etc.
   *
   * @return the panel that contains the buttons of type OK, CANCEL, etc.
   */
  private JPanel createButtonsPanel()
  {
    JPanel buttonsPanel = new JPanel(new GridBagLayout());
    GridBagConstraints gbc = new GridBagConstraints();
    gbc.gridx = 0;
    gbc.gridy = 0;
    gbc.anchor = GridBagConstraints.WEST;
    gbc.fill = GridBagConstraints.HORIZONTAL;
    gbc.gridwidth = 1;
    gbc.gridy = 0;
    lLimit = Utilities.createDefaultLabel();
    Utilities.setWarningLabel(lLimit, INFO_CTRL_PANEL_MAXIMUM_CHILDREN_DISPLAYED.get(MAX_NUMBER_ENTRIES));
    gbc.weighty = 0.0;
    gbc.gridy++;
    lLimit.setVisible(false);
    lNumberOfEntries = Utilities.createDefaultLabel();
    gbc.insets = new Insets(10, 10, 10, 10);
    buttonsPanel.add(lNumberOfEntries, gbc);
    buttonsPanel.add(lLimit, gbc);
    gbc.weightx = 1.0;
    gbc.gridx++;
    buttonsPanel.add(Box.createHorizontalGlue(), gbc);
    buttonsPanel.setOpaque(true);
    buttonsPanel.setBackground(ColorAndFontConstants.greyBackground);
    gbc.gridx++;
    gbc.weightx = 0.0;
    if (getBrowseButtonType() == GenericDialog.ButtonType.CLOSE)
    {
      closeButton = Utilities.createButton(INFO_CTRL_PANEL_CLOSE_BUTTON_LABEL.get());
      closeButton.setOpaque(false);
      buttonsPanel.add(closeButton, gbc);
      closeButton.addActionListener(new ActionListener()
      {
        @Override
        public void actionPerformed(ActionEvent ev)
        {
          closeClicked();
        }
      });
    }
    else if (getBrowseButtonType() == GenericDialog.ButtonType.OK)
    {
      okButton = Utilities.createButton(INFO_CTRL_PANEL_OK_BUTTON_LABEL.get());
      okButton.setOpaque(false);
      buttonsPanel.add(okButton, gbc);
      okButton.addActionListener(new ActionListener()
      {
        @Override
        public void actionPerformed(ActionEvent ev)
        {
          okClicked();
        }
      });
    }
    if (getBrowseButtonType() == GenericDialog.ButtonType.OK_CANCEL)
    {
      okButton = Utilities.createButton(INFO_CTRL_PANEL_OK_BUTTON_LABEL.get());
      okButton.setOpaque(false);
      gbc.insets.right = 0;
      buttonsPanel.add(okButton, gbc);
      okButton.addActionListener(new ActionListener()
      {
        @Override
        public void actionPerformed(ActionEvent ev)
        {
          okClicked();
        }
      });
      cancelButton = Utilities.createButton(INFO_CTRL_PANEL_CANCEL_BUTTON_LABEL.get());
      cancelButton.setOpaque(false);
      gbc.insets.right = 10;
      gbc.insets.left = 5;
      gbc.gridx++;
      buttonsPanel.add(cancelButton, gbc);
      cancelButton.addActionListener(new ActionListener()
      {
        @Override
        public void actionPerformed(ActionEvent ev)
        {
          cancelClicked();
        }
      });
    }

    buttonsPanel.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, ColorAndFontConstants.defaultBorderColor));

    return buttonsPanel;
  }

  @Override
  public Component getPreferredFocusComponent()
  {
    return baseDNs;
  }

  @Override
  public void cancelClicked()
  {
    setPrimaryValid(lBaseDN);
    setSecondaryValid(lFilter);
    super.cancelClicked();
  }

  /**
   * The method that is called when the user clicks on Apply. Basically it will
   * update the BrowserController with the new base DN and filter specified by
   * the user. The method assumes that is being called from the event thread.
   */
  protected void applyButtonClicked()
  {
    List<LocalizableMessage> errors = new ArrayList<>();
    setPrimaryValid(lFilter);
    String s = getBaseDN();
    boolean displayAll = false;
    DN theDN = null;
    if (s != null)
    {
      displayAll = ALL_BASE_DNS.equals(s);
      if (!displayAll)
      {
        try
        {
          theDN = DN.valueOf(s);
        }
        catch (Throwable t)
        {
          errors.add(INFO_CTRL_PANEL_INVALID_DN_DETAILS.get(s, t));
        }
      }
    }
    else
    {
      errors.add(INFO_CTRL_PANEL_NO_BASE_DN_SELECTED.get());
    }
    String filterValue = getFilter();
    try
    {
      LDAPFilter.decode(filterValue);
    }
    catch (LDAPException le)
    {
      errors.add(INFO_CTRL_PANEL_INVALID_FILTER_DETAILS.get(le.getMessageObject()));
      setPrimaryInvalid(lFilter);
    }
    if (errors.isEmpty())
    {
      lLimit.setVisible(false);
      lNumberOfEntries.setVisible(true);
      controller.removeAllUnderRoot();
      controller.setFilter(filterValue);
      controller.setAutomaticExpand(!BrowserController.ALL_OBJECTS_FILTER.equals(filterValue));
      SortedSet<String> allSuffixes = new TreeSet<>();
      if (controller.getConfigurationConnection() != null)
      {
        treePane.getTree().setRootVisible(displayAll);
        treePane.getTree().setShowsRootHandles(!displayAll);
        boolean added = false;
        for (BackendDescriptor backend : getInfo().getServerDescriptor().getBackends())
        {
          for (BaseDNDescriptor baseDN : backend.getBaseDns())
          {
            boolean isBaseDN = baseDN.getDn().equals(theDN);
            String dn = Utilities.unescapeUtf8(baseDN.getDn().toString());
            if (displayAll)
            {
              allSuffixes.add(dn);
            }
            else if (isBaseDN)
            {
              controller.addSuffix(dn, null);
              added = true;
            }
          }
        }
        if (displayAll)
        {
          allSuffixes.add(ServerConstants.DN_EXTERNAL_CHANGELOG_ROOT);
          for (String dn : allSuffixes)
          {
            controller.addSuffix(dn, null);
          }
        }
        else if (!added && !displayAll)
        {
          if (isChangeLog(theDN))
          {
            // Consider it a suffix
            controller.addSuffix(s, null);
          }
          else
          {
            BasicNode rootNode = (BasicNode) controller.getTree().getModel().getRoot();
            if (controller.findChildNode(rootNode, s) == -1)
            {
              controller.addNodeUnderRoot(s);
            }
          }
        }
      }
      else
      {
        controller.getTree().setRootVisible(false);
        controller.removeAllUnderRoot();
      }
    }
    else
    {
      displayErrorDialog(errors);
    }
  }

  private boolean isChangeLog(DN theDN)
  {
    try
    {
      return theDN.equals(DN.valueOf(ServerConstants.DN_EXTERNAL_CHANGELOG_ROOT));
    }
    catch (Throwable t)
    {
      // Bug
      t.printStackTrace();
      return false;
    }
  }

  /**
   * Returns the LDAP filter built based in the parameters provided by the user.
   *
   * @return the LDAP filter built based in the parameters provided by the user.
   */
  private String getFilter()
  {
    String filterText = filter.getText();
    if (filterText.length() == 0)
    {
      return BrowserController.ALL_OBJECTS_FILTER;
    }

    Object attr = filterAttribute.getSelectedItem();
    if (LDAP_FILTER.equals(attr))
    {
      filterText = filterText.trim();
      if (filterText.length() == 0)
      {
        return BrowserController.ALL_OBJECTS_FILTER;
      }

      return filterText;
    }
    else if (USER_FILTER.equals(attr))
    {
      if ("*".equals(filterText))
      {
        return "(objectClass=person)";
      }

      return "(&(objectClass=person)(|" + "(cn=" + filterText + ")(sn=" + filterText + ")(uid=" + filterText + ")))";
    }
    else if (GROUP_FILTER.equals(attr))
    {
      if ("*".equals(filterText))
      {
        return "(|(objectClass=groupOfUniqueNames)(objectClass=groupOfURLs))";
      }

      return "(&(|(objectClass=groupOfUniqueNames)(objectClass=groupOfURLs))" + "(cn=" + filterText + "))";
    }
    else if (attr != null)
    {
      try
      {
        return new LDAPFilter(SearchFilter.createFilterFromString("(" + attr + "=" + filterText + ")")).toString();
      }
      catch (DirectoryException de)
      {
        // Try this alternative:
        AttributeType attrType =
            getInfo().getServerDescriptor().getSchema().getAttributeType(attr.toString().toLowerCase());
        ByteString filterBytes = ByteString.valueOfUtf8(filterText);
        return new LDAPFilter(SearchFilter.createEqualityFilter(attrType, filterBytes)).toString();
      }
    }
    else
    {
      return BrowserController.ALL_OBJECTS_FILTER;
    }
  }

  /**
   * Returns the component that will be displayed between the filtering options
   * and the buttons panel. This component must contain the tree panel.
   *
   * @return the component that will be displayed between the filtering options
   *         and the buttons panel.
   */
  protected abstract Component createMainPanel();

  @Override
  public void backendPopulated(BackendPopulatedEvent ev)
  {
    if (controller.getConfigurationConnection() != null)
    {
      boolean displayAll = false;
      boolean errorOccurred = false;
      DN theDN = null;
      String s = getBaseDN();
      if (s != null)
      {
        displayAll = ALL_BASE_DNS.equals(s);
        if (!displayAll)
        {
          try
          {
            theDN = DN.valueOf(s);
          }
          catch (Throwable t)
          {
            errorOccurred = true;
          }
        }
      }
      else
      {
        errorOccurred = true;
      }
      if (!errorOccurred)
      {
        treePane.getTree().setRootVisible(displayAll);
        treePane.getTree().setShowsRootHandles(!displayAll);
        BasicNode rootNode = (BasicNode) controller.getTree().getModel().getRoot();
        boolean isSubordinate = false;
        for (BackendDescriptor backend : ev.getBackends())
        {
          for (BaseDNDescriptor baseDN : backend.getBaseDns())
          {
            boolean isBaseDN = false;
            if (baseDN.getDn().equals(theDN))
            {
              isBaseDN = true;
            }
            else if (baseDN.getDn().isSuperiorOrEqualTo(theDN))
            {
              isSubordinate = true;
            }
            String dn = Utilities.unescapeUtf8(baseDN.getDn().toString());
            if (displayAll || isBaseDN)
            {
              try
              {
                if (!controller.hasSuffix(dn))
                {
                  controller.addSuffix(dn, null);
                }
                else
                {
                  int index = controller.findChildNode(rootNode, dn);
                  if (index >= 0)
                  {
                    TreeNode node = rootNode.getChildAt(index);
                    if (node != null)
                    {
                      TreePath path = new TreePath(controller.getTreeModel().getPathToRoot(node));
                      controller.startRefresh(controller.getNodeInfoFromPath(path));
                    }
                  }
                }
              }
              catch (IllegalArgumentException iae)
              {
                // The suffix node exists but is not a suffix node. Simply log a message.
                logger.warn(
                    LocalizableMessage.raw("Suffix: " + dn + " added as a non suffix node. Exception: " + iae, iae));
              }
            }
          }
        }
        if (isSubordinate && controller.findChildNode(rootNode, s) == -1)
        {
          controller.addNodeUnderRoot(s);
        }
      }
    }
  }

  @Override
  public void configurationChanged(ConfigurationChangeEvent ev)
  {
    final ServerDescriptor desc = ev.getNewDescriptor();

    updateCombos(desc);
    updateBrowserControllerAndErrorPane(desc);
  }

  /**
   * Creates and returns the tree panel.
   *
   * @return the tree panel.
   */
  protected JComponent createTreePane()
  {
    treePane = new TreePanel();

    lNoMatchFound = Utilities.createDefaultLabel(INFO_CTRL_PANEL_NO_MATCHES_FOUND_LABEL.get());
    lNoMatchFound.setVisible(false);

    // Calculate default size
    JTree tree = treePane.getTree();
    DefaultMutableTreeNode root = new DefaultMutableTreeNode("myserver.mydomain.com:389");
    DefaultTreeModel model = new DefaultTreeModel(root);
    tree.setModel(model);
    tree.setShowsRootHandles(false);
    tree.expandPath(new TreePath(root));
    JPanel p = new JPanel(new GridBagLayout());
    p.setBackground(ColorAndFontConstants.background);
    GridBagConstraints gbc = new GridBagConstraints();
    gbc.gridx = 0;
    gbc.gridy = 0;
    gbc.gridwidth = 1;
    gbc.anchor = GridBagConstraints.NORTHWEST;
    gbc.fill = GridBagConstraints.BOTH;
    gbc.weightx = 1.0;
    gbc.weighty = 1.0;
    Utilities.setBorder(treePane, new EmptyBorder(10, 0, 10, 0));
    p.add(treePane, gbc);
    gbc.fill = GridBagConstraints.HORIZONTAL;
    Utilities.setBorder(lNoMatchFound, new EmptyBorder(15, 15, 15, 15));
    p.add(lNoMatchFound, gbc);

    if (getInfo() != null && controller == null)
    {
      createBrowserController(getInfo());
    }
    numberEntriesUpdater = new NumberOfEntriesUpdater();
    numberEntriesUpdater.start();

    return p;
  }

  /**
   * Creates the browser controller object.
   *
   * @param info
   *          the ControlPanelInfo to be used to create the browser controller.
   */
  protected void createBrowserController(ControlPanelInfo info)
  {
    controller = new BrowserController(treePane.getTree(), info.getConnectionPool(), info.getIconPool());
    controller.setContainerClasses(CONTAINER_CLASSES);
    controller.setShowContainerOnly(false);
    controller.setMaxChildren(MAX_NUMBER_ENTRIES);
    controller.addBrowserEventListener(new BrowserEventListener()
    {
      @Override
      public void processBrowserEvent(BrowserEvent ev)
      {
        if (ev.getType() == BrowserEvent.Type.SIZE_LIMIT_REACHED)
        {
          lLimit.setVisible(true);
          lNumberOfEntries.setVisible(false);
        }
      }
    });
    controller.getTreeModel().addTreeModelListener(new TreeModelListener()
    {
      @Override
      public void treeNodesChanged(TreeModelEvent e)
      {
        // no-op
      }

      @Override
      public void treeNodesInserted(TreeModelEvent e)
      {
        checkRootNode();
      }

      @Override
      public void treeNodesRemoved(TreeModelEvent e)
      {
        checkRootNode();
      }

      @Override
      public void treeStructureChanged(TreeModelEvent e)
      {
        checkRootNode();
      }
    });
  }


  private static boolean displayIndex(String name)
  {
    for (String systemIndex : SYSTEM_INDEXES)
    {
      if (systemIndex.equalsIgnoreCase(name))
      {
        return false;
      }
    }
    return true;
  }

  /**
   * Updates the contents of the combo boxes with the provided ServerDescriptor.
   *
   * @param desc
   *          the server descriptor to be used to update the combo boxes.
   */
  @SuppressWarnings("rawtypes")
  private void updateCombos(ServerDescriptor desc)
  {
    final SortedSet<String> newElements = new TreeSet<>();
    for (BackendDescriptor backend : desc.getBackends())
    {
      for (IndexDescriptor index : backend.getIndexes())
      {
        String indexName = index.getName();
        if (displayIndex(indexName))
        {
          newElements.add(indexName);
        }
      }
    }

    final DefaultComboBoxModel<CharSequence> model = (DefaultComboBoxModel<CharSequence>) filterAttribute.getModel();
    if (hasChanged(newElements, model))
    {
      SwingUtilities.invokeLater(new Runnable()
      {
        @Override
        public void run()
        {
          Object selected = filterAttribute.getSelectedItem();
          model.removeAllElements();
          model.addElement(USER_FILTER);
          model.addElement(GROUP_FILTER);
          model.addElement(COMBO_SEPARATOR);
          for (String newElement : newElements)
          {
            model.addElement(newElement);
          }
          // If there are not backends, we get no indexes to set.
          if (!newElements.isEmpty())
          {
            model.addElement(COMBO_SEPARATOR);
          }
          model.addElement(LDAP_FILTER);
          if (selected != null)
          {
            if (model.getIndexOf(selected) != -1)
            {
              model.setSelectedItem(selected);
            }
            else
            {
              model.setSelectedItem(model.getElementAt(0));
            }
          }
        }
      });
    }

    Set<Object> baseDNNewElements = new LinkedHashSet<>();
    SortedSet<String> backendIDs = new TreeSet<>();
    Map<String, SortedSet<String>> hmBaseDNs = new HashMap<>();

    Map<String, BaseDNDescriptor> hmBaseDNWithEntries = new HashMap<>();

    BaseDNDescriptor baseDNWithEntries = null;
    for (BackendDescriptor backend : desc.getBackends())
    {
      if (displayBackend(backend))
      {
        String backendID = backend.getBackendID();
        backendIDs.add(backendID);
        SortedSet<String> someBaseDNs = new TreeSet<>();
        for (BaseDNDescriptor baseDN : backend.getBaseDns())
        {
          try
          {
            someBaseDNs.add(Utilities.unescapeUtf8(baseDN.getDn().toString()));
          }
          catch (Throwable t)
          {
            throw new RuntimeException("Unexpected error: " + t, t);
          }
          if (baseDN.getEntries() > 0)
          {
            hmBaseDNWithEntries.put(Utilities.unescapeUtf8(baseDN.getDn().toString()), baseDN);
          }
        }
        hmBaseDNs.put(backendID, someBaseDNs);
        if ("userRoot".equalsIgnoreCase(backendID))
        {
          for (String baseDN : someBaseDNs)
          {
            baseDNWithEntries = hmBaseDNWithEntries.get(baseDN);
            if (baseDNWithEntries != null)
            {
              break;
            }
          }
        }
      }
    }

    baseDNNewElements.add(new CategorizedComboBoxElement(ALL_BASE_DNS, CategorizedComboBoxElement.Type.REGULAR));
    for (String backendID : backendIDs)
    {
      baseDNNewElements.add(new CategorizedComboBoxElement(backendID, CategorizedComboBoxElement.Type.CATEGORY));
      SortedSet<String> someBaseDNs = hmBaseDNs.get(backendID);
      for (String baseDN : someBaseDNs)
      {
        baseDNNewElements.add(new CategorizedComboBoxElement(baseDN, CategorizedComboBoxElement.Type.REGULAR));
        if (baseDNWithEntries == null)
        {
          baseDNWithEntries = hmBaseDNWithEntries.get(baseDN);
        }
      }
    }
    for (DN dn : otherBaseDns)
    {
      baseDNNewElements.add(COMBO_SEPARATOR);
      baseDNNewElements.add(new CategorizedComboBoxElement(
          Utilities.unescapeUtf8(dn.toString()), CategorizedComboBoxElement.Type.REGULAR));
    }
    baseDNNewElements.add(COMBO_SEPARATOR);
    baseDNNewElements.add(OTHER_BASE_DN);

    if (firstTimeDisplayed && baseDNWithEntries != null)
    {
      ignoreBaseDNEvents = true;
    }
    updateComboBoxModel(baseDNNewElements, (DefaultComboBoxModel) baseDNs.getModel());
    // Select the element in the combo box.
    if (firstTimeDisplayed && baseDNWithEntries != null)
    {
      final Object toSelect = new CategorizedComboBoxElement(
          Utilities.unescapeUtf8(baseDNWithEntries.getDn().toString()), CategorizedComboBoxElement.Type.REGULAR);
      SwingUtilities.invokeLater(new Runnable()
      {
        @Override
        public void run()
        {
          // After this updateBrowseController is called.
          ignoreBaseDNEvents = true;
          baseDNs.setSelectedItem(toSelect);
          ignoreBaseDNEvents = false;
        }
      });
    }
    if (getInfo().getServerDescriptor().isAuthenticated())
    {
      firstTimeDisplayed = false;
    }
  }

  private boolean hasChanged(final SortedSet<String> newElements, final DefaultComboBoxModel<CharSequence> model)
  {
    if (newElements.size() != model.getSize() - 2)
    {
      return true;
    }

    int i = 0;
    for (String newElement : newElements)
    {
      if (!newElement.equals(model.getElementAt(i)))
      {
        return true;
      }
      i++;
    }
    return false;
  }

  /**
   * Updates the contents of the error pane and the browser controller with the
   * provided ServerDescriptor. It checks that the server is running and that we
   * are authenticated, that the connection to the server has not changed, etc.
   *
   * @param desc
   *          the server descriptor to be used to update the error pane and browser controller.
   */
  private void updateBrowserControllerAndErrorPane(ServerDescriptor desc)
  {
    boolean displayNodes = false;
    boolean displayErrorPane = false;
    LocalizableMessage errorTitle = LocalizableMessage.EMPTY;
    LocalizableMessage errorDetails = LocalizableMessage.EMPTY;
    ServerDescriptor.ServerStatus status = desc.getStatus();
    if (status == ServerDescriptor.ServerStatus.STARTED)
    {
      if (!desc.isAuthenticated())
      {
        LocalizableMessageBuilder mb = new LocalizableMessageBuilder();
        mb.append(INFO_CTRL_PANEL_AUTHENTICATION_REQUIRED_TO_BROWSE_SUMMARY.get());
        mb.append("<br><br>").append(getAuthenticateHTML());
        errorDetails = mb.toMessage();
        errorTitle = INFO_CTRL_PANEL_AUTHENTICATION_REQUIRED_SUMMARY.get();

        displayErrorPane = true;
      }
      else
      {
        try
        {
          InitialLdapContext ctx = getInfo().getConnection().getLdapContext();
          InitialLdapContext ctx1 = controller.getConfigurationConnection();
          boolean setConnection = ctx != ctx1;
          updateNumSubordinateHacker(desc);
          if (setConnection)
          {
            if (getInfo().getUserDataDirContext() == null)
            {
              InitialLdapContext ctxUserData =
                  createUserDataDirContext(ConnectionUtils.getBindDN(ctx), ConnectionUtils.getBindPassword(ctx));
              getInfo().setUserDataDirContext(ctxUserData);
            }
            final NamingException[] fNe = { null };
            Runnable runnable = new Runnable()
            {
              @Override
              public void run()
              {
                try
                {
                  ControlPanelInfo info = getInfo();
                  controller.setConnections(
                      info.getServerDescriptor(), info.getConnection(), info.getUserDataDirContext());
                  applyButtonClicked();
                }
                catch (NamingException ne)
                {
                  fNe[0] = ne;
                }
              }
            };
            if (!SwingUtilities.isEventDispatchThread())
            {
              try
              {
                SwingUtilities.invokeAndWait(runnable);
              }
              catch (Throwable t) {}
            }
            else
            {
              runnable.run();
            }

            if (fNe[0] != null)
            {
              throw fNe[0];
            }
          }
          displayNodes = true;
        }
        catch (NamingException ne)
        {
          errorTitle = INFO_CTRL_PANEL_ERROR_CONNECT_BROWSE_DETAILS.get();
          errorDetails = INFO_CTRL_PANEL_ERROR_CONNECT_BROWSE_SUMMARY.get(ne);
          displayErrorPane = true;
        }
        catch (ConfigReadException cre)
        {
          errorTitle = INFO_CTRL_PANEL_ERROR_CONNECT_BROWSE_DETAILS.get();
          errorDetails = INFO_CTRL_PANEL_ERROR_CONNECT_BROWSE_SUMMARY.get(cre.getMessageObject());
          displayErrorPane = true;
        }
      }
    }
    else if (status == ServerDescriptor.ServerStatus.NOT_CONNECTED_TO_REMOTE)
    {
      LocalizableMessageBuilder mb = new LocalizableMessageBuilder();
      mb.append(INFO_CTRL_PANEL_CANNOT_CONNECT_TO_REMOTE_DETAILS.get(desc.getHostname()));
      mb.append("<br><br>").append(getAuthenticateHTML());
      errorDetails = mb.toMessage();
      errorTitle = INFO_CTRL_PANEL_CANNOT_CONNECT_TO_REMOTE_SUMMARY.get();
      displayErrorPane = true;
    }
    else
    {
      errorTitle = INFO_CTRL_PANEL_SERVER_NOT_RUNNING_SUMMARY.get();
      LocalizableMessageBuilder mb = new LocalizableMessageBuilder();
      mb.append(INFO_CTRL_PANEL_AUTHENTICATION_SERVER_MUST_RUN_TO_BROWSE_SUMMARY.get());
      mb.append("<br><br>");
      mb.append(getStartServerHTML());
      errorDetails = mb.toMessage();
      displayErrorPane = true;
    }

    final boolean fDisplayNodes = displayNodes;
    final boolean fDisplayErrorPane = displayErrorPane;
    final LocalizableMessage fErrorTitle = errorTitle;
    final LocalizableMessage fErrorDetails = errorDetails;
    SwingUtilities.invokeLater(new Runnable()
    {
      @Override
      public void run()
      {
        applyButton.setEnabled(!fDisplayErrorPane);
        errorPane.setVisible(fDisplayErrorPane);
        if (fDisplayErrorPane)
        {
          updateErrorPane(errorPane, fErrorTitle,
              ColorAndFontConstants.errorTitleFont, fErrorDetails, ColorAndFontConstants.defaultFont);
        }
        else if (fDisplayNodes)
        {
          // Update the browser controller with the potential new suffixes.
          String s = getBaseDN();
          DN theDN = null;
          boolean displayAll = false;
          if (s != null)
          {
            displayAll = ALL_BASE_DNS.equals(s);
            if (!displayAll)
            {
              try
              {
                theDN = DN.valueOf(s);
              }
              catch (Throwable t)
              {
                s = null;
              }
            }
          }
          treePane.getTree().setRootVisible(displayAll);
          treePane.getTree().setShowsRootHandles(!displayAll);
          if (s != null)
          {
            boolean added = false;
            for (BackendDescriptor backend : getInfo().getServerDescriptor().getBackends())
            {
              for (BaseDNDescriptor baseDN : backend.getBaseDns())
              {
                boolean isBaseDN = false;
                String dn = Utilities.unescapeUtf8(baseDN.getDn().toString());
                if (theDN != null && baseDN.getDn().equals(theDN))
                {
                  isBaseDN = true;
                }
                if (baseDN.getEntries() > 0)
                {
                  try
                  {
                    if ((displayAll || isBaseDN) && !controller.hasSuffix(dn))
                    {
                      controller.addSuffix(dn, null);
                      added = true;
                    }
                  }
                  catch (IllegalArgumentException iae)
                  {
                    // The suffix node exists but is not a suffix node. Simply log a message.
                    logger.warn(LocalizableMessage.raw(
                        "Suffix: " + dn + " added as a non suffix node. Exception: " + iae, iae));
                  }
                }
              }
              if (!added && !displayAll)
              {
                BasicNode rootNode = (BasicNode) controller.getTree().getModel().getRoot();
                if (controller.findChildNode(rootNode, s) == -1)
                {
                  controller.addNodeUnderRoot(s);
                }
              }
            }
          }
        }

        if (!fDisplayNodes)
        {
          controller.removeAllUnderRoot();
          treePane.getTree().setRootVisible(false);
        }
      }
    });
  }

  /**
   * Returns the base DN specified by the user.
   *
   * @return the base DN specified by the user.
   */
  private String getBaseDN()
  {
    String dn = getBaseDN0();
    if (dn != null && dn.trim().length() == 0)
    {
      dn = ALL_BASE_DNS;
    }
    return dn;
  }

  private String getBaseDN0()
  {
    Object o = baseDNs.getSelectedItem();
    if (o instanceof String)
    {
      return (String) o;
    }
    else if (o instanceof CategorizedComboBoxElement)
    {
      return ((CategorizedComboBoxElement) o).getValue().toString();
    }
    else
    {
      return null;
    }
  }

  /**
   * Creates the context to be used to retrieve user data for some given
   * credentials.
   *
   * @param bindDN
   *          the bind DN.
   * @param bindPassword
   *          the bind password.
   * @return the context to be used to retrieve user data for some given
   *         credentials.
   * @throws NamingException
   *           if an error occurs connecting to the server.
   * @throws ConfigReadException
   *           if an error occurs reading the configuration.
   */
  private InitialLdapContext createUserDataDirContext(final String bindDN, final String bindPassword)
      throws NamingException, ConfigReadException
  {
    createdUserDataCtx = null;
    try
    {
      createdUserDataCtx = Utilities.getUserDataDirContext(getInfo(), bindDN, bindPassword);
    }
    catch (NamingException ne)
    {
      if (!isCertificateException(ne))
      {
        throw ne;
      }

      ApplicationTrustManager.Cause cause = getInfo().getTrustManager().getLastRefusedCause();

      logger.info(LocalizableMessage.raw("Certificate exception cause: " + cause));
      UserDataCertificateException.Type excType = null;
      if (cause == ApplicationTrustManager.Cause.NOT_TRUSTED)
      {
        excType = UserDataCertificateException.Type.NOT_TRUSTED;
      }
      else if (cause == ApplicationTrustManager.Cause.HOST_NAME_MISMATCH)
      {
        excType = UserDataCertificateException.Type.HOST_NAME_MISMATCH;
      }

      if (excType != null)
      {
        String h;
        int p;
        try
        {
          URI uri = new URI(getInfo().getAdminConnectorURL());
          h = uri.getHost();
          p = uri.getPort();
        }
        catch (Throwable t)
        {
          logger.warn(LocalizableMessage.raw("Error parsing ldap url of ldap url.", t));
          h = INFO_NOT_AVAILABLE_LABEL.get().toString();
          p = -1;
        }
        final UserDataCertificateException udce = new UserDataCertificateException(
            null, INFO_CERTIFICATE_EXCEPTION.get(h, p), ne, h, p, getInfo().getTrustManager().getLastRefusedChain(),
            getInfo().getTrustManager().getLastRefusedAuthType(), excType);

        if (SwingUtilities.isEventDispatchThread())
        {
          handleCertificateException(udce, bindDN, bindPassword);
        }
        else
        {
          final ConfigReadException[] fcre = { null };
          final NamingException[] fne = { null };
          try
          {
            SwingUtilities.invokeAndWait(new Runnable()
            {
              @Override
              public void run()
              {
                try
                {
                  handleCertificateException(udce, bindDN, bindPassword);
                }
                catch (ConfigReadException cre)
                {
                  fcre[0] = cre;
                }
                catch (NamingException ne)
                {
                  fne[0] = ne;
                }
              }
            });
          }
          catch (Exception e)
          {
            throw new IllegalArgumentException("Unexpected error: " + e, e);
          }
          if (fcre[0] != null)
          {
            throw fcre[0];
          }
          if (fne[0] != null)
          {
            throw fne[0];
          }
        }
      }
    }
    return createdUserDataCtx;
  }

  /**
   * Displays a dialog asking the user to accept a certificate if the user
   * accepts it, we update the trust manager and simulate a click on "OK" to
   * re-check the authentication. This method assumes that we are being called
   * from the event thread.
   *
   * @param bindDN
   *          the bind DN.
   * @param bindPassword
   *          the bind password.
   */
  private void handleCertificateException(UserDataCertificateException ce, String bindDN, String bindPassword)
      throws NamingException, ConfigReadException
  {
    CertificateDialog dlg = new CertificateDialog(null, ce);
    dlg.pack();
    Utilities.centerGoldenMean(dlg, Utilities.getParentDialog(this));
    dlg.setVisible(true);
    if (dlg.getUserAnswer() != CertificateDialog.ReturnType.NOT_ACCEPTED)
    {
      X509Certificate[] chain = ce.getChain();
      String authType = ce.getAuthType();
      String host = ce.getHost();

      if (chain != null && authType != null && host != null)
      {
        logger.info(LocalizableMessage.raw("Accepting certificate presented by host " + host));
        getInfo().getTrustManager().acceptCertificate(chain, authType, host);
        createdUserDataCtx = createUserDataDirContext(bindDN, bindPassword);
      }
      else
      {
        if (chain == null)
        {
          logger.warn(LocalizableMessage.raw("The chain is null for the UserDataCertificateException"));
        }
        if (authType == null)
        {
          logger.warn(LocalizableMessage.raw("The auth type is null for the UserDataCertificateException"));
        }
        if (host == null)
        {
          logger.warn(LocalizableMessage.raw("The host is null for the UserDataCertificateException"));
        }
      }
    }
    if (dlg.getUserAnswer() == CertificateDialog.ReturnType.ACCEPTED_PERMANENTLY)
    {
      X509Certificate[] chain = ce.getChain();
      if (chain != null)
      {
        try
        {
          UIKeyStore.acceptCertificate(chain);
        }
        catch (Throwable t)
        {
          logger.warn(LocalizableMessage.raw("Error accepting certificate: " + t, t));
        }
      }
    }
  }

  /**
   * This class is used simply to avoid an inset on the left for the 'All Base
   * DNs' item. Since this item is a CategorizedComboBoxElement of type
   * CategorizedComboBoxElement.Type.REGULAR, it has by default an inset on the
   * left. The class simply handles this particular case to not to have that
   * inset for the 'All Base DNs' item.
   */
  private class CustomComboBoxCellRenderer extends CustomListCellRenderer
  {
    private final LocalizableMessage ALL_BASE_DNS_STRING = INFO_CTRL_PANEL_ALL_BASE_DNS.get();

    /**
     * The constructor.
     *
     * @param combo
     *          the combo box to be rendered.
     */
    private CustomComboBoxCellRenderer(JComboBox<?> combo)
    {
      super(combo);
    }

    @Override
    @SuppressWarnings("rawtypes")
    public Component getListCellRendererComponent(
        JList list, Object value, int index, boolean isSelected, boolean cellHasFocus)
    {
      Component comp = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
      if (value instanceof CategorizedComboBoxElement)
      {
        CategorizedComboBoxElement element = (CategorizedComboBoxElement) value;
        String name = getStringValue(element);
        if (ALL_BASE_DNS.equals(name))
        {
          ((JLabel) comp).setText(ALL_BASE_DNS_STRING.toString());
        }
      }
      comp.setFont(defaultFont);
      return comp;
    }
  }

  /**
   * Checks that the root node has some children. It it has no children the
   * message 'No Match Found' is displayed instead of the tree panel.
   */
  private void checkRootNode()
  {
    DefaultMutableTreeNode root = (DefaultMutableTreeNode) controller.getTreeModel().getRoot();
    boolean visible = root.getChildCount() > 0;
    if (visible != treePane.isVisible())
    {
      treePane.setVisible(visible);
      lNoMatchFound.setVisible(!visible);
      lNumberOfEntries.setVisible(visible);
    }
    numberEntriesUpdater.recalculate();
  }

  /**
   * Updates the NumsubordinateHacker of the browser controller with the
   * provided server descriptor.
   *
   * @param server
   *          the server descriptor.
   */
  private void updateNumSubordinateHacker(ServerDescriptor server)
  {
    String serverHost = server.getHostname();
    int serverPort = server.getAdminConnector().getPort();

    List<DN> allSuffixes = new ArrayList<>();
    for (BackendDescriptor backend : server.getBackends())
    {
      for (BaseDNDescriptor baseDN : backend.getBaseDns())
      {
        allSuffixes.add(baseDN.getDn());
      }
    }

    List<DN> rootSuffixes = new ArrayList<>();
    for (DN dn : allSuffixes)
    {
      if (isRootSuffix(allSuffixes, dn))
      {
        rootSuffixes.add(dn);
      }
    }
    controller.getNumSubordinateHacker().update(allSuffixes, rootSuffixes, serverHost, serverPort);
  }

  private boolean isRootSuffix(List<DN> allSuffixes, DN dn)
  {
    for (DN suffix : allSuffixes)
    {
      if (suffix.isSuperiorOrEqualTo(dn) && !suffix.equals(dn))
      {
        return false;
      }
    }
    return true;
  }

  /**
   * This is a class that simply checks the number of entries that the browser
   * contains and updates a counter with the new number of entries. It is
   * basically a thread that sleeps and checks whether some calculation must be
   * made: when we know that something is updated in the browser the method
   * recalculate() is called. We could use a more sophisticated code (like use a
   * wait() call that would get notified when recalculate() is called) but this
   * is not required and it might have an impact on the reactivity of the UI if
   * recalculate gets called too often. We can afford to wait 400 miliseconds
   * before updating the number of entries and with this approach there is
   * hardly no impact on the reactivity of the UI.
   */
  private class NumberOfEntriesUpdater extends Thread
  {
    private boolean recalculate;

    /** Notifies that the number of entries in the browser has changed. */
    private void recalculate()
    {
      recalculate = true;
    }

    /** Executes the updater. */
    @Override
    public void run()
    {
      while (true)
      {
        try
        {
          Thread.sleep(400);
        }
        catch (Throwable t)
        {
          // ignore
        }
        if (recalculate)
        {
          recalculate = false;
          SwingUtilities.invokeLater(new Runnable()
          {
            @Override
            public void run()
            {
              int nEntries = 0;
              // This recursive algorithm is fast enough to use it on the
              // event thread.  Running it here we avoid issues with concurrent
              // access to the node children
              if (controller.getTree().isRootVisible())
              {
                nEntries++;
              }
              DefaultMutableTreeNode root = (DefaultMutableTreeNode) controller.getTreeModel().getRoot();

              nEntries += getChildren(root);
              lNumberOfEntries.setText(INFO_CTRL_BROWSER_NUMBER_OF_ENTRIES.get(nEntries).toString());
            }
          });
        }
        if (controller != null)
        {
          final boolean mustDisplayRefreshIcon = controller.getQueueSize() > 0;
          if (mustDisplayRefreshIcon != filter.isRefreshIconDisplayed())
          {
            SwingUtilities.invokeLater(new Runnable()
            {
              @Override
              public void run()
              {
                filter.displayRefreshIcon(mustDisplayRefreshIcon);
              }
            });
          }
        }
      }
    }

    /**
     * Returns the number of children for a given node.
     *
     * @param node
     *          the node.
     * @return the number of children for the node.
     */
    private int getChildren(DefaultMutableTreeNode node)
    {
      int nEntries = 0;

      if (!node.isLeaf())
      {
        Enumeration<?> en = node.children();
        while (en.hasMoreElements())
        {
          nEntries++;
          nEntries += getChildren((DefaultMutableTreeNode) en.nextElement());
        }
      }
      return nEntries;
    }
  }
}
