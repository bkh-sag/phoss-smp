/**
 * Copyright (C) 2014-2016 Philip Helger (www.helger.com)
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
package com.helger.peppol.smpserver.ui.secure;

import java.security.cert.X509Certificate;
import java.time.LocalDate;
import java.util.Locale;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import com.helger.commons.annotation.Nonempty;
import com.helger.commons.collection.CollectionHelper;
import com.helger.commons.collection.ext.CommonsTreeSet;
import com.helger.commons.collection.ext.ICommonsList;
import com.helger.commons.collection.ext.ICommonsSortedSet;
import com.helger.commons.collection.multimap.MultiHashMapArrayListBased;
import com.helger.commons.compare.ESortOrder;
import com.helger.commons.datetime.PDTFactory;
import com.helger.commons.errorlist.FormErrors;
import com.helger.commons.state.EValidity;
import com.helger.commons.state.IValidityIndicator;
import com.helger.commons.string.StringHelper;
import com.helger.commons.url.ISimpleURL;
import com.helger.datetime.format.PDTToString;
import com.helger.html.hc.IHCNode;
import com.helger.html.hc.html.forms.HCHiddenField;
import com.helger.html.hc.html.grouping.HCDiv;
import com.helger.html.hc.html.grouping.HCUL;
import com.helger.html.hc.html.tabular.HCRow;
import com.helger.html.hc.html.tabular.HCTable;
import com.helger.html.hc.html.textlevel.HCA;
import com.helger.html.hc.html.textlevel.HCCode;
import com.helger.html.hc.impl.HCNodeList;
import com.helger.peppol.smpserver.domain.SMPMetaManager;
import com.helger.peppol.smpserver.domain.servicegroup.ISMPServiceGroupManager;
import com.helger.peppol.smpserver.domain.serviceinfo.ISMPEndpoint;
import com.helger.peppol.smpserver.domain.serviceinfo.ISMPProcess;
import com.helger.peppol.smpserver.domain.serviceinfo.ISMPServiceInformation;
import com.helger.peppol.smpserver.domain.serviceinfo.ISMPServiceInformationManager;
import com.helger.peppol.smpserver.domain.serviceinfo.SMPEndpoint;
import com.helger.peppol.smpserver.ui.AbstractSMPWebPage;
import com.helger.peppol.utils.CertificateHelper;
import com.helger.photon.bootstrap3.alert.BootstrapInfoBox;
import com.helger.photon.bootstrap3.alert.BootstrapSuccessBox;
import com.helger.photon.bootstrap3.alert.BootstrapWarnBox;
import com.helger.photon.bootstrap3.button.BootstrapButton;
import com.helger.photon.bootstrap3.button.BootstrapButtonToolbar;
import com.helger.photon.bootstrap3.form.BootstrapForm;
import com.helger.photon.bootstrap3.form.BootstrapFormGroup;
import com.helger.photon.bootstrap3.uictrls.datatables.BootstrapDTColAction;
import com.helger.photon.bootstrap3.uictrls.datatables.BootstrapDataTables;
import com.helger.photon.core.form.RequestField;
import com.helger.photon.uicore.css.CPageParam;
import com.helger.photon.uicore.icon.EDefaultIcon;
import com.helger.photon.uicore.page.AbstractWebPageForm;
import com.helger.photon.uicore.page.WebPageExecutionContext;
import com.helger.photon.uictrls.autosize.HCTextAreaAutosize;
import com.helger.photon.uictrls.datatables.DataTables;
import com.helger.photon.uictrls.datatables.column.DTCol;
import com.helger.photon.uictrls.datatables.column.EDTColType;

public final class PageSecureEndpointsChangeCertificate extends AbstractSMPWebPage
{
  private static final String FIELD_OLD_CERTIFICATE = "oldcert";
  private static final String FIELD_NEW_CERTIFICATE = "newcert";

  public PageSecureEndpointsChangeCertificate (@Nonnull @Nonempty final String sID)
  {
    super (sID, "Bulk change certificate");
  }

  @Override
  @Nonnull
  protected IValidityIndicator isValidToDisplayPage (@Nonnull final WebPageExecutionContext aWPEC)
  {
    final HCNodeList aNodeList = aWPEC.getNodeList ();
    final ISMPServiceGroupManager aServiceGroupManager = SMPMetaManager.getServiceGroupMgr ();
    if (aServiceGroupManager.getSMPServiceGroupCount () == 0)
    {
      aNodeList.addChild (new BootstrapWarnBox ().addChild ("No service group is present! At least one service group must be present to change endpoints."));
      aNodeList.addChild (new BootstrapButton ().addChild ("Create new service group")
                                                .setOnClick (AbstractWebPageForm.createCreateURL (aWPEC,
                                                                                                  CMenuSecure.MENU_SERVICE_GROUPS))
                                                .setIcon (EDefaultIcon.YES));
      return EValidity.INVALID;
    }
    return super.isValidToDisplayPage (aWPEC);
  }

  @Nullable
  private static String _getCertificateParsingError (@Nonnull final String sCert)
  {
    X509Certificate aEndpointCert = null;
    try
    {
      aEndpointCert = CertificateHelper.convertStringToCertficate (sCert);
    }
    catch (final Exception ex)
    {
      return ex.getMessage ();
    }
    return aEndpointCert != null ? null : "Invalid input string provided";
  }

  @Override
  protected void fillContent (@Nonnull final WebPageExecutionContext aWPEC)
  {
    final Locale aDisplayLocale = aWPEC.getDisplayLocale ();
    final HCNodeList aNodeList = aWPEC.getNodeList ();
    final ISMPServiceInformationManager aServiceInfoMgr = SMPMetaManager.getServiceInformationMgr ();
    boolean bShowList = true;

    final MultiHashMapArrayListBased <String, ISMPEndpoint> aGroupedPerURL = new MultiHashMapArrayListBased<> ();
    final ICommonsList <? extends ISMPServiceInformation> aAllSIs = aServiceInfoMgr.getAllSMPServiceInformation ();
    int nTotalEndpointCount = 0;
    for (final ISMPServiceInformation aSI : aAllSIs)
      for (final ISMPProcess aProcess : aSI.getAllProcesses ())
        for (final ISMPEndpoint aEndpoint : aProcess.getAllEndpoints ())
        {
          ++nTotalEndpointCount;
          aGroupedPerURL.putSingle (aEndpoint.getCertificate (), aEndpoint);
        }

    if (aWPEC.hasAction (CPageParam.ACTION_EDIT))
    {
      bShowList = false;
      final FormErrors aFormErrors = new FormErrors ();

      final String sOldCert = aWPEC.getAttributeAsString (FIELD_OLD_CERTIFICATE);

      if (aWPEC.hasSubAction (CPageParam.ACTION_SAVE))
      {
        final String sNewCert = aWPEC.getAttributeAsString (FIELD_NEW_CERTIFICATE);

        if (StringHelper.hasNoText (sOldCert))
          aFormErrors.addFieldInfo (FIELD_OLD_CERTIFICATE, "An old certificate must be provided");
        else
        {
          final String sErrorDetails = _getCertificateParsingError (sOldCert);
          if (sErrorDetails != null)
            aFormErrors.addFieldInfo (FIELD_OLD_CERTIFICATE, "The old certificate is invalid: " + sErrorDetails);
        }

        if (StringHelper.hasNoText (sNewCert))
          aFormErrors.addFieldInfo (FIELD_NEW_CERTIFICATE, "A new certificate must be provided");
        else
        {
          final String sErrorDetails = _getCertificateParsingError (sNewCert);
          if (sErrorDetails != null)
            aFormErrors.addFieldInfo (FIELD_NEW_CERTIFICATE, "The new certificate is invalid: " + sErrorDetails);
          else
            if (sNewCert.equals (sOldCert))
              aFormErrors.addFieldInfo (FIELD_NEW_CERTIFICATE,
                                        "The new certificate is identical to the old certificate");
        }

        // Validate parameters
        if (aFormErrors.isEmpty ())
        {
          // Modify all endpoints
          int nChangedEndpoints = 0;
          final ICommonsSortedSet <String> aChangedServiceGroup = new CommonsTreeSet<> ();
          for (final ISMPServiceInformation aSI : aAllSIs)
          {
            boolean bChanged = false;
            for (final ISMPProcess aProcess : aSI.getAllProcesses ())
              for (final ISMPEndpoint aEndpoint : aProcess.getAllEndpoints ())
                if (sOldCert.equals (aEndpoint.getCertificate ()))
                {
                  ((SMPEndpoint) aEndpoint).setCertificate (sNewCert);
                  bChanged = true;
                  ++nChangedEndpoints;
                }
            if (bChanged)
            {
              aServiceInfoMgr.mergeSMPServiceInformation (aSI);
              aChangedServiceGroup.add (aSI.getServiceGroupID ());
            }
          }

          if (nChangedEndpoints > 0)
          {
            final HCUL aUL = new HCUL ();
            for (final String sServiceGroupID : aChangedServiceGroup)
              aUL.addItem (sServiceGroupID);
            aWPEC.postRedirectGet (new BootstrapSuccessBox ().addChildren (new HCDiv ().addChild ("The old certificate was changed in " +
                                                                                                  nChangedEndpoints +
                                                                                                  " endpoints to the new certificate:"),
                                                                           _getCertificateDisplay (sNewCert,
                                                                                                   aDisplayLocale),
                                                                           new HCDiv ().addChild ("Effected service groups are:"),
                                                                           aUL));
          }
          else
            aWPEC.postRedirectGet (new BootstrapWarnBox ().addChild ("No endpoint was found that contains the old certificate"));
        }
      }

      final int nEPCount = CollectionHelper.getSize (aGroupedPerURL.get (sOldCert));
      aNodeList.addChild (new BootstrapInfoBox ().addChild ("The selected old certificate is currently used in " +
                                                            nEPCount +
                                                            " " +
                                                            (nEPCount == 1 ? "endpoint" : "endpoints") +
                                                            "."));

      // Show edit screen
      final BootstrapForm aForm = aNodeList.addAndReturnChild (getUIHandler ().createFormSelf (aWPEC));
      aForm.addChild (new HCHiddenField (CPageParam.PARAM_ACTION, CPageParam.ACTION_EDIT));
      aForm.addChild (new HCHiddenField (CPageParam.PARAM_SUBACTION, CPageParam.ACTION_SAVE));
      aForm.addChild (new HCHiddenField (FIELD_OLD_CERTIFICATE, sOldCert));

      aForm.addFormGroup (new BootstrapFormGroup ().setLabel ("Old certificate")
                                                   .setCtrl (_getCertificateDisplay (sOldCert, aDisplayLocale))
                                                   .setHelpText ("The old certificate that is to be changed in all matching endpoints")
                                                   .setErrorList (aFormErrors.getListOfField (FIELD_OLD_CERTIFICATE)));

      aForm.addFormGroup (new BootstrapFormGroup ().setLabelMandatory ("New certificate")
                                                   .setCtrl (new HCTextAreaAutosize (new RequestField (FIELD_NEW_CERTIFICATE,
                                                                                                       sOldCert)))
                                                   .setHelpText ("The new certificate that is used instead")
                                                   .setErrorList (aFormErrors.getListOfField (FIELD_NEW_CERTIFICATE)));

      final BootstrapButtonToolbar aToolbar = aForm.addAndReturnChild (getUIHandler ().createToolbar (aWPEC));
      aToolbar.addSubmitButton ("Save changes", EDefaultIcon.SAVE);
      aToolbar.addButtonCancel (aDisplayLocale);
    }

    if (bShowList)
    {
      aNodeList.addChild (new BootstrapInfoBox ().addChildren (new HCDiv ().addChild ("This page lets you change the certificates of multiple endpoints at once. This is e.g. helpful when the old certificate expired."),
                                                               new HCDiv ().addChild ("Currently " +
                                                                                      nTotalEndpointCount +
                                                                                      " endpoints are registered.")));

      final HCTable aTable = new HCTable (new DTCol ("Certificate").setInitialSorting (ESortOrder.ASCENDING),
                                          new DTCol ("Endpoint Count").setDisplayType (EDTColType.INT, aDisplayLocale),
                                          new BootstrapDTColAction (aDisplayLocale)).setID (getID ());
      aGroupedPerURL.forEach ( (sCert, aEndpoints) -> {
        final HCRow aRow = aTable.addBodyRow ();
        aRow.addCell (_getCertificateDisplay (sCert, aDisplayLocale));
        aRow.addCell (Integer.toString (aEndpoints.size ()));

        final ISimpleURL aEditURL = aWPEC.getSelfHref ()
                                         .add (CPageParam.PARAM_ACTION, CPageParam.ACTION_EDIT)
                                         .add (FIELD_OLD_CERTIFICATE, sCert);
        aRow.addCell (new HCA (aEditURL).setTitle ("Change all endpoints using this certificate")
                                        .addChild (EDefaultIcon.EDIT.getAsNode ()));
      });

      final DataTables aDataTables = BootstrapDataTables.createDefaultDataTables (aWPEC, aTable);
      aNodeList.addChild (aTable).addChild (aDataTables);
    }
  }

  @Nonnull
  private static IHCNode _getCertificateDisplay (@Nullable final String sCert, @Nonnull final Locale aDisplayLocale)
  {
    X509Certificate aEndpointCert = null;
    try
    {
      aEndpointCert = CertificateHelper.convertStringToCertficate (sCert);
    }
    catch (final Exception ex)
    {
      // Ignore
    }
    if (aEndpointCert == null)
    {
      final int nDisplayLen = 20;
      final String sCertPart = (sCert.length () > nDisplayLen ? sCert.substring (0, 20) + "..." : sCert);
      return new HCDiv ().addChild ("Invalid certificate" + (sCert.length () > nDisplayLen ? " starting with: " : ": "))
                         .addChild (new HCCode ().addChild (sCertPart));
    }

    final HCNodeList ret = new HCNodeList ();
    ret.addChild (new HCDiv ().addChild ("Issuer: " + aEndpointCert.getIssuerDN ().toString ()));
    ret.addChild (new HCDiv ().addChild ("Subject: " + aEndpointCert.getSubjectDN ().toString ()));
    final LocalDate aNotBefore = PDTFactory.createLocalDate (aEndpointCert.getNotBefore ());
    ret.addChild (new HCDiv ().addChild ("Not before: " + PDTToString.getAsString (aNotBefore, aDisplayLocale)));
    final LocalDate aNotAfter = PDTFactory.createLocalDate (aEndpointCert.getNotAfter ());
    ret.addChild (new HCDiv ().addChild ("Not after: " + PDTToString.getAsString (aNotAfter, aDisplayLocale)));
    return ret;
  }
}
