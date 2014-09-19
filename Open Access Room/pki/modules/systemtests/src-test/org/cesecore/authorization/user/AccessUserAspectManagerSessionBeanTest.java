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
package org.cesecore.authorization.user;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;

import org.cesecore.authorization.user.matchvalues.X500PrincipalAccessMatchValue;
import org.cesecore.roles.RoleData;
import org.cesecore.util.EjbRemoteHelper;
import org.junit.Test;

/**
 * Functional tests for the AccessUserAspectManagerSessionBean class.
 * 
 * @version $Id: AccessUserAspectManagerSessionBeanTest.java 16148 2013-01-19 17:04:56Z anatom $
 * 
 */
public class AccessUserAspectManagerSessionBeanTest {

    AccessUserAspectManagerTestSessionRemote accessUserAspectManagerSession = EjbRemoteHelper.INSTANCE.getRemoteSession(AccessUserAspectManagerTestSessionRemote.class, EjbRemoteHelper.MODULE_TEST);

    /**
     * Simple sanity test, meant to involve other session beans as little as possible.
     */
    @Test
    public void testCrudOperations() {

        final RoleData role = new RoleData(1337, "NerdHerder");
        final int caId = 4711;

        AccessUserAspectData result = null;
        Integer primaryKey = AccessUserAspectData.generatePrimaryKey(role.getRoleName(), caId, X500PrincipalAccessMatchValue.WITH_COUNTRY,
                AccessMatchType.TYPE_EQUALCASE, "SE");

        assertNull("accessUserAspectManagerSession.find did not return null for a non existing object.",
                accessUserAspectManagerSession.find(primaryKey));

        try {
            result = accessUserAspectManagerSession.create(role, caId, X500PrincipalAccessMatchValue.WITH_COUNTRY,
                    AccessMatchType.TYPE_EQUALCASE, "SE");
        } catch (AccessUserAspectExistsException e) {
            fail("You're probably running this test from a dirty database.");
        }
        try {
            assertNotNull("AccessUserAspect was not persisted sucessfully", accessUserAspectManagerSession.find(primaryKey));
        } finally {
            accessUserAspectManagerSession.remove(result);
            assertNull("AccessUserAspectManagerSessionRemote did not properly remove an object.",
                    accessUserAspectManagerSession.find(primaryKey));
        }
    }

}
