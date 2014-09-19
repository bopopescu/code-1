/*************************************************************************
 *                                                                       *
 *  CESeCore: CE Security Core                                           *
 *                                                                       *
 *  This software is free software; you can redistribute it and/or       *
 *  modify it under the terms of the GNU Lesser General Public           *
 *  License as published by the Free Software Foundation; either         *
 *  version 2.1 of the License, or any later version.                    *
 *                                                                       *
 *  See terms of license at gnu.org.                                     *
 *                                                                       *
 *************************************************************************/
package org.cesecore.roles.access;

import java.util.ArrayList;
import java.util.Collection;

import javax.ejb.Stateless;
import javax.ejb.TransactionAttribute;
import javax.ejb.TransactionAttributeType;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.persistence.Query;

import org.cesecore.config.CesecoreConfiguration;
import org.cesecore.jndi.JndiConstants;
import org.cesecore.roles.RoleData;
import org.cesecore.util.QueryResultWrapper;

/**
 * @version $Id: RoleAccessSessionBean.java 17625 2013-09-20 07:12:06Z netmackan $
 *
 */
@Stateless(mappedName = JndiConstants.APP_JNDI_PREFIX + "RoleAccessSessionRemote")
@TransactionAttribute(TransactionAttributeType.SUPPORTS)
public class RoleAccessSessionBean implements RoleAccessSessionLocal, RoleAccessSessionRemote {

    @PersistenceContext(unitName = CesecoreConfiguration.PERSISTENCE_UNIT)
    private EntityManager entityManager;
    
    @SuppressWarnings("unchecked")
    @Override
    @TransactionAttribute(TransactionAttributeType.SUPPORTS)
    public Collection<RoleData> getAllRoles() {
        final Query query = entityManager.createQuery("SELECT a FROM RoleData a");
        return (query.getResultList() != null ? query.getResultList() : new ArrayList<RoleData>());
    }


    @Override
    @TransactionAttribute(TransactionAttributeType.SUPPORTS)
    public RoleData findRole(final String roleName) {
        final Query query = entityManager.createQuery("SELECT a FROM RoleData a WHERE a.roleName=:roleName");
        query.setParameter("roleName", roleName);
        return (RoleData) QueryResultWrapper.getSingleResult(query);
    }

    @Override
    @TransactionAttribute(TransactionAttributeType.SUPPORTS)
    public RoleData findRole(final Integer primaryKey) {
        final Query query = entityManager.createQuery("SELECT a FROM RoleData a WHERE a.primaryKey=:primaryKey");
        query.setParameter("primaryKey", primaryKey);

        return (RoleData) QueryResultWrapper.getSingleResult(query);
    }
    
}
