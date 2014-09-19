/*************************************************************************
 *                                                                       *
 *  CESeCore: CE Security Core                                           *
 *                                                                       *
 *  This software is free software; you can redistribute it and/or       *
 *  modify it under the terms of the GNU Lesser General           *
 *  License as published by the Free Software Foundation; either         *
 *  version 2.1 of the License, or any later version.                    *
 *                                                                       *
 *  See terms of license at gnu.org.                                     *
 *                                                                       *
 *************************************************************************/
package org.cesecore.certificates.certificate;

import org.cesecore.CesecoreException;

/**
 * Exception used in order to catch the error that we are trying to use custom certificate serial numbers, but are not using a unique
 * issuerDN/certSerialNo index in the database. This index is needed in order to use custom certificate serial numbers.
 * 
 * @version $Id: CustomCertSerialNumberException.java 17625 2013-09-20 07:12:06Z netmackan $
 */
public class CustomCertSerialNumberException extends CesecoreException {

    private static final long serialVersionUID = -2969078756967846634L;

    public CustomCertSerialNumberException(String message) {
        super(message);
    }

    public CustomCertSerialNumberException(Exception e) {
        super(e);
    }
}
