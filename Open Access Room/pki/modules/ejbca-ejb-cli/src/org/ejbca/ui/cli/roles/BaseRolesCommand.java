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

package org.ejbca.ui.cli.roles;

import org.cesecore.authentication.tokens.AuthenticationToken;
import org.cesecore.authorization.AuthorizationDeniedException;
import org.cesecore.authorization.control.StandardRules;
import org.cesecore.certificates.ca.CADoesntExistsException;
import org.cesecore.certificates.ca.CaSessionRemote;
import org.cesecore.util.EjbRemoteHelper;
import org.ejbca.core.ejb.ra.raadmin.EndEntityProfileSessionRemote;
import org.ejbca.core.ejb.ra.userdatasource.UserDataSourceSessionRemote;
import org.ejbca.core.model.authorization.AccessRulesConstants;
import org.ejbca.core.model.ra.raadmin.EndEntityProfileNotFoundException;
import org.ejbca.ui.cli.BaseCommand;

/**
 * Base for Admins commands, contains common functions for Admins operations
 */
public abstract class BaseRolesCommand extends BaseCommand {

    protected static final String OLD_COMMAND = "admins";
    private static final String MAIN_COMMAND = "roles";

    @Override
    public String getMainCommand() {
        return MAIN_COMMAND;
    }
    
    @Override
    public String[] getMainCommandAliases() {
        return new String[]{OLD_COMMAND};
    }
    
    @Override
    public String[] getSubCommandAliases() {
        return new String[]{};
    }
    
    protected String getParsedAccessRule(AuthenticationToken authenticationToken, String resource) throws NumberFormatException, CADoesntExistsException, AuthorizationDeniedException {
        // Check if it is a profile rule, then replace profile id with profile
        // name.
        if (resource.startsWith(AccessRulesConstants.ENDENTITYPROFILEPREFIX)) {
            if (resource.lastIndexOf('/') < AccessRulesConstants.ENDENTITYPROFILEPREFIX.length()) {
                return AccessRulesConstants.ENDENTITYPROFILEPREFIX
                        + ejb.getRemoteSession(EndEntityProfileSessionRemote.class).getEndEntityProfileName(Integer.parseInt(resource.substring(AccessRulesConstants.ENDENTITYPROFILEPREFIX
                                .length())));
            } else {
                String tmpString = resource.substring(AccessRulesConstants.ENDENTITYPROFILEPREFIX.length());
                return AccessRulesConstants.ENDENTITYPROFILEPREFIX
                        + ejb.getRemoteSession(EndEntityProfileSessionRemote.class).getEndEntityProfileName(Integer.parseInt(tmpString.substring(0, tmpString.indexOf('/'))))
                        + tmpString.substring(tmpString.indexOf('/'));
            }
        }
        // Check if it is a CA rule, then replace CA id with CA name.
        if (resource.startsWith(StandardRules.CAACCESS.resource())) {
            if (resource.lastIndexOf('/') < StandardRules.CAACCESS.resource().length()) {
                final int caid = Integer.valueOf(resource.substring(StandardRules.CAACCESS.resource().length()));
                final String caname = EjbRemoteHelper.INSTANCE.getRemoteSession(CaSessionRemote.class).getCAInfo(authenticationToken, caid).getName();
                return StandardRules.CAACCESS.resource() + caname;
            } else {
                final int caid = Integer.valueOf(resource.substring(StandardRules.CAACCESS.resource().length(), resource.lastIndexOf('/')));
                final String caname = EjbRemoteHelper.INSTANCE.getRemoteSession(CaSessionRemote.class).getCAInfo(authenticationToken, caid).getName();
                return StandardRules.CAACCESS.resource()
                        + caname
                        + resource.substring(resource.lastIndexOf('/'));
            }
        }
        // Check if it is a User Data Source rule, then replace User Data Source
        // id with User Data Source name.
        if (resource.startsWith(AccessRulesConstants.USERDATASOURCEPREFIX)) {
            if (resource.lastIndexOf('/') < AccessRulesConstants.USERDATASOURCEPREFIX.length()) {
                return AccessRulesConstants.USERDATASOURCEPREFIX
                        + ejb.getRemoteSession(UserDataSourceSessionRemote.class).getUserDataSourceName(authenticationToken,
                                Integer.parseInt(resource.substring(AccessRulesConstants.USERDATASOURCEPREFIX.length())));
            } else {
                return AccessRulesConstants.USERDATASOURCEPREFIX
                        + ejb.getRemoteSession(UserDataSourceSessionRemote.class).getUserDataSourceName(authenticationToken,
                                Integer.parseInt(resource.substring(AccessRulesConstants.USERDATASOURCEPREFIX.length(), resource.lastIndexOf('/'))))
                        + resource.substring(resource.lastIndexOf('/'));
            }
        }
        return resource;
    }

