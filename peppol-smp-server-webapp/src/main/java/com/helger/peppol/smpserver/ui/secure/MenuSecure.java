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

import javax.annotation.Nonnull;
import javax.annotation.concurrent.Immutable;

import com.helger.commons.debug.GlobalDebug;
import com.helger.peppol.smpserver.SMPServerConfiguration;
import com.helger.peppol.smpserver.app.CApp;
import com.helger.peppol.smpserver.domain.SMPMetaManager;
import com.helger.peppol.smpserver.smlhook.RegistrationHookFactory;
import com.helger.photon.basic.app.menu.IMenuItemPage;
import com.helger.photon.basic.app.menu.IMenuObject;
import com.helger.photon.basic.app.menu.IMenuObjectFilter;
import com.helger.photon.basic.app.menu.IMenuTree;
import com.helger.photon.basic.app.menu.filter.AbstractMenuObjectFilter;
import com.helger.photon.bootstrap3.pages.BootstrapPagesMenuConfigurator;
import com.helger.photon.bootstrap3.pages.security.BasePageSecurityChangePassword;
import com.helger.photon.security.menu.MenuObjectFilterUserAssignedToUserGroup;
import com.helger.photon.uicore.page.WebPageExecutionContext;
import com.helger.photon.uicore.page.system.BasePageShowChildren;

@Immutable
public final class MenuSecure
{
  private MenuSecure ()
  {}

  public static void init (@Nonnull final IMenuTree aMenuTree)
  {
    // We need this additional indirection layer, as the pages are initialized
    // statically!
    final MenuObjectFilterUserAssignedToUserGroup aFilterAdministrators = new MenuObjectFilterUserAssignedToUserGroup (CApp.USERGROUP_ADMINISTRATORS_ID);
    final IMenuObjectFilter aFilterPEPPOLDirectory = new AbstractMenuObjectFilter ()
    {
      public boolean test (@Nonnull final IMenuObject aValue)
      {
        return GlobalDebug.isDebugMode () || SMPServerConfiguration.isPEPPOLDirectoryIntegrationEnabled ();
      }
    };
    final IMenuObjectFilter aFilterSMLConnectionActive = new AbstractMenuObjectFilter ()
    {
      public boolean test (@Nonnull final IMenuObject aValue)
      {
        return GlobalDebug.isDebugMode () || RegistrationHookFactory.isSMLConnectionActive ();
      }
    };

    if (SMPMetaManager.getUserMgr ().isSpecialUserManagementNeeded ())
      aMenuTree.createRootItem (new PageSecureUsers (CMenuSecure.MENU_USERS));
    aMenuTree.createRootItem (new PageSecureServiceGroups (CMenuSecure.MENU_SERVICE_GROUPS));
    {
      final IMenuItemPage aEndpoints = aMenuTree.createRootItem (new PageSecureEndpoints (CMenuSecure.MENU_ENDPOINTS));
      aMenuTree.createItem (aEndpoints, new PageSecureEndpointsChangeURL (CMenuSecure.MENU_ENDPOINTS_CHANGE_URL));
    }
    aMenuTree.createRootItem (new PageSecureRedirects (CMenuSecure.MENU_REDIRECTS));
    aMenuTree.createRootItem (new PageSecureBusinessCards (CMenuSecure.MENU_BUSINESS_CARDS))
             .setDisplayFilter (aFilterPEPPOLDirectory);
    aMenuTree.createRootItem (new PageSecureTransportProfile (CMenuSecure.MENU_TRANSPORT_PROFILES));
    aMenuTree.createRootItem (new PageSecureCertificateInformation (CMenuSecure.MENU_CERTIFICATE_INFORMATION));
    aMenuTree.createRootItem (new PageSecureTasks (CMenuSecure.MENU_TASKS));
    {
      final IMenuItemPage aPageSML = aMenuTree.createRootItem (new BasePageShowChildren<> (CMenuSecure.MENU_SML,
                                                                                           "SML",
                                                                                           aMenuTree));
      aMenuTree.createItem (aPageSML, new PageSecureSMLInfo (CMenuSecure.MENU_SML_INFO));
      aMenuTree.createItem (aPageSML, new PageSecureSMLSetup (CMenuSecure.MENU_SML_SETUP))
               .setDisplayFilter (aFilterSMLConnectionActive);
    }
    aMenuTree.createRootSeparator ();

    // Administrator
    {
      final IMenuItemPage aAdmin = aMenuTree.createRootItem (new BasePageShowChildren <WebPageExecutionContext> (CMenuSecure.MENU_ADMIN,
                                                                                                                 "Administration",
                                                                                                                 aMenuTree));
      aMenuTree.createItem (aAdmin,
                            new BasePageSecurityChangePassword <WebPageExecutionContext> (CMenuSecure.MENU_CHANGE_PASSWORD));
      BootstrapPagesMenuConfigurator.addAllItems (aMenuTree, aAdmin, aFilterAdministrators, CApp.DEFAULT_LOCALE);
    }

    // Default menu item
    aMenuTree.setDefaultMenuItemID (CMenuSecure.MENU_SERVICE_GROUPS);
  }
}
