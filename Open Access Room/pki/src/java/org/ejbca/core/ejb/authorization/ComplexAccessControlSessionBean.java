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
package org.ejbca.core.ejb.authorization;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.ejb.EJB;
import javax.ejb.Stateless;
import javax.ejb.TransactionAttribute;
import javax.ejb.TransactionAttributeType;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.persistence.Query;

import org.apache.log4j.Logger;
import org.cesecore.authentication.tokens.AuthenticationToken;
import org.cesecore.authentication.tokens.X509CertificateAuthenticationToken;
import org.cesecore.authorization.AuthorizationDeniedException;
import org.cesecore.authorization.cache.AccessTreeUpdateSessionLocal;
import org.cesecore.authorization.control.AccessControlSessionLocal;
import org.cesecore.authorization.control.AuditLogRules;
import org.cesecore.authorization.control.CryptoTokenRules;
import org.cesecore.authorization.control.StandardRules;
import org.cesecore.authorization.rules.AccessRuleData;
import org.cesecore.authorization.rules.AccessRuleNotFoundException;
import org.cesecore.authorization.rules.AccessRuleState;
import org.cesecore.authorization.user.AccessMatchType;
import org.cesecore.authorization.user.AccessUserAspectData;
import org.cesecore.authorization.user.matchvalues.AccessMatchValue;
import org.cesecore.authorization.user.matchvalues.AccessMatchValueReverseLookupRegistry;
import org.cesecore.authorization.user.matchvalues.X500PrincipalAccessMatchValue;
import org.cesecore.certificates.ca.CAData;
import org.cesecore.certificates.ca.CaSessionLocal;
import org.cesecore.config.CesecoreConfiguration;
import org.cesecore.jndi.JndiConstants;
import org.cesecore.keys.token.CryptoTokenSessionLocal;
import org.cesecore.roles.RoleData;
import org.cesecore.roles.RoleExistsException;
import org.cesecore.roles.RoleNotFoundException;
import org.cesecore.roles.access.RoleAccessSessionLocal;
import org.cesecore.roles.management.RoleManagementSessionLocal;
import org.cesecore.util.ValueExtractor;
import org.ejbca.config.EjbcaConfiguration;
import org.ejbca.core.ejb.authentication.cli.CliUserAccessMatchValue;
import org.ejbca.core.ejb.ra.UserData;
import org.ejbca.core.model.SecConst;
import org.ejbca.core.model.authorization.AccessRulesConstants;

/**
 * This session bean handles complex authorization queries.
 * 
 * @version $Id: ComplexAccessControlSessionBean.java 16239 2013-01-30 11:56:54Z mikekushner $
 * 
 */
@Stateless(mappedName = JndiConstants.APP_JNDI_PREFIX + "ComplexAccessControlSessionRemote")
@TransactionAttribute(TransactionAttributeType.SUPPORTS)
public class ComplexAccessControlSessionBean implements ComplexAccessControlSessionLocal, ComplexAccessControlSessionRemote {

    private static final Logger log = Logger.getLogger(ComplexAccessControlSessionBean.class);

    @EJB
    private AccessControlSessionLocal accessControlSession;
    @EJB
    private AccessTreeUpdateSessionLocal accessTreeUpdateSession;
    @EJB
    private CaSessionLocal caSession;
    @EJB
    private CryptoTokenSessionLocal cryptoTokenSession;
    @EJB
    private RoleAccessSessionLocal roleAccessSession;
    @EJB
    private RoleManagementSessionLocal roleMgmtSession;

    @PersistenceContext(unitName = CesecoreConfiguration.PERSISTENCE_UNIT)
    private EntityManager entityManager;

    @TransactionAttribute(TransactionAttributeType.REQUIRED)
    @Override
    public void initializeAuthorizationModule() {
        Collection<RoleData> roles = roleAccessSession.getAllRoles();
        List<CAData> cas = CAData.findAll(entityManager);
        if ((roles.size() == 0) && (cas.size() == 0)) {
            log.info("No roles or CAs exist, intializing Super Administrator Role with default CLI user.");
            createSuperAdministrator();
        } else {
            log.info("Roles or CAs exist, not intializing " + SUPERADMIN_ROLE);
        }
    }
    
