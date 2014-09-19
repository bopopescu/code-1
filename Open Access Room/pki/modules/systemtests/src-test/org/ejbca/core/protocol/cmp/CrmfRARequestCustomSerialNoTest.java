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

package org.ejbca.core.protocol.cmp;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayOutputStream;
import java.math.BigInteger;
import java.security.KeyPair;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.Collection;
import java.util.Iterator;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.math.RandomUtils;
import org.apache.log4j.Logger;
import org.bouncycastle.asn1.DEROutputStream;
import org.bouncycastle.asn1.cmp.PKIFailureInfo;
import org.bouncycastle.asn1.cmp.PKIMessage;
import org.bouncycastle.asn1.crmf.CertReqMessages;
import org.bouncycastle.asn1.pkcs.PKCSObjectIdentifiers;
import org.cesecore.authentication.tokens.AuthenticationToken;
import org.cesecore.authentication.tokens.UsernamePrincipal;
import org.cesecore.certificates.ca.CAInfo;
import org.cesecore.certificates.ca.CaSessionRemote;
import org.cesecore.certificates.certificateprofile.CertificateProfile;
import org.cesecore.certificates.certificateprofile.CertificateProfileConstants;
import org.cesecore.certificates.certificateprofile.CertificateProfileExistsException;
import org.cesecore.certificates.certificateprofile.CertificateProfileSession;
import org.cesecore.certificates.certificateprofile.CertificateProfileSessionRemote;
import org.cesecore.certificates.util.AlgorithmConstants;
import org.cesecore.certificates.util.DnComponents;
import org.cesecore.keys.util.KeyTools;
import org.cesecore.mock.authentication.tokens.TestAlwaysAllowLocalAuthenticationToken;
import org.cesecore.util.CertTools;
import org.cesecore.util.CryptoProviderTools;
import org.cesecore.util.EjbRemoteHelper;
import org.ejbca.config.CmpConfiguration;
import org.ejbca.config.Configuration;
import org.ejbca.core.ejb.config.GlobalConfigurationSessionRemote;
import org.ejbca.core.ejb.ra.EndEntityManagementSessionRemote;
import org.ejbca.core.ejb.ra.raadmin.EndEntityProfileSession;
import org.ejbca.core.ejb.ra.raadmin.EndEntityProfileSessionRemote;
import org.ejbca.core.model.ra.NotFoundException;
import org.ejbca.core.model.ra.raadmin.EndEntityProfile;
import org.ejbca.core.model.ra.raadmin.EndEntityProfileExistsException;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * @author tomas
 * @version $Id: CrmfRARequestCustomSerialNoTest.java 17741 2013-10-08 11:04:21Z aveen4711 $
 */
public class CrmfRARequestCustomSerialNoTest extends CmpTestCase {

    final private static Logger log = Logger.getLogger(CrmfRARequestCustomSerialNoTest.class);

    final private static String PBEPASSWORD = "password";
    private String issuerDN;
    private int caid;
    final private AuthenticationToken admin = new TestAlwaysAllowLocalAuthenticationToken(new UsernamePrincipal("CrmfRARequestCustomSerialNoTest"));
    private X509Certificate cacert;
    private CmpConfiguration cmpConfiguration;
    private String cmpAlias = "CrmfRARequestCustomSerialNoTestCmpConfigAlias";

    private CaSessionRemote caSession = EjbRemoteHelper.INSTANCE.getRemoteSession(CaSessionRemote.class);
    private EndEntityManagementSessionRemote endEntityManagementSession = EjbRemoteHelper.INSTANCE.getRemoteSession(EndEntityManagementSessionRemote.class);
    private EndEntityProfileSession eeProfileSession = EjbRemoteHelper.INSTANCE.getRemoteSession(EndEntityProfileSessionRemote.class);
    private CertificateProfileSession certProfileSession = EjbRemoteHelper.INSTANCE.getRemoteSession(CertificateProfileSessionRemote.class);
    private GlobalConfigurationSessionRemote globalConfigurationSession = EjbRemoteHelper.INSTANCE.getRemoteSession(GlobalConfigurationSessionRemote.class);

