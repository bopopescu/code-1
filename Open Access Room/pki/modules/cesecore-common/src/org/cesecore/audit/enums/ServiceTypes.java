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
package org.cesecore.audit.enums;

/**
 * Represents the basic service types supported.
 *
 * When doing secure audit log ServiceType is used to indicate if the log  
 * was executed from the core itself or by an external application.
 * 
 * @version $Id: ServiceTypes.java 17625 2013-09-20 07:12:06Z netmackan $
 */
public enum ServiceTypes implements ServiceType {
	CORE;

	@Override
    public boolean equals(ServiceType value) {
        if(value == null) {
            return false;
        }
        return this.toString().equals(value.toString());
    }
}
