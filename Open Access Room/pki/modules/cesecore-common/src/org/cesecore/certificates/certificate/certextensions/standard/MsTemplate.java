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
package org.cesecore.certificates.certificate.certextensions.standard;

import java.security.PublicKey;

import org.bouncycastle.asn1.ASN1Encodable;
import org.bouncycastle.asn1.DERBMPString;
import org.cesecore.certificates.ca.CA;
import org.cesecore.certificates.ca.internal.CertificateValidity;
import org.cesecore.certificates.certificate.certextensions.CertificateExtensionException;
import org.cesecore.certificates.certificate.certextensions.CertificateExtentionConfigurationException;
import org.cesecore.certificates.certificateprofile.CertificateProfile;
import org.cesecore.certificates.endentity.EndEntityInformation;
import org.cesecore.util.CertTools;

/** Microsoft Template extension, for domain controllers
 * 
 * Class for standard X509 certificate extension. 
 * See rfc3280 or later for spec of this extension.      
 * 
 * @version $Id: MsTemplate.java 17625 2013-09-20 07:12:06Z netmackan $
 */
public class MsTemplate extends StandardCertificateExtension {
	
    @Override
	public void init(final CertificateProfile certProf) {
		super.setOID(CertTools.OID_MSTEMPLATE);
		super.setCriticalFlag(false);
	}
    
    @Override
	public ASN1Encodable getValue(final EndEntityInformation subject, final CA ca, final CertificateProfile certProfile, final PublicKey userPublicKey, final PublicKey caPublicKey, CertificateValidity val ) throws CertificateExtentionConfigurationException, CertificateExtensionException {
		final String mstemplate = certProfile.getMicrosoftTemplate();             
        return new DERBMPString(mstemplate);             
	}	
}
