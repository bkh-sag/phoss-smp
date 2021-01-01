/**
 * Copyright (C) 2014-2021 Philip Helger and contributors
 * philip[at]helger[dot]com
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.helger.phoss.smp.ui.secure;

import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;

import javax.annotation.Nonnegative;
import javax.annotation.Nonnull;

import com.helger.collection.multimap.MultiHashMapArrayListBased;
import com.helger.collection.multimap.MultiHashMapHashSetBased;
import com.helger.commons.annotation.Nonempty;
import com.helger.commons.collection.CollectionHelper;
import com.helger.commons.collection.impl.CommonsTreeSet;
import com.helger.commons.collection.impl.ICommonsList;
import com.helger.commons.collection.impl.ICommonsSet;
import com.helger.commons.collection.impl.ICommonsSortedSet;
import com.helger.commons.compare.ESortOrder;
import com.helger.commons.state.EValidity;
import com.helger.commons.state.IValidityIndicator;
import com.helger.commons.string.StringHelper;
import com.helger.commons.text.ReadOnlyMultilingualText;
import com.helger.commons.url.ISimpleURL;
import com.helger.commons.url.URLValidator;
import com.helger.html.hc.IHCNode;
import com.helger.html.hc.html.forms.HCEdit;
import com.helger.html.hc.html.forms.HCHiddenField;
import com.helger.html.hc.html.forms.HCSelect;
import com.helger.html.hc.html.grouping.HCUL;
import com.helger.html.hc.html.tabular.HCRow;
import com.helger.html.hc.html.tabular.HCTable;
import com.helger.html.hc.html.textlevel.HCA;
import com.helger.html.hc.impl.HCNodeList;
import com.helger.peppolid.IParticipantIdentifier;
import com.helger.peppolid.factory.IIdentifierFactory;
import com.helger.phoss.smp.CSMPServer;
import com.helger.phoss.smp.domain.SMPMetaManager;
import com.helger.phoss.smp.domain.servicegroup.ISMPServiceGroup;
import com.helger.phoss.smp.domain.servicegroup.ISMPServiceGroupManager;
import com.helger.phoss.smp.domain.serviceinfo.ISMPEndpoint;
import com.helger.phoss.smp.domain.serviceinfo.ISMPProcess;
import com.helger.phoss.smp.domain.serviceinfo.ISMPServiceInformation;
import com.helger.phoss.smp.domain.serviceinfo.ISMPServiceInformationManager;
import com.helger.phoss.smp.domain.serviceinfo.SMPEndpoint;
import com.helger.phoss.smp.ui.AbstractSMPWebPage;
import com.helger.photon.app.PhotonWorkerPool;
import com.helger.photon.bootstrap4.button.BootstrapButton;
import com.helger.photon.bootstrap4.buttongroup.BootstrapButtonToolbar;
import com.helger.photon.bootstrap4.form.BootstrapForm;
import com.helger.photon.bootstrap4.form.BootstrapFormGroup;
import com.helger.photon.bootstrap4.traits.IHCBootstrap4Trait;
import com.helger.photon.bootstrap4.uictrls.datatables.BootstrapDTColAction;
import com.helger.photon.bootstrap4.uictrls.datatables.BootstrapDataTables;
import com.helger.photon.core.form.FormErrorList;
import com.helger.photon.core.form.RequestField;
import com.helger.photon.core.longrun.AbstractLongRunningJobRunnable;
import com.helger.photon.core.longrun.LongRunningJobResult;
import com.helger.photon.uicore.css.CPageParam;
import com.helger.photon.uicore.icon.EDefaultIcon;
import com.helger.photon.uicore.page.AbstractWebPageForm;
import com.helger.photon.uicore.page.WebPageExecutionContext;
import com.helger.photon.uictrls.datatables.DataTables;
import com.helger.photon.uictrls.datatables.column.DTCol;
import com.helger.photon.uictrls.datatables.column.EDTColType;
import com.helger.web.scope.mgr.WebScoped;

public final class PageSecureEndpointChangeURL extends AbstractSMPWebPage
{
  /**
   * The async logic
   *
   * @author Philip Helger
   */
  private static final class BulkChangeEndpointURL extends AbstractLongRunningJobRunnable implements IHCBootstrap4Trait
  {
    private static final AtomicInteger s_aRunningJobs = new AtomicInteger (0);

    private final ICommonsList <ISMPServiceInformation> m_aAllSIs;
    private final ISMPServiceGroup m_aServiceGroup;
    private final String m_sOldURL;
    private final String m_sNewURL;

    public BulkChangeEndpointURL (final ICommonsList <ISMPServiceInformation> aAllSIs,
                                  final ISMPServiceGroup aServiceGroup,
                                  final String sOldURL,
                                  final String sNewURL)
    {
      super ("BulkChangeEndpointURL", new ReadOnlyMultilingualText (CSMPServer.DEFAULT_LOCALE, "Bulk change endpoint URL"));
      m_aAllSIs = aAllSIs;
      m_aServiceGroup = aServiceGroup;
      m_sOldURL = sOldURL;
      m_sNewURL = sNewURL;
    }

    @Nonnull
    public LongRunningJobResult createLongRunningJobResult ()
    {
      s_aRunningJobs.incrementAndGet ();
      try (final WebScoped w = new WebScoped ())
      {
        final ISMPServiceInformationManager aServiceInfoMgr = SMPMetaManager.getServiceInformationMgr ();

        // Modify all endpoints
        int nChangedEndpoints = 0;
        int nSaveErrors = 0;
        final ICommonsSortedSet <String> aChangedServiceGroup = new CommonsTreeSet <> ();
        for (final ISMPServiceInformation aSI : m_aAllSIs)
        {
          if (m_aServiceGroup != null && !aSI.getServiceGroup ().equals (m_aServiceGroup))
          {
            // Wrong service group
            continue;
          }

          boolean bChanged = false;
          for (final ISMPProcess aProcess : aSI.getAllProcesses ())
            for (final ISMPEndpoint aEndpoint : aProcess.getAllEndpoints ())
              if (m_sOldURL.equals (aEndpoint.getEndpointReference ()))
              {
                ((SMPEndpoint) aEndpoint).setEndpointReference (m_sNewURL);
                bChanged = true;
                ++nChangedEndpoints;
              }
          if (bChanged)
          {
            if (aServiceInfoMgr.mergeSMPServiceInformation (aSI).isFailure ())
              nSaveErrors++;
            aChangedServiceGroup.add (aSI.getServiceGroupID ());
          }
        }

        final IHCNode aRes;
        if (nChangedEndpoints > 0)
        {
          final HCUL aUL = new HCUL ();
          for (final String sChangedServiceGroupID : aChangedServiceGroup)
            aUL.addItem (sChangedServiceGroupID);

          final HCNodeList aNodes = new HCNodeList ().addChildren (div ("The old URL '" +
                                                                        m_sOldURL +
                                                                        "' was changed in " +
                                                                        nChangedEndpoints +
                                                                        " endpoints. Effected service groups are:"),
                                                                   aUL);
          if (nSaveErrors == 0)
            aRes = success (aNodes);
          else
          {
            aNodes.addChildAt (0, h3 ("Some changes could NOT be saved! Please check the logs!"));
            aRes = error (aNodes);
          }
        }
        else
          aRes = warn ("No endpoint was found that contains the old URL '" + m_sOldURL + "'");

        return LongRunningJobResult.createXML (aRes);
      }
      finally
      {
        s_aRunningJobs.decrementAndGet ();
      }
    }

    @Nonnegative
    public static int getRunningJobCount ()
    {
      return s_aRunningJobs.get ();
    }
  }

  private static final String SERVICE_GROUP_ALL = "all";
  private static final String FIELD_SERVICE_GROUP = "servicegroup";
  private static final String FIELD_OLD_URL = "oldurl";
  private static final String FIELD_NEW_URL = "newurl";

  public PageSecureEndpointChangeURL (@Nonnull @Nonempty final String sID)
  {
    super (sID, "Bulk change URL");
  }

  @Override
  @Nonnull
  protected IValidityIndicator isValidToDisplayPage (@Nonnull final WebPageExecutionContext aWPEC)
  {
    final HCNodeList aNodeList = aWPEC.getNodeList ();
    final ISMPServiceGroupManager aServiceGroupMgr = SMPMetaManager.getServiceGroupMgr ();
    if (aServiceGroupMgr.getSMPServiceGroupCount () == 0)
    {
      aNodeList.addChild (warn ("No service group is present! At least one service group must be present to change endpoints."));
      aNodeList.addChild (new BootstrapButton ().addChild ("Create new service group")
                                                .setOnClick (AbstractWebPageForm.createCreateURL (aWPEC, CMenuSecure.MENU_SERVICE_GROUPS))
                                                .setIcon (EDefaultIcon.YES));
      return EValidity.INVALID;
    }
    return super.isValidToDisplayPage (aWPEC);
  }

  @Override
  protected void fillContent (@Nonnull final WebPageExecutionContext aWPEC)
  {
    final Locale aDisplayLocale = aWPEC.getDisplayLocale ();
    final HCNodeList aNodeList = aWPEC.getNodeList ();
    final IIdentifierFactory aIdentifierFactory = SMPMetaManager.getIdentifierFactory ();
    final ISMPServiceGroupManager aServiceGroupMgr = SMPMetaManager.getServiceGroupMgr ();
    final ISMPServiceInformationManager aServiceInfoMgr = SMPMetaManager.getServiceInformationMgr ();
    boolean bShowList = true;

    final MultiHashMapArrayListBased <String, ISMPEndpoint> aEndpointsGroupedPerURL = new MultiHashMapArrayListBased <> ();
    final MultiHashMapHashSetBased <String, ISMPServiceGroup> aServiceGroupsGroupedPerURL = new MultiHashMapHashSetBased <> ();
    final ICommonsList <ISMPServiceInformation> aAllSIs = aServiceInfoMgr.getAllSMPServiceInformation ();
    int nTotalEndpointCount = 0;
    int nTotalEndpointCountWithURL = 0;
    for (final ISMPServiceInformation aSI : aAllSIs)
    {
      final ISMPServiceGroup aSG = aSI.getServiceGroup ();
      for (final ISMPProcess aProcess : aSI.getAllProcesses ())
        for (final ISMPEndpoint aEndpoint : aProcess.getAllEndpoints ())
        {
          ++nTotalEndpointCount;
          if (aEndpoint.hasEndpointReference ())
          {
            aEndpointsGroupedPerURL.putSingle (aEndpoint.getEndpointReference (), aEndpoint);
            aServiceGroupsGroupedPerURL.putSingle (aEndpoint.getEndpointReference (), aSG);
            ++nTotalEndpointCountWithURL;
          }
        }
    }

    {
      final BootstrapButtonToolbar aToolbar = new BootstrapButtonToolbar (aWPEC);
      aToolbar.addButton ("Refresh", aWPEC.getSelfHref (), EDefaultIcon.REFRESH);
      aNodeList.addChild (aToolbar);

      final int nCount = BulkChangeEndpointURL.getRunningJobCount ();
      if (nCount > 0)
      {
        aNodeList.addChild (warn ((nCount == 1 ? "1 bulk change is" : nCount + " bulk changes are") +
                                  " currently running in the background"));
      }
    }

    if (aWPEC.hasAction (CPageParam.ACTION_EDIT))
    {
      bShowList = false;
      final FormErrorList aFormErrors = new FormErrorList ();

      final String sOldURL = aWPEC.params ().getAsString (FIELD_OLD_URL);

      if (aWPEC.hasSubAction (CPageParam.ACTION_SAVE))
      {
        // Find selected service group (if any)
        final String sServiceGroupID = aWPEC.params ().getAsString (FIELD_SERVICE_GROUP);
        ISMPServiceGroup aServiceGroup = null;
        if (StringHelper.hasText (sServiceGroupID))
        {
          final IParticipantIdentifier aParticipantID = aIdentifierFactory.parseParticipantIdentifier (sServiceGroupID);
          if (aParticipantID != null)
            aServiceGroup = aServiceGroupMgr.getSMPServiceGroupOfID (aParticipantID);
        }

        final String sNewURL = aWPEC.params ().getAsString (FIELD_NEW_URL);

        if (StringHelper.hasNoText (sOldURL))
          aFormErrors.addFieldInfo (FIELD_OLD_URL, "An old URL must be provided");
        else
          if (!URLValidator.isValid (sOldURL))
            aFormErrors.addFieldInfo (FIELD_OLD_URL, "The old URL is invalid");

        if (StringHelper.hasNoText (sNewURL))
          aFormErrors.addFieldInfo (FIELD_NEW_URL, "A new URL must be provided");
        else
          if (!URLValidator.isValid (sNewURL))
            aFormErrors.addFieldInfo (FIELD_NEW_URL, "The new URL is invalid");
          else
            if (sNewURL.equals (sOldURL))
              aFormErrors.addFieldInfo (FIELD_NEW_URL, "The new URL is identical to the old URL");

        // Validate parameters
        if (aFormErrors.isEmpty ())
        {
          PhotonWorkerPool.getInstance ()
                          .run ("BulkChangeEndpointURL", new BulkChangeEndpointURL (aAllSIs, aServiceGroup, sOldURL, sNewURL));

          aWPEC.postRedirectGetInternal (success ("The bulk change of the endpoint URL from '" +
                                                  sOldURL +
                                                  "' to '" +
                                                  sNewURL +
                                                  "' is now running in the background. Please manually refresh the page to see the update."));
        }
      }

      final ICommonsSet <ISMPServiceGroup> aServiceGroups = aServiceGroupsGroupedPerURL.get (sOldURL);
      final int nSGCount = CollectionHelper.getSize (aServiceGroups);
      final int nEPCount = CollectionHelper.getSize (aEndpointsGroupedPerURL.get (sOldURL));
      aNodeList.addChild (info ("The selected old URL '" +
                                sOldURL +
                                "' is currently used in " +
                                nEPCount +
                                " " +
                                (nEPCount == 1 ? "endpoint" : "endpoints") +
                                " of " +
                                nSGCount +
                                " " +
                                (nSGCount == 1 ? "service group" : "service groups") +
                                "."));

      // Show edit screen
      final BootstrapForm aForm = aNodeList.addAndReturnChild (getUIHandler ().createFormSelf (aWPEC));
      aForm.addChild (new HCHiddenField (CPageParam.PARAM_ACTION, CPageParam.ACTION_EDIT));
      aForm.addChild (new HCHiddenField (CPageParam.PARAM_SUBACTION, CPageParam.ACTION_SAVE));

      if (nSGCount > 1)
      {
        // Select the affected service groups if more than one is available
        final HCSelect aSGSelect = new HCSelect (new RequestField (FIELD_SERVICE_GROUP));
        aSGSelect.addOption (SERVICE_GROUP_ALL, "All affected Service Groups");
        if (aServiceGroups != null)
          for (final ISMPServiceGroup aSG : aServiceGroups.getSorted (ISMPServiceGroup.comparator ()))
            aSGSelect.addOption (aSG.getID (), aSG.getParticpantIdentifier ().getURIEncoded ());
        aForm.addFormGroup (new BootstrapFormGroup ().setLabel ("Service group")
                                                     .setCtrl (aSGSelect)
                                                     .setHelpText ("If a specific service group is selected, the URL change will only happen in the endpoints of the selected service group. Othwerwise the endpoint is changed in ALL service groups with matching endpoints.")
                                                     .setErrorList (aFormErrors.getListOfField (FIELD_OLD_URL)));
      }
      else
      {
        // If less than 2 service groups are affected, use the 0/1
        // automatically.
        aForm.addChild (new HCHiddenField (FIELD_SERVICE_GROUP, SERVICE_GROUP_ALL));
      }

      aForm.addFormGroup (new BootstrapFormGroup ().setLabelMandatory ("Old endpoint URL")
                                                   .setCtrl (new HCEdit (new RequestField (FIELD_OLD_URL, sOldURL)))
                                                   .setHelpText ("The old URL that is to be changed in all matching endpoints")
                                                   .setErrorList (aFormErrors.getListOfField (FIELD_OLD_URL)));

      aForm.addFormGroup (new BootstrapFormGroup ().setLabelMandatory ("New endpoint URL")
                                                   .setCtrl (new HCEdit (new RequestField (FIELD_NEW_URL, sOldURL)))
                                                   .setHelpText ("The new URL that is used instead")
                                                   .setErrorList (aFormErrors.getListOfField (FIELD_NEW_URL)));

      final BootstrapButtonToolbar aToolbar = aForm.addAndReturnChild (getUIHandler ().createToolbar (aWPEC));
      aToolbar.addSubmitButton ("Save changes", EDefaultIcon.SAVE);
      aToolbar.addButtonCancel (aDisplayLocale);
    }

    if (bShowList)
    {
      aNodeList.addChild (info ().addChildren (div ("This page lets you change the URLs of multiple endpoints at once. This is e.g. helpful when the underlying server got a new URL."),
                                               div ("Currently " +
                                                    (nTotalEndpointCount == 1 ? "1 endpoint is" : nTotalEndpointCount + " endpoints are") +
                                                    " registered" +
                                                    (nTotalEndpointCountWithURL < nTotalEndpointCount ? " of which " +
                                                                                                        nTotalEndpointCountWithURL +
                                                                                                        " have an endpoint reference"
                                                                                                      : "") +
                                                    ".")));

      final HCTable aTable = new HCTable (new DTCol ("Endpoint URL").setInitialSorting (ESortOrder.ASCENDING),
                                          new DTCol ("Service Group Count").setDisplayType (EDTColType.INT, aDisplayLocale),
                                          new DTCol ("Endpoint Count").setDisplayType (EDTColType.INT, aDisplayLocale),
                                          new BootstrapDTColAction (aDisplayLocale)).setID (getID ());
      aEndpointsGroupedPerURL.forEach ( (sURL, aEndpoints) -> {
        final HCRow aRow = aTable.addBodyRow ();
        aRow.addCell (sURL);

        final int nSGCount = CollectionHelper.getSize (aServiceGroupsGroupedPerURL.get (sURL));
        aRow.addCell (Integer.toString (nSGCount));

        aRow.addCell (Integer.toString (aEndpoints.size ()));

        final ISimpleURL aEditURL = aWPEC.getSelfHref ().add (CPageParam.PARAM_ACTION, CPageParam.ACTION_EDIT).add (FIELD_OLD_URL, sURL);
        aRow.addCell (new HCA (aEditURL).setTitle ("Change all endpoints pointing to " + sURL).addChild (EDefaultIcon.EDIT.getAsNode ()));
      });

      final DataTables aDataTables = BootstrapDataTables.createDefaultDataTables (aWPEC, aTable);
      aNodeList.addChild (aTable).addChild (aDataTables);
    }
  }
}