    protected String getOriginalAccessRule(AuthenticationToken authenticationToken, String resource) throws NumberFormatException,
            CADoesntExistsException, AuthorizationDeniedException, EndEntityProfileNotFoundException {
     // Check if it is a profile rule, then replace profile id with profile
        // name.
        if (resource.startsWith(AccessRulesConstants.ENDENTITYPROFILEPREFIX)) {
            if (resource.lastIndexOf('/') < AccessRulesConstants.ENDENTITYPROFILEPREFIX.length()) {
                return AccessRulesConstants.ENDENTITYPROFILEPREFIX
                        + ejb.getRemoteSession(EndEntityProfileSessionRemote.class).getEndEntityProfileId(resource.substring(AccessRulesConstants.ENDENTITYPROFILEPREFIX.length()));
            } else {
                String tmpString = resource.substring(AccessRulesConstants.ENDENTITYPROFILEPREFIX.length());
                return AccessRulesConstants.ENDENTITYPROFILEPREFIX
                        + ejb.getRemoteSession(EndEntityProfileSessionRemote.class).getEndEntityProfileId(tmpString.substring(0, tmpString.indexOf('/')))
                        + tmpString.substring(tmpString.indexOf('/'));
            }
        }
        // Check if it is a CA rule, then replace CA id with CA name.
        if (resource.startsWith(StandardRules.CAACCESS.resource())) {
            if (resource.lastIndexOf('/') < StandardRules.CAACCESS.resource().length()) {
                return StandardRules.CAACCESS.resource()
                        + EjbRemoteHelper.INSTANCE.getRemoteSession(CaSessionRemote.class).getCAInfo(authenticationToken, resource.substring(StandardRules.CAACCESS.resource().length())).getCAId();
            } else {
                return StandardRules.CAACCESS.resource()
                        + EjbRemoteHelper.INSTANCE.getRemoteSession(CaSessionRemote.class).getCAInfo(authenticationToken, resource.substring(StandardRules.CAACCESS.resource().length(), resource.lastIndexOf('/'))).getCAId()
                        + resource.substring(resource.lastIndexOf('/'));
            }
        }
        // Check if it is a User Data Source rule, then replace User Data Source
        // id with User Data Source name.
        if (resource.startsWith(AccessRulesConstants.USERDATASOURCEPREFIX)) {
            if (resource.lastIndexOf('/') < AccessRulesConstants.USERDATASOURCEPREFIX.length()) {
                return AccessRulesConstants.USERDATASOURCEPREFIX
                        + ejb.getRemoteSession(UserDataSourceSessionRemote.class).getUserDataSourceId(authenticationToken, resource.substring(AccessRulesConstants.USERDATASOURCEPREFIX.length()));
            } else {
                return AccessRulesConstants.USERDATASOURCEPREFIX
                        + ejb.getRemoteSession(UserDataSourceSessionRemote.class).getUserDataSourceId(authenticationToken,
                                resource.substring(AccessRulesConstants.USERDATASOURCEPREFIX.length(), resource.lastIndexOf('/')))
                        + resource.substring(resource.lastIndexOf('/'));
            }
        }
        return resource;
    }
}