    @TransactionAttribute(TransactionAttributeType.REQUIRED)
    @Override
    public void createSuperAdministrator() {
        //Create the GUI Super Admin
        RoleData role = roleAccessSession.findRole(SUPERADMIN_ROLE);
        Map<Integer, AccessUserAspectData> newUsers = new HashMap<Integer, AccessUserAspectData>();   
        RoleData oldSuperAdminRole = roleAccessSession.findRole(TEMPORARY_SUPERADMIN_ROLE);
        if (role == null) {
            log.debug("Creating new role '" + SUPERADMIN_ROLE + "'.");
            role = new RoleData(1, SUPERADMIN_ROLE);
            entityManager.persist(role);
        } else {
            log.debug("'" + SUPERADMIN_ROLE + "' already exists, not creating new.");            
        }

        Map<Integer, AccessRuleData> rules = role.getAccessRules();
        AccessRuleData rule = new AccessRuleData(SUPERADMIN_ROLE, StandardRules.ROLE_ROOT.resource(), AccessRuleState.RULE_ACCEPT, true);
        if (!rules.containsKey(rule.getPrimaryKey())) {
            log.debug("Adding new rule '/' to " + SUPERADMIN_ROLE + ".");
            Map<Integer, AccessRuleData> newrules = new HashMap<Integer, AccessRuleData>();
            newrules.put(rule.getPrimaryKey(), rule);
            role.setAccessRules(newrules);
        } else {
            log.debug("rule '/' already exists in " + SUPERADMIN_ROLE + ".");
        }
        //Pick up the aspects from the old temp. super admin group and add them to the new one.        
        if (oldSuperAdminRole != null) {
            Map<Integer, AccessUserAspectData> oldSuperAdminAspects = oldSuperAdminRole.getAccessUsers();
            Map<Integer, AccessUserAspectData> existingSuperAdminAspects = role.getAccessUsers();
            for (AccessUserAspectData aspect : oldSuperAdminAspects.values()) {
                AccessMatchValue matchWith = AccessMatchValueReverseLookupRegistry.INSTANCE.performReverseLookup(
                        X509CertificateAuthenticationToken.TOKEN_TYPE, aspect.getMatchWith());
                AccessUserAspectData superAdminUserAspect = new AccessUserAspectData(SUPERADMIN_ROLE, aspect.getCaId(), matchWith,
                        aspect.getMatchTypeAsType(), aspect.getMatchValue());
                if (existingSuperAdminAspects.containsKey(superAdminUserAspect.getPrimaryKey())) {
                    log.debug(SUPERADMIN_ROLE + " already contains aspect matching " + aspect.getMatchValue() + " for CA with ID " + aspect.getCaId());
                } else {
                    newUsers.put(superAdminUserAspect.getPrimaryKey(), superAdminUserAspect);
                }
            }
        }
           
        //Create the CLI Default User
        Map<Integer, AccessUserAspectData> users = role.getAccessUsers();
        AccessUserAspectData defaultCliUserAspect = new AccessUserAspectData(SUPERADMIN_ROLE, 0, CliUserAccessMatchValue.USERNAME,
                AccessMatchType.TYPE_EQUALCASE, EjbcaConfiguration.getCliDefaultUser());
        if (!users.containsKey(defaultCliUserAspect.getPrimaryKey())) {
            log.debug("Adding new AccessUserAspect '"+EjbcaConfiguration.getCliDefaultUser()+"' to " + SUPERADMIN_ROLE + ".");
              
            newUsers.put(defaultCliUserAspect.getPrimaryKey(), defaultCliUserAspect);
            UserData defaultCliUserData = new UserData(EjbcaConfiguration.getCliDefaultUser(), EjbcaConfiguration.getCliDefaultPassword(), false, "UID="
                    + EjbcaConfiguration.getCliDefaultUser(), 0, null, null, null, 0, SecConst.EMPTY_ENDENTITYPROFILE, 0, 0, 0, null);
            entityManager.persist(defaultCliUserData);
        } else {
            log.debug("AccessUserAspect '"+EjbcaConfiguration.getCliDefaultUser()+"' already exists in " + SUPERADMIN_ROLE + ".");            
        }
        //Add all created aspects to role
        role.setAccessUsers(newUsers);
        
    }