    @Before
    public void setUp() throws Exception {
        super.setUp();
        cmpConfiguration = (CmpConfiguration) globalConfigurationSession.getCachedConfiguration(Configuration.CMPConfigID);
        
        // Configure CMP for this test, we allow custom certificate serial numbers
    	CertificateProfile profile = new CertificateProfile(CertificateProfileConstants.CERTPROFILE_FIXED_ENDUSER);
    	//profile.setAllowCertSerialNumberOverride(true);
    	try {
    		certProfileSession.addCertificateProfile(admin, "CMPTESTPROFILE", profile);
		} catch (CertificateProfileExistsException e) {
			log.error("Could not create certificate profile.", e);
		}
        int cpId = certProfileSession.getCertificateProfileId("CMPTESTPROFILE");
        EndEntityProfile eep = new EndEntityProfile(true);
        eep.setValue(EndEntityProfile.DEFAULTCERTPROFILE,0, "" + cpId);
        eep.setValue(EndEntityProfile.AVAILCERTPROFILES,0, "" + cpId);
        eep.addField(DnComponents.COMMONNAME);
        eep.addField(DnComponents.ORGANIZATION);
        eep.addField(DnComponents.COUNTRY);
        eep.addField(DnComponents.RFC822NAME);
        eep.addField(DnComponents.UPN);
        eep.setModifyable(DnComponents.RFC822NAME, 0, true);
        eep.setUse(DnComponents.RFC822NAME, 0, false);	// Don't use field from "email" data
        try {
        	eeProfileSession.addEndEntityProfile(admin, "CMPTESTPROFILE", eep);
		} catch (EndEntityProfileExistsException e) {
			log.error("Could not create end entity profile.", e);
		}
        
        // Configure CMP for this test
        cmpConfiguration.addAlias(cmpAlias);
        cmpConfiguration.setRAMode(cmpAlias, true);
        cmpConfiguration.setAllowRAVerifyPOPO(cmpAlias, true);
        cmpConfiguration.setResponseProtection(cmpAlias, "signature");
        cmpConfiguration.setRAEEProfile(cmpAlias, "CMPTESTPROFILE");
        cmpConfiguration.setRACertProfile(cmpAlias, "CMPTESTPROFILE");
        cmpConfiguration.setRACAName(cmpAlias, "ManagementCA");
        cmpConfiguration.setRANameGenScheme(cmpAlias, "DN");
        cmpConfiguration.setRANameGenParams(cmpAlias, "CN");
        cmpConfiguration.setAllowRACustomSerno(cmpAlias, false);
        cmpConfiguration.setAuthenticationModule(cmpAlias, CmpConfiguration.AUTHMODULE_REG_TOKEN_PWD + ";" + CmpConfiguration.AUTHMODULE_HMAC);
        cmpConfiguration.setAuthenticationParameters(cmpAlias, "-;" + PBEPASSWORD);
        globalConfigurationSession.saveConfiguration(admin, cmpConfiguration, Configuration.CMPConfigID);

        CryptoProviderTools.installBCProvider();
        // Try to use ManagementCA if it exists
        final CAInfo managementca;

        managementca = caSession.getCAInfo(admin, "ManagementCA");

        if (managementca == null) {
            final Collection<Integer> caids;

            caids = caSession.getAuthorizedCAs(admin);

            final Iterator<Integer> iter = caids.iterator();
            int tmp = 0;
            while (iter.hasNext()) {
                tmp = iter.next().intValue();
            }
            caid = tmp;
        } else {
            caid = managementca.getCAId();
        }
        if (caid == 0) {
            assertTrue("No active CA! Must have at least one active CA to run tests!", false);
        }
        final CAInfo cainfo;

        cainfo = caSession.getCAInfo(admin, caid);

        Collection<Certificate> certs = cainfo.getCertificateChain();
        if (certs.size() > 0) {
            Iterator<Certificate> certiter = certs.iterator();
            Certificate cert = certiter.next();
            String subject = CertTools.getSubjectDN(cert);
            if (StringUtils.equals(subject, cainfo.getSubjectDN())) {
                // Make sure we have a BC certificate
                try {
                    cacert = (X509Certificate) CertTools.getCertfromByteArray(cert.getEncoded());
                } catch (Exception e) {
                    throw new Error(e);
                }
            } else {
                cacert = null;
            }
        } else {
            log.error("NO CACERT for caid " + caid);
            cacert = null;
        }
        issuerDN = cacert != null ? cacert.getIssuerDN().getName() : "CN=ManagementCA,O=EJBCA Sample,C=SE";
    }

