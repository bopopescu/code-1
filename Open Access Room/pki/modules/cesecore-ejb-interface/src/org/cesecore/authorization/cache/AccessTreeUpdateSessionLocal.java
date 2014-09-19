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
package org.cesecore.authorization.cache;

import javax.ejb.Local;

/**
 * @version $Id: AccessTreeUpdateSessionLocal.java 17625 2013-09-20 07:12:06Z netmackan $
 */
@Local
public interface AccessTreeUpdateSessionLocal {

    /**
     * Returns a reference to the AuthorizationTreeUpdateData
     */
    AccessTreeUpdateData getAccessTreeUpdateData();
    
    /**
     * Method incrementing the authorization tree update number and thereby
     * signaling to other beans that they should reconstruct their access trees.
     */
    public void signalForAccessTreeUpdate();

}