    public void initializeAuthorizationModule(AuthenticationToken admin, int caid, String superAdminCN) throws RoleExistsException,
            AuthorizationDeniedException, AccessRuleNotFoundException, RoleNotFoundException {
        if (log.isTraceEnabled()) {
            log.trace(">initializeAuthorizationModule(" + caid + ", " + superAdminCN);
        }
        // In this method we need to use the entityManager explicitly instead of the role management session bean.
        // This is because it is also used to initialize the first rule that will allow the AlwayAllowAuthenticationToken to do anything.
        // Without this role and access rule we are not authorized to use the role management session bean
        RoleData role = roleAccessSession.findRole(SUPERADMIN_ROLE);
        if (role == null) {
            log.debug("Creating new role '" + SUPERADMIN_ROLE + "'.");
            roleMgmtSession.create(admin, SUPERADMIN_ROLE);
        }
        Map<Integer, AccessRuleData> rules = role.getAccessRules();
        AccessRuleData rule = new AccessRuleData(SUPERADMIN_ROLE, StandardRules.ROLE_ROOT.resource(), AccessRuleState.RULE_ACCEPT, true);
        if (!rules.containsKey(rule.getPrimaryKey())) {
            log.debug("Adding new rule '/' to " + SUPERADMIN_ROLE + ".");
            Collection<AccessRuleData> newrules = new ArrayList<AccessRuleData>();
            newrules.add(rule);
            roleMgmtSession.addAccessRulesToRole(admin, role, newrules);
        }
        Map<Integer, AccessUserAspectData> users = role.getAccessUsers();
        AccessUserAspectData aua = new AccessUserAspectData(SUPERADMIN_ROLE, caid, X500PrincipalAccessMatchValue.WITH_COMMONNAME, AccessMatchType.TYPE_EQUALCASE,
                superAdminCN);
        if (!users.containsKey(aua.getPrimaryKey())) {
            log.debug("Adding new AccessUserAspect for '" + superAdminCN + "' to " + SUPERADMIN_ROLE + ".");
            Collection<AccessUserAspectData> subjects = new ArrayList<AccessUserAspectData>();
            subjects.add(aua);
            roleMgmtSession.addSubjectsToRole(admin, role, subjects);
        }
        accessTreeUpdateSession.signalForAccessTreeUpdate();
        accessControlSession.forceCacheExpire();
        if (log.isTraceEnabled()) {
            log.trace("<initializeAuthorizationModule(" + caid + ", " + superAdminCN);
        }
    }

