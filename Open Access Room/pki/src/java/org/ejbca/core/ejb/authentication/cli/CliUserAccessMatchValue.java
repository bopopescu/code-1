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
package org.ejbca.core.ejb.authentication.cli;

import org.cesecore.authorization.user.matchvalues.AccessMatchValue;
import org.cesecore.authorization.user.matchvalues.AccessMatchValueReverseLookupRegistry;

/**
 * @version $Id: CliUserAccessMatchValue.java 16118 2013-01-11 13:38:45Z jeklund $
 *
 */
public enum CliUserAccessMatchValue implements AccessMatchValue {
    USERNAME(0);

    static {
        AccessMatchValueReverseLookupRegistry.INSTANCE.register(CliUserAccessMatchValue.values());
    }
    
    private final int numericValue;

    private CliUserAccessMatchValue(int numericValue) {
        this.numericValue = numericValue;
    }

    @Override
    public int getNumericValue() {
        return numericValue;
    }

    @Override
    public boolean isDefaultValue() {
        return numericValue == USERNAME.numericValue;
    }

    @Override
    public String getTokenType() {
        return CliAuthenticationToken.TOKEN_TYPE;
    }

    @Override
    public boolean isIssuedByCa() {
        return false;
    }
}
