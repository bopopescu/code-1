/*************************************************************************
 *                                                                       *
 *  EJBCA: The OpenSource Certificate Authority                          *
 *                                                                       *
 *  This software is free software; you can redistribute it and/or       *
 *  modify it under the terms of the GNU Lesser General Public           *
 *  License as published by the Free Software Foundation; either         *
 *  version 2.1 of the License, or any later version.                    *
 *                                                                       *
 *  See terms of license at gnu.org.                                     *
 *                                                                       *
 *************************************************************************/
 
package org.ejbca.core.model.authorization;

import org.cesecore.authorization.control.AuditLogRules;
import org.cesecore.authorization.control.StandardRules;

/**
 * @version $Id: AccessRulesConstants.java 17085 2013-06-11 15:50:50Z mikekushner $
 */
public abstract class AccessRulesConstants {

    // Available end entity profile authorization rules.
    public static final String VIEW_RIGHTS                                = "/view_end_entity";
    public static final String EDIT_RIGHTS                                = "/edit_end_entity";
    public static final String CREATE_RIGHTS                              = "/create_end_entity";
    public static final String DELETE_RIGHTS                              = "/delete_end_entity";
    public static final String REVOKE_RIGHTS                              = "/revoke_end_entity";
    public static final String HISTORY_RIGHTS                             = "/view_end_entity_history";
    public static final String APPROVAL_RIGHTS                            = "/approve_end_entity";

    public static final String HARDTOKEN_RIGHTS                           = "/view_hardtoken";
    public static final String HARDTOKEN_PUKDATA_RIGHTS                   = "/view_hardtoken/puk_data";

    public static final String  KEYRECOVERY_RIGHTS                        = "/keyrecovery";    
    
    // Endings used in profile authorization.
    public static final String[] ENDENTITYPROFILE_ENDINGS = {VIEW_RIGHTS,EDIT_RIGHTS,CREATE_RIGHTS,DELETE_RIGHTS,REVOKE_RIGHTS,HISTORY_RIGHTS,APPROVAL_RIGHTS};
    
    // Name of end entity profile prefix directory in authorization module.
    public static final String ENDENTITYPROFILEBASE                       = "/endentityprofilesrules";
    public static final String ENDENTITYPROFILEPREFIX                     = "/endentityprofilesrules/";

    // Name of end entity profile prefix directory in authorization module.
    public static final String USERDATASOURCEBASE                         = "/userdatasourcesrules";
    public static final String USERDATASOURCEPREFIX                       = "/userdatasourcesrules/";
    
    public static final String UDS_FETCH_RIGHTS                           = "/fetch_userdata";
    public static final String UDS_REMOVE_RIGHTS                          = "/remove_userdata";
    
    // Endings used in profile authorization.
    public static final String[]  USERDATASOURCE_ENDINGS = {UDS_FETCH_RIGHTS,UDS_REMOVE_RIGHTS};

    // CA access rules are managed in CESecore, see StandardRules

    public static final String ROLE_PUBLICWEBUSER                         = "/public_web_user";
    public static final String ROLE_ADMINISTRATOR                         = "/administrator";
    public static final String REGULAR_CAFUNCTIONALTY                     = StandardRules.CAFUNCTIONALITY.resource();
    public static final String REGULAR_CABASICFUNCTIONS                   = StandardRules.CAFUNCTIONALITY.resource()+"/basic_functions";
    public static final String REGULAR_ACTIVATECA                         = REGULAR_CABASICFUNCTIONS+"/activate_ca";    
    public static final String REGULAR_RENEWCA                            = StandardRules.CAFUNCTIONALITY.resource()+"/renew_ca";    
    public static final String REGULAR_VIEWCERTIFICATE                    = StandardRules.CAFUNCTIONALITY.resource()+"/view_certificate";    
    public static final String REGULAR_APPROVECAACTION                    = StandardRules.CAFUNCTIONALITY.resource()+"/approve_caaction";
    public static final String REGULAR_CREATECRL                          = StandardRules.CREATECRL.resource();    
    public static final String REGULAR_EDITCERTIFICATEPROFILES            = StandardRules.EDITCERTIFICATEPROFILE.resource();    
    public static final String REGULAR_CREATECERTIFICATE                  = StandardRules.CREATECERT.resource();
    public static final String REGULAR_STORECERTIFICATE                   = StandardRules.CAFUNCTIONALITY.resource()+"/store_certificate";    
    public static final String REGULAR_EDITPUBLISHER                      = StandardRules.CAFUNCTIONALITY.resource()+"/edit_publisher";    
    public static final String REGULAR_RAFUNCTIONALITY                    = "/ra_functionality";
    public static final String REGULAR_EDITENDENTITYPROFILES              = "/ra_functionality/edit_end_entity_profiles";
    public static final String REGULAR_EDITUSERDATASOURCES                = "/ra_functionality/edit_user_data_sources";
    public static final String REGULAR_APPROVEENDENTITY                   = "/ra_functionality"+APPROVAL_RIGHTS;
    // REGULAR_REVOKEENDENTITY is used when revoking the certificate of a user
    public static final String REGULAR_REVOKEENDENTITY                    = "/ra_functionality"+REVOKE_RIGHTS;    
    // The rules below seem to be for rights to certificates, and ae mostly used from WS for token certificates and CMP for token certificates
    // You can question if these are valid and right?
    // Some of them are unused if you check references here, but admin GUI contains directly the string /ra_functionality instead, just to make things hard
    public static final String REGULAR_VIEWENDENTITY                      = "/ra_functionality"+VIEW_RIGHTS; // Unused, but exists as "raw" string
    public static final String REGULAR_CREATEENDENTITY                    = "/ra_functionality"+CREATE_RIGHTS;
    public static final String REGULAR_EDITENDENTITY                      = "/ra_functionality"+EDIT_RIGHTS ;
    public static final String REGULAR_DELETEENDENTITY                    = "/ra_functionality"+DELETE_RIGHTS; // Unused, but exists as "raw" string
    public static final String REGULAR_VIEWENDENTITYHISTORY               = "/ra_functionality"+HISTORY_RIGHTS; // Unused, but exists as "raw" string