    @TransactionAttribute(TransactionAttributeType.SUPPORTS)
    @Override
    public Collection<String> getAuthorizedAvailableAccessRules(AuthenticationToken authenticationToken, boolean enableendentityprofilelimitations,
            boolean usehardtokenissuing, boolean usekeyrecovery, Collection<Integer> authorizedEndEntityProfileIds,
            Collection<Integer> authorizedUserDataSourceIds, String[] customaccessrules) {
        if (log.isTraceEnabled()) {
            log.trace(">getAuthorizedAvailableAccessRules");
        }
        ArrayList<String> accessrules = new ArrayList<String>();

        accessrules.add(AccessRulesConstants.ROLEACCESSRULES[0]);
        accessrules.add(AccessRulesConstants.ROLEACCESSRULES[1]);
        if (accessControlSession.isAuthorizedNoLogging(authenticationToken, StandardRules.ROLE_ROOT.resource())) {
            accessrules.add(StandardRules.ROLE_ROOT.resource());
        }
        if (accessControlSession.isAuthorizedNoLogging(authenticationToken, StandardRules.ROLE_ROOT.resource())) {
            accessrules.add(StandardRules.ROLE_ROOT.resource());
        }

        // Insert Standard Access Rules.
        for (int i = 0; i < AccessRulesConstants.STANDARDREGULARACCESSRULES.length; i++) {
            if (accessControlSession.isAuthorizedNoLogging(authenticationToken, AccessRulesConstants.STANDARDREGULARACCESSRULES[i])) {
                accessrules.add(AccessRulesConstants.STANDARDREGULARACCESSRULES[i]);
            }
        }
  
        for(AuditLogRules rule : AuditLogRules.values()) {
            if(accessControlSession.isAuthorizedNoLogging(authenticationToken, rule.resource())) {
                accessrules.add(rule.resource());
            }
        }
        for (CryptoTokenRules rule : CryptoTokenRules.values()) {
            final String fullRule = rule.resource();
            if (accessControlSession.isAuthorizedNoLogging(authenticationToken, fullRule)) {
                accessrules.add(fullRule);
            }
        }
        final List<Integer> allCryptoTokenIds = cryptoTokenSession.getCryptoTokenIds();
        for (Integer cryptoTokenId : allCryptoTokenIds) {
            for (CryptoTokenRules rule : CryptoTokenRules.values()) {
                if (!rule.equals(CryptoTokenRules.BASE) && !rule.equals(CryptoTokenRules.MODIFY_CRYPTOTOKEN) && !rule.equals(CryptoTokenRules.DELETE_CRYPTOTOKEN)) {
                    final String fullRule = rule.resource() + "/" + cryptoTokenId;
                    if (accessControlSession.isAuthorizedNoLogging(authenticationToken, fullRule)) {
                        accessrules.add(fullRule);
                    }
                }
            }
        }
        if (usehardtokenissuing) {
            for (int i = 0; i < AccessRulesConstants.HARDTOKENACCESSRULES.length; i++) {
                accessrules.add(AccessRulesConstants.HARDTOKENACCESSRULES[i]);
            }
            if (accessControlSession.isAuthorizedNoLogging(authenticationToken, AccessRulesConstants.REGULAR_VIEWHARDTOKENS)) {
                accessrules.add(AccessRulesConstants.REGULAR_VIEWHARDTOKENS);
            }
            if (accessControlSession.isAuthorizedNoLogging(authenticationToken, AccessRulesConstants.REGULAR_VIEWPUKS)) {
                accessrules.add(AccessRulesConstants.REGULAR_VIEWPUKS);
            }
        }

        if (usekeyrecovery) {
            if (accessControlSession.isAuthorizedNoLogging(authenticationToken, AccessRulesConstants.REGULAR_KEYRECOVERY)) {
                accessrules.add(AccessRulesConstants.REGULAR_KEYRECOVERY);
            }
        }

        if (enableendentityprofilelimitations) {
            // Add most basic rule if authorized to it.
            if (accessControlSession.isAuthorizedNoLogging(authenticationToken, AccessRulesConstants.ENDENTITYPROFILEBASE)) {
                accessrules.add(AccessRulesConstants.ENDENTITYPROFILEBASE);
            } else {
                // Add it to SuperAdministrator anyway
                if (accessControlSession.isAuthorizedNoLogging(authenticationToken, StandardRules.ROLE_ROOT.resource())) {
                    accessrules.add(AccessRulesConstants.ENDENTITYPROFILEBASE);
                }
            }
            // Add all authorized End Entity Profiles
            for (int profileid : authorizedEndEntityProfileIds) {
                // Administrator is authorized to this End Entity Profile, add it.
                if (accessControlSession.isAuthorizedNoLogging(authenticationToken, AccessRulesConstants.ENDENTITYPROFILEPREFIX + profileid)) {
                    accessrules.add(AccessRulesConstants.ENDENTITYPROFILEPREFIX + profileid);
                    for (int j = 0; j < AccessRulesConstants.ENDENTITYPROFILE_ENDINGS.length; j++) {
                        accessrules.add(AccessRulesConstants.ENDENTITYPROFILEPREFIX + profileid + AccessRulesConstants.ENDENTITYPROFILE_ENDINGS[j]);
                    }
                    if (usehardtokenissuing) {
                        accessrules.add(AccessRulesConstants.ENDENTITYPROFILEPREFIX + profileid + AccessRulesConstants.HARDTOKEN_RIGHTS);
                        accessrules.add(AccessRulesConstants.ENDENTITYPROFILEPREFIX + profileid + AccessRulesConstants.HARDTOKEN_PUKDATA_RIGHTS);
                    }
                    if (usekeyrecovery) {
                        accessrules.add(AccessRulesConstants.ENDENTITYPROFILEPREFIX + profileid + AccessRulesConstants.KEYRECOVERY_RIGHTS);
                    }
                }
            }
        }
        // Insert User data source access rules
        if (accessControlSession.isAuthorizedNoLogging(authenticationToken, AccessRulesConstants.USERDATASOURCEBASE)) {
            accessrules.add(AccessRulesConstants.USERDATASOURCEBASE);
        }
        for (int id : authorizedUserDataSourceIds) {
            if (accessControlSession.isAuthorizedNoLogging(authenticationToken, AccessRulesConstants.USERDATASOURCEPREFIX + id
                    + AccessRulesConstants.UDS_FETCH_RIGHTS)) {
                accessrules.add(AccessRulesConstants.USERDATASOURCEPREFIX + id + AccessRulesConstants.UDS_FETCH_RIGHTS);
            }
            if (accessControlSession.isAuthorizedNoLogging(authenticationToken, AccessRulesConstants.USERDATASOURCEPREFIX + id
                    + AccessRulesConstants.UDS_REMOVE_RIGHTS)) {
                accessrules.add(AccessRulesConstants.USERDATASOURCEPREFIX + id + AccessRulesConstants.UDS_REMOVE_RIGHTS);
            }
        }
        // Insert available CA access rules
        if (accessControlSession.isAuthorizedNoLogging(authenticationToken, StandardRules.CAACCESSBASE.resource())) {
            accessrules.add(StandardRules.CAACCESSBASE.resource());
        }
        for (int caId : getAuthorizedCAIds(authenticationToken)) {
            accessrules.add(StandardRules.CAACCESS.resource() + caId);
        }

        // Insert custom access rules
        for (int i = 0; i < customaccessrules.length; i++) {
            if (!customaccessrules[i].trim().equals("")) {
                if (accessControlSession.isAuthorizedNoLogging(authenticationToken, customaccessrules[i].trim())) {
                    accessrules.add(customaccessrules[i].trim());
                }

            }
        }
        if (log.isTraceEnabled()) {
            log.trace("<getAuthorizedAvailableAccessRules");
        }
        return accessrules;
    }

