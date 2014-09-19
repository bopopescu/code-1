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
package org.ejbca.extra.db;

import org.cesecore.internal.IUpgradeableData;

/**
 * Common interface for all SubMessages. Each implementor is responsible for it's own persistence.
 * 
 * @author philip
 * $Id: ISubMessage.java 12207 2011-08-05 15:16:48Z anatom $
 */
public interface ISubMessage extends IUpgradeableData {

	static final String CLASSTYPE = "CLASSTYPE";

}
