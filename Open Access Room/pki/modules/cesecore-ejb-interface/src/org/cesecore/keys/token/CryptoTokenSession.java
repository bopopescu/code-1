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
package org.cesecore.keys.token;

/**
 * @version $Id: CryptoTokenSession.java 17625 2013-09-20 07:12:06Z netmackan $
 *
 */
public interface CryptoTokenSession {

    /** @return true if the specified name is already in use by another CryptoToken (checks the database, not the cache) */
    boolean isCryptoTokenNameUsed(String cryptoTokenName);
    
    /** @return the full class name (including package names) for a CryptoToken type */
    public String getClassNameForType(String tokenType);
}