    @Override
    public Collection<Integer> getAuthorizedEndEntityProfileIds(AuthenticationToken admin, String rapriviledge,
            Collection<Integer> availableEndEntityProfileId) {
        ArrayList<Integer> returnval = new ArrayList<Integer>();
        Iterator<Integer> iter = availableEndEntityProfileId.iterator();
        while (iter.hasNext()) {
            Integer profileid = iter.next();
            if (accessControlSession.isAuthorizedNoLogging(admin, AccessRulesConstants.ENDENTITYPROFILEPREFIX + profileid + rapriviledge)) {
                returnval.add(profileid);
            } else {
                if (log.isDebugEnabled()) {
                    log.debug("Admin not authorized to end entity profile: " + profileid);
                }
            }
        }
        return returnval;
    }

    @TransactionAttribute(TransactionAttributeType.SUPPORTS)
    @Override
    public boolean existsEndEntityProfileInRules(int profileid) {
        if (log.isTraceEnabled()) {
            log.trace(">existsEndEntityProfileInRules(" + profileid + ")");
        }
        final String whereClause = "accessRule = '" + AccessRulesConstants.ENDENTITYPROFILEPREFIX + profileid + "' OR accessRule LIKE '"
                + AccessRulesConstants.ENDENTITYPROFILEPREFIX + profileid + "/%'";
        Query query = entityManager.createNativeQuery("SELECT COUNT(*) FROM AccessRulesData a WHERE " + whereClause);
        long count = ValueExtractor.extractLongValue(query.getSingleResult());
        if (log.isTraceEnabled()) {
            log.trace("<existsEndEntityProfileInRules(" + profileid + "): " + count);
        }
        return count > 0;
    }
    
    /**
     * Method used to return an ArrayList of Integers indicating which CAids an administrator is authorized to access.
     * 
     * @return Collection of Integer
     */
    private Collection<Integer> getAuthorizedCAIds(AuthenticationToken admin) {
        List<Integer> returnval = new ArrayList<Integer>();
        for (Integer caid : caSession.getAvailableCAs()) {
            if (accessControlSession.isAuthorizedNoLogging(admin, StandardRules.CAACCESS.resource() + caid.toString())) {
                returnval.add(caid);
            } else {
                if (log.isDebugEnabled()) {
                    log.debug("Admin not authorized to CA: " + caid);
                }
            }
        }
        return returnval;
    }

}