    /**
     * @param userDN
     *            for new certificate.
     * @param keys
     *            key of the new certificate.
     * @param sFailMessage
     *            if !=null then EJBCA is expected to fail. The failure response
     *            message string is checked against this parameter.
     * @return If it is a certificate request that results in a successful certificate issuance, this certificate is returned
     * @throws Exception
     */
    private X509Certificate crmfHttpUserTest(String userDN, KeyPair keys, String sFailMessage, BigInteger customCertSerno) throws Exception {

        X509Certificate ret = null;
        final byte[] nonce = CmpMessageHelper.createSenderNonce();
        final byte[] transid = CmpMessageHelper.createSenderNonce();
        final int reqId;
        {
            final PKIMessage one = genCertReq(issuerDN, userDN, keys, cacert, nonce, transid, true, null, null, null, customCertSerno, null, null);
            final PKIMessage req = protectPKIMessage(one, false, PBEPASSWORD, 567);

            CertReqMessages ir = (CertReqMessages) req.getBody().getContent();
            reqId = ir.toCertReqMsgArray()[0].getCertReq().getCertReqId().getValue().intValue();
            assertNotNull(req);
            final ByteArrayOutputStream bao = new ByteArrayOutputStream();
            final DEROutputStream out = new DEROutputStream(bao);
            out.writeObject(req);
            final byte[] ba = bao.toByteArray();
            // Send request and receive response
            final byte[] resp = sendCmpHttp(ba, 200, cmpAlias);
            // do not check signing if we expect a failure (sFailMessage==null)
            checkCmpResponseGeneral(resp, issuerDN, userDN, cacert, nonce, transid, sFailMessage == null, null, PKCSObjectIdentifiers.sha1WithRSAEncryption.getId());
            if (sFailMessage == null) {
            	ret = checkCmpCertRepMessage(userDN, cacert, resp, reqId);
                // verify if custom cert serial number was used
                if (customCertSerno != null) {
                	assertTrue(ret.getSerialNumber().toString(16)+" is not same as expected "+customCertSerno.toString(16), ret.getSerialNumber().equals(customCertSerno));
                }
            } else {
                checkCmpFailMessage(resp, sFailMessage, CmpPKIBodyConstants.ERRORMESSAGE, reqId, PKIFailureInfo.badRequest, PKIFailureInfo.incorrectData);
            }
        }
        {
            // Send a confirm message to the CA
            final String hash = "foo123";
            final PKIMessage con = genCertConfirm(userDN, cacert, nonce, transid, hash, reqId);
            assertNotNull(con);
            PKIMessage confirm = protectPKIMessage(con, false, PBEPASSWORD, 567);
            final ByteArrayOutputStream bao = new ByteArrayOutputStream();
            final DEROutputStream out = new DEROutputStream(bao);
            out.writeObject(confirm);
            final byte[] ba = bao.toByteArray();
            // Send request and receive response
            final byte[] resp = sendCmpHttp(ba, 200, cmpAlias);
            checkCmpResponseGeneral(resp, issuerDN, userDN, cacert, nonce, transid, false, null, PKCSObjectIdentifiers.sha1WithRSAEncryption.getId());
            checkCmpPKIConfirmMessage(userDN, cacert, resp);
        }
        return ret;
    }

    @Test
    public void test01CustomCertificateSerialNumber() throws Exception {
    	final KeyPair key1 = KeyTools.genKeys("512", AlgorithmConstants.KEYALGORITHM_RSA);
    	final String userName1 = "cmptest1";
    	final String userDN1 = "C=SE,O=PrimeKey,CN=" + userName1;
    	try {
    		// check that several certificates could be created for one user and one key.
    		long serno = RandomUtils.nextLong();
    		BigInteger bint = BigInteger.valueOf(serno);
            int cpId = certProfileSession.getCertificateProfileId("CMPTESTPROFILE");
            // First it should fail because the CMP RA does not even look for, or parse, requested custom certificate serial numbers
            // Actually it does not fail here, but returns good answer
    		X509Certificate cert = crmfHttpUserTest(userDN1, key1, null, null);
    		assertFalse("SerialNumbers should not be equal when custom serialnumbers are not allowed.", bint.equals(cert.getSerialNumber()));
    		
    		
            // Second it should fail when the certificate profile does not allow serial number override
            // crmfHttpUserTest checks the returned serno if bint parameter is not null
    		cmpConfiguration.setAllowRACustomSerno(cmpAlias, true);
    		globalConfigurationSession.saveConfiguration(admin, cmpConfiguration, Configuration.CMPConfigID);
    		crmfHttpUserTest(userDN1, key1, "Used certificate profile ('"+cpId+"') is not allowing certificate serial number override.", bint);
    		
    		
    		// Third it should succeed and we should get our custom requested serialnumber
    		cmpConfiguration.setAllowRACustomSerno(cmpAlias, true);
    		globalConfigurationSession.saveConfiguration(admin, cmpConfiguration, Configuration.CMPConfigID);
    		CertificateProfile cp = certProfileSession.getCertificateProfile("CMPTESTPROFILE");
    		cp.setAllowCertSerialNumberOverride(true);
    		// Now when the profile allows serial number override it should work
    		certProfileSession.changeCertificateProfile(admin, "CMPTESTPROFILE", cp);
    		crmfHttpUserTest(userDN1, key1, null, bint);
    	} finally {
    		try {
    			endEntityManagementSession.deleteUser(admin, userName1);
    		} catch (NotFoundException e) {}
    	}
    }

    @After
    public void tearDown() throws Exception {
    	super.tearDown();
        cmpConfiguration.removeAlias(cmpAlias);
        globalConfigurationSession.saveConfiguration(admin, cmpConfiguration, Configuration.CMPConfigID);
        // Remove test profiles
        certProfileSession.removeCertificateProfile(admin, "CMPTESTPROFILE");
        eeProfileSession.removeEndEntityProfile(admin, "CMPTESTPROFILE");
    }
    
    public String getRoleName() {
        return this.getClass().getSimpleName(); 
    }
}
