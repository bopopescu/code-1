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
package org.cesecore.certificates.certificate.request;

import java.security.cert.Certificate;

/**
 * Interface extending ResponseMessage to add certificate functionality.
 * 
 * @version $Id: CertificateResponseMessage.java 17625 2013-09-20 07:12:06Z netmackan $
 *
 */
public interface CertificateResponseMessage extends ResponseMessage {

    /**
     * Gets the complete certificate in the response message.
     *
     * @return certificate in the response message.
     */
    Certificate getCertificate();
    
    /**
     * Sets the complete certificate in the response message.
     *
     * @param cert certificate in the response message.
     */
    void setCertificate(Certificate cert);
}
