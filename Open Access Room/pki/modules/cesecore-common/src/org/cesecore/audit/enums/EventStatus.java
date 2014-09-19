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
 * Specifies the status of an operation. When trying to do a secure log through the SecureEventsLogger session bean, it is necessary 
 * to specify if the overall operation resulted in FAILURE, SUCCESS or if it doens't matter VOID.
 * 
 * @version $Id: EventStatus.java 17625 2013-09-20 07:12:06Z netmackan $
 * 
 */
public enum EventStatus {

    FAILURE, SUCCESS, VOID

}