    public static final String REGULAR_SYSTEMFUNCTIONALITY                = StandardRules.SYSTEMFUNCTIONALITY.resource(); // Unused but the "raw" string /system_functionality is present in admin GUI pages
    public static final String REGULAR_EDITSYSTEMCONFIGURATION            = StandardRules.SYSTEMFUNCTIONALITY.resource()+"/edit_systemconfiguration";

    public static final String REGULAR_VIEWHARDTOKENS                     = "/ra_functionality" + HARDTOKEN_RIGHTS;
    public static final String REGULAR_VIEWPUKS                           = "/ra_functionality" + HARDTOKEN_PUKDATA_RIGHTS;
    public static final String REGULAR_KEYRECOVERY                        = "/ra_functionality" + KEYRECOVERY_RIGHTS;
    	
    public static final String HARDTOKEN_HARDTOKENFUNCTIONALITY           = "/hardtoken_functionality";
    public static final String HARDTOKEN_EDITHARDTOKENISSUERS             = "/hardtoken_functionality/edit_hardtoken_issuers";
    public static final String HARDTOKEN_EDITHARDTOKENPROFILES            = "/hardtoken_functionality/edit_hardtoken_profiles";
    public static final String HARDTOKEN_ISSUEHARDTOKENS                  = "/hardtoken_functionality/issue_hardtokens";
    public static final String HARDTOKEN_ISSUEHARDTOKENADMINISTRATORS     = "/hardtoken_functionality/issue_hardtoken_administrators";
    
    // Standard Regular Access Rules
    public static final String[] STANDARDREGULARACCESSRULES = {REGULAR_CAFUNCTIONALTY, 
                                                           REGULAR_CABASICFUNCTIONS,
                                                           REGULAR_ACTIVATECA,
                                                           REGULAR_RENEWCA,
                                                           REGULAR_VIEWCERTIFICATE, 
                                                           REGULAR_CREATECRL,
                                                           REGULAR_EDITCERTIFICATEPROFILES,                                                           
                                                           REGULAR_CREATECERTIFICATE,
                                                           REGULAR_STORECERTIFICATE,
                                                           REGULAR_EDITPUBLISHER,
                                                           REGULAR_APPROVECAACTION,
                                                           REGULAR_RAFUNCTIONALITY, 
                                                           REGULAR_EDITENDENTITYPROFILES,
                                                           REGULAR_EDITUSERDATASOURCES,                                                           
                                                           REGULAR_VIEWENDENTITY,
                                                           REGULAR_CREATEENDENTITY, 
                                                           REGULAR_EDITENDENTITY, 
                                                           REGULAR_DELETEENDENTITY,
                                                           REGULAR_REVOKEENDENTITY,
                                                           REGULAR_VIEWENDENTITYHISTORY,
                                                           REGULAR_APPROVEENDENTITY,
                                                           AuditLogRules.LOG.resource(),
                                                           AuditLogRules.LOG_CUSTOM.resource(),  
                                                           AuditLogRules.VIEW.resource(),
                                                           AuditLogRules.CONFIGURE.resource(),
                                                           REGULAR_SYSTEMFUNCTIONALITY,
                                                           StandardRules.EDITROLES.resource(),
                                                           REGULAR_EDITSYSTEMCONFIGURATION};
                                                       
    // Role Access Rules
    public static final  String[] ROLEACCESSRULES = {ROLE_PUBLICWEBUSER, ROLE_ADMINISTRATOR, StandardRules.ROLE_ROOT.resource()};
                                                        
    // Hard Token specific accessrules used in authorization module.
    public static final String[] HARDTOKENACCESSRULES = 
       	  {HARDTOKEN_HARDTOKENFUNCTIONALITY,
    		HARDTOKEN_EDITHARDTOKENISSUERS,
			HARDTOKEN_EDITHARDTOKENPROFILES,     
			HARDTOKEN_ISSUEHARDTOKENS,
			HARDTOKEN_ISSUEHARDTOKENADMINISTRATORS};
}
