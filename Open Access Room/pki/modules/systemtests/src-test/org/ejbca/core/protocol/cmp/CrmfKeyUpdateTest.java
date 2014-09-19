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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.KeyPair;
import java.security.Principal;
import java.security.Signature;
import java.security.cert.Certificate;
import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.ejb.CreateException;
import javax.ejb.ObjectNotFoundException;
import javax.ejb.RemoveException;
import javax.security.auth.x500.X500Principal;

import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;
import org.bouncycastle.asn1.ASN1InputStream;
import org.bouncycastle.asn1.ASN1Primitive;
import org.bouncycastle.asn1.DEROctetString;
import org.bouncycastle.asn1.DEROutputStream;
import org.bouncycastle.asn1.cmp.CMPCertificate;
import org.bouncycastle.asn1.cmp.CertRepMessage;
import org.bouncycastle.asn1.cmp.CertResponse;
import org.bouncycastle.asn1.cmp.ErrorMsgContent;
import org.bouncycastle.asn1.cmp.PKIBody;
import org.bouncycastle.asn1.cmp.PKIMessage;
import org.bouncycastle.asn1.cmp.PKIStatusInfo;
import org.bouncycastle.asn1.crmf.CertReqMessages;
import org.bouncycastle.asn1.pkcs.PKCSObjectIdentifiers;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.AlgorithmIdentifier;
import org.bouncycastle.asn1.x509.ReasonFlags;
import org.bouncycastle.asn1.x9.X9ObjectIdentifiers;
import org.bouncycastle.cms.CMSSignedGenerator;
import org.bouncycastle.jce.X509KeyUsage;
import org.cesecore.CaTestUtils;
import org.cesecore.CesecoreException;
import org.cesecore.authentication.tokens.AuthenticationSubject;
import org.cesecore.authentication.tokens.AuthenticationToken;
import org.cesecore.authentication.tokens.UsernamePrincipal;
import org.cesecore.authorization.AuthorizationDeniedException;
import org.cesecore.authorization.user.AccessMatchType;
import org.cesecore.authorization.user.AccessUserAspectData;
import org.cesecore.authorization.user.matchvalues.X500PrincipalAccessMatchValue;
import org.cesecore.certificates.CertificateCreationException;
import org.cesecore.certificates.ca.CA;
import org.cesecore.certificates.ca.CADoesntExistsException;
import org.cesecore.certificates.ca.CAInfo;
import org.cesecore.certificates.ca.CaSessionRemote;
import org.cesecore.certificates.certificate.CertificateStoreSession;
import org.cesecore.certificates.certificate.CertificateStoreSessionRemote;
import org.cesecore.certificates.certificateprofile.CertificateProfileConstants;
import org.cesecore.certificates.crl.RevokedCertInfo;
import org.cesecore.certificates.endentity.EndEntityConstants;
import org.cesecore.certificates.endentity.EndEntityInformation;
import org.cesecore.certificates.endentity.EndEntityType;
import org.cesecore.certificates.endentity.EndEntityTypes;
import org.cesecore.certificates.util.AlgorithmConstants;
import org.cesecore.keys.token.CryptoTokenManagementSessionTest;
import org.cesecore.keys.util.KeyTools;
import org.cesecore.mock.authentication.tokens.TestAlwaysAllowLocalAuthenticationToken;
import org.cesecore.mock.authentication.tokens.TestX509CertificateAuthenticationToken;
import org.cesecore.roles.RoleData;
import org.cesecore.roles.RoleExistsException;
import org.cesecore.roles.RoleNotFoundException;
import org.cesecore.roles.access.RoleAccessSessionRemote;
import org.cesecore.roles.management.RoleManagementSessionRemote;
import org.cesecore.util.Base64;
import org.cesecore.util.CertTools;
import org.cesecore.util.CryptoProviderTools;
import org.cesecore.util.EjbRemoteHelper;
import org.ejbca.config.CmpConfiguration;
import org.ejbca.config.Configuration;
import org.ejbca.config.EjbcaConfigurationHolder;
import org.ejbca.core.EjbcaException;
import org.ejbca.core.ejb.ca.sign.SignSessionRemote;
import org.ejbca.core.ejb.config.GlobalConfigurationSessionRemote;
import org.ejbca.core.ejb.ra.EndEntityManagementSessionRemote;
import org.ejbca.core.model.SecConst;
import org.ejbca.core.model.approval.ApprovalException;
import org.ejbca.core.model.approval.WaitingForApprovalException;
import org.ejbca.core.model.ra.NotFoundException;
import org.ejbca.core.model.ra.raadmin.UserDoesntFullfillEndEntityProfile;
import org.junit.After;
import org.junit.Before;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runners.MethodSorters;

/**
 * This will test the different cmp authentication modules.
 * 
 * @version $Id: CrmfKeyUpdateTest.java 18188 2013-11-20 08:44:39Z aveen4711 $
 *
 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class CrmfKeyUpdateTest extends CmpTestCase {

    
    private static final Logger log = Logger.getLogger(CrmfKeyUpdateTest.class);

    private static final AuthenticationToken admin = new TestAlwaysAllowLocalAuthenticationToken(new UsernamePrincipal("CrmfKeyUpdateTest"));
    
    private String username;
    private String userDN;
    private String issuerDN;
    private byte[] nonce;
    private byte[] transid;
    private int caid;
    private Certificate cacert;
    private CA testx509ca;
    private CmpConfiguration cmpConfiguration;
    private String cmpAlias = "CrmfKeyUpdateTestCmpConfigAlias";
    
    private CaSessionRemote caSession = EjbRemoteHelper.INSTANCE.getRemoteSession(CaSessionRemote.class);
    private EndEntityManagementSessionRemote endEntityManagementSession = EjbRemoteHelper.INSTANCE.getRemoteSession(EndEntityManagementSessionRemote.class);
    private SignSessionRemote signSession = EjbRemoteHelper.INSTANCE.getRemoteSession(SignSessionRemote.class);
    private CertificateStoreSession certStoreSession = EjbRemoteHelper.INSTANCE.getRemoteSession(CertificateStoreSessionRemote.class);
    private RoleManagementSessionRemote roleManagementSession = EjbRemoteHelper.INSTANCE.getRemoteSession(RoleManagementSessionRemote.class);
    private RoleAccessSessionRemote roleAccessSessionRemote = EjbRemoteHelper.INSTANCE.getRemoteSession(RoleAccessSessionRemote.class);
    private GlobalConfigurationSessionRemote globalConfigurationSession = EjbRemoteHelper.INSTANCE.getRemoteSession(GlobalConfigurationSessionRemote.class);
    
    @Before
    public void setUp() throws Exception {
        super.setUp();

        username = "certRenewalUser";
        userDN = "CN="+username+",O=PrimeKey Solutions AB,C=SE";
        issuerDN = "CN=TestCA";
        nonce = CmpMessageHelper.createSenderNonce();
        transid = CmpMessageHelper.createSenderNonce();

        CryptoProviderTools.installBCProvider();
        cmpConfiguration = (CmpConfiguration) globalConfigurationSession.getCachedConfiguration(Configuration.CMPConfigID);
        
        int keyusage = X509KeyUsage.digitalSignature + X509KeyUsage.keyCertSign + X509KeyUsage.cRLSign;
        testx509ca = CaTestUtils.createTestX509CA(issuerDN, null, false, keyusage);
        caid = testx509ca.getCAId();
        cacert = (X509Certificate) testx509ca.getCACertificate();
        caSession.addCA(admin, testx509ca);
        
        // Initialize config in here
        EjbcaConfigurationHolder.instance();
        //confSession.backupConfiguration();

        
        cmpConfiguration.addAlias(cmpAlias);
        cmpConfiguration.setRAEEProfile(cmpAlias, "EMPTY");
        cmpConfiguration.setRACertProfile(cmpAlias, "ENDUSER");
        cmpConfiguration.setRACAName(cmpAlias, "TestCA");
        cmpConfiguration.setCMPDefaultCA(cmpAlias, "TestCA");
        cmpConfiguration.setRAMode(cmpAlias, false);
        cmpConfiguration.setAuthenticationModule(cmpAlias, "RegTokenPwd;HMAC");
        cmpConfiguration.setAuthenticationParameters(cmpAlias, "-;-");
        globalConfigurationSession.saveConfiguration(admin, cmpConfiguration, Configuration.CMPConfigID);
    }

    @After
    public void tearDown() throws Exception {

        super.tearDown();
        
        CryptoTokenManagementSessionTest.removeCryptoToken(null, testx509ca.getCAToken().getCryptoTokenId());
        caSession.removeCA(admin, caid);
        
        try {
            endEntityManagementSession.revokeAndDeleteUser(admin, username, ReasonFlags.unused);
            endEntityManagementSession.revokeAndDeleteUser(admin, "fakeuser", ReasonFlags.unused);

        } catch(Exception e){}
        
        cmpConfiguration.removeAlias(cmpAlias);
        globalConfigurationSession.saveConfiguration(admin, cmpConfiguration, Configuration.CMPConfigID);
    }

    
    /**
     * A "Happy Path" test. Sends a KeyUpdateRequest and receives a new certificate.
     * 
     * - Pre-configuration: Sets the operational mode to client mode (cmp.raoperationalmode=normal)
     * - Pre-configuration: Sets cmp.allowautomaticrenewal to 'true' and tests that the resetting of configuration has worked.
     * - Pre-configuration: Sets cmp.allowupdatewithsamekey to 'true'
     * - Creates a new user and obtains a certificate, cert, for this user. Tests whether obtaining the certificate was successful.
     * - Generates a CMP KeyUpdate Request and tests that such request has been created.
     * - Signs the CMP request using cert and attaches cert to the CMP request. Tests that the CMP request is still not null
     * - Sends the request using HTTP and receives a response.
     * - Examines the response:
     *      - Checks that the response is not empty or null
     *      - Checks that the protection algorithm is sha1WithRSAEncryption
     *      - Checks that the signer is the expected CA
     *      - Verifies the response signature
     *      - Checks that the response's senderNonce is 16 bytes long
     *      - Checks that the request's senderNonce is the same as the response's recipientNonce
     *      - Checks that the request and the response has the same transactionID
     *      - Obtains the certificate from the response
     *      - Checks that the obtained certificate has the right subjectDN and issuerDN
     * 
     * @throws Exception
     */
    @Test
    public void test01KeyUpdateRequestOK() throws Exception {
        if(log.isTraceEnabled()) {
            log.trace(">test01KeyUpdateRequestOK");
        }
        
        cmpConfiguration.setKurAllowAutomaticUpdate(cmpAlias, true);
        cmpConfiguration.setKurAllowSameKey(cmpAlias, true);
        globalConfigurationSession.saveConfiguration(admin, cmpConfiguration, Configuration.CMPConfigID);
        
        //--------------- create the user and issue his first certificate -----------------
        createUser(username, userDN, "foo123");
        KeyPair keys = KeyTools.genKeys("512", AlgorithmConstants.KEYALGORITHM_RSA);
        Certificate certificate = null;
        try {
            certificate = (X509Certificate) signSession.createCertificate(admin, username, "foo123", keys.getPublic());
        } catch (ObjectNotFoundException e) {
            throw new CertificateCreationException("Error encountered when creating certificate", e);
        } catch (CADoesntExistsException e) {
            throw new CertificateCreationException("Error encountered when creating certificate", e);
        } catch (EjbcaException e) {
            throw new CertificateCreationException("Error encountered when creating certificate", e);
        } catch (AuthorizationDeniedException e) {
            throw new CertificateCreationException("Error encountered when creating certificate", e);
        } catch (CesecoreException e) {
            throw new CertificateCreationException("Error encountered when creating certificate", e);
        }
        assertNotNull("Failed to create a test certificate", certificate);

        AlgorithmIdentifier pAlg = new AlgorithmIdentifier(PKCSObjectIdentifiers.sha1WithRSAEncryption);
        PKIMessage req = genRenewalReq(userDN, cacert, nonce, transid, keys, false, null, null, pAlg, new DEROctetString(nonce));
        assertNotNull("Failed to generate a CMP renewal request", req);
        CertReqMessages kur = (CertReqMessages) req.getBody().getContent();
        int reqId = kur.toCertReqMsgArray()[0].getCertReq().getCertReqId().getValue().intValue();
        CMPCertificate[] extraCert = getCMPCert(certificate);
        req = CmpMessageHelper.buildCertBasedPKIProtection(req, extraCert, keys.getPrivate(), pAlg.getAlgorithm().getId(), "BC");
        assertNotNull(req);
        
        ByteArrayOutputStream bao = new ByteArrayOutputStream();
        DEROutputStream out = new DEROutputStream(bao);
        out.writeObject(req);
        byte[] ba = bao.toByteArray();
        // Send request and receive response
        byte[] resp = sendCmpHttp(ba, 200, cmpAlias);
        checkCmpResponseGeneral(resp, issuerDN, userDN, cacert, nonce, transid, true, null, PKCSObjectIdentifiers.sha1WithRSAEncryption.getId());
        X509Certificate cert = checkKurCertRepMessage(userDN, cacert, resp, reqId);
        assertNotNull("Failed to renew the certificate", cert);
        assertTrue("The new certificate's keys are incorrect.", cert.getPublicKey().equals(keys.getPublic()));
        
        if(log.isTraceEnabled()) {
            log.trace("<test01KeyUpdateRequestOK");
        }

    }

    /**
     * Sends a KeyUpdateRequest for a certificate that belongs to an end entity whose status is not NEW and the configurations is 
     * NOT to allow changing the end entity status automatically. A CMP error message is expected and no certificate renewal.
     * 
     * - Pre-configuration: Sets the operational mode to client mode (cmp.raoperationalmode=normal)
     * - Pre-configuration: Sets cmp.allowautomaticrenewal to 'false' and tests that the resetting of configuration has worked.
     * - Pre-configuration: Sets cmp.allowupdatewithsamekey to 'true'
     * - Creates a new user and obtains a certificate, cert, for this user. Tests whether obtaining the certificate was successful.
     * - Generates a CMP KeyUpdate Request and tests that such request has been created.
     * - Signs the CMP request using cert and attaches cert to the CMP request. Tests that the CMP request is still not null
     * - Sends the request using HTTP and receives a response.
     * - Examines the response:
     *      - Checks that the response is not empty or null
     *      - Checks that the protection algorithm is sha1WithRSAEncryption
     *      - Checks that the signer is the expected CA
     *      - Verifies the response signature
     *      - Checks that the response's senderNonce is 16 bytes long
     *      - Checks that the request's senderNonce is the same as the response's recipientNonce
     *      - Checks that the request and the response has the same transactionID
     *      - Parses the response and checks that the parsing did not result in a 'null'
     *      - Checks that the CMP response message tag number is '23', indicating a CMP error message
     *      - Checks that the CMP response message contains the expected error details text
     * 
     * @throws Exception
     */
    @Test
    public void test02AutomaticUpdateNotAllowed() throws Exception {
        if(log.isTraceEnabled()) {
            log.trace(">test02AutomaticUpdateNotAllowed");
        }
        
        cmpConfiguration.setKurAllowAutomaticUpdate(cmpAlias, false);
        cmpConfiguration.setKurAllowSameKey(cmpAlias, true);
        globalConfigurationSession.saveConfiguration(admin, cmpConfiguration, Configuration.CMPConfigID);

        //--------------- create the user and issue his first certificate -----------------
        createUser(username, userDN, "foo123");
        KeyPair keys = KeyTools.genKeys("512", AlgorithmConstants.KEYALGORITHM_RSA);
        Certificate certificate = null;
        try {
            certificate = (X509Certificate) signSession.createCertificate(admin, username, "foo123", keys.getPublic());
        } catch (ObjectNotFoundException e) {
            throw new CertificateCreationException("Error encountered when creating certificate", e);
        } catch (CADoesntExistsException e) {
            throw new CertificateCreationException("Error encountered when creating certificate", e);
        } catch (EjbcaException e) {
            throw new CertificateCreationException("Error encountered when creating certificate", e);
        } catch (AuthorizationDeniedException e) {
            throw new CertificateCreationException("Error encountered when creating certificate", e);
        } catch (CesecoreException e) {
            throw new CertificateCreationException("Error encountered when creating certificate", e);
        }
        assertNotNull("Failed to create a test certificate", certificate);

        AlgorithmIdentifier pAlg = new AlgorithmIdentifier(PKCSObjectIdentifiers.sha1WithRSAEncryption);
        PKIMessage req = genRenewalReq(userDN, cacert, nonce, transid, keys, false, null, null, pAlg, new DEROctetString(nonce));
        assertNotNull("Failed to generate a CMP renewal request", req);

        CMPCertificate[] extraCert = getCMPCert(certificate);
        req = CmpMessageHelper.buildCertBasedPKIProtection(req, extraCert, keys.getPrivate(), pAlg.getAlgorithm().getId(), "BC");
        assertNotNull(req);
        
        ByteArrayOutputStream bao = new ByteArrayOutputStream();
        DEROutputStream out = new DEROutputStream(bao);
        out.writeObject(req);
        byte[] ba = bao.toByteArray();
        // Send request and receive response
        byte[] resp = sendCmpHttp(ba, 200, cmpAlias);
        checkCmpResponseGeneral(resp, issuerDN, userDN, cacert, nonce, transid, false, null, PKCSObjectIdentifiers.sha1WithRSAEncryption.getId());
        
        PKIMessage respObject = null;
        ASN1InputStream asn1InputStream = new ASN1InputStream(new ByteArrayInputStream(resp));
        try {
            respObject = PKIMessage.getInstance(asn1InputStream.readObject());
        } finally {
            asn1InputStream.close();
        }
        assertNotNull(respObject);

        final PKIBody body = respObject.getBody();
        assertEquals(23, body.getType());
        ErrorMsgContent err = (ErrorMsgContent) body.getContent();
        final String errMsg = err.getPKIStatusInfo().getStatusString().getStringAt(0).getString();
        final String expectedErrMsg = "Got request with status GENERATED (40), NEW, FAILED or INPROCESS required: " + username + ".";
        assertEquals(expectedErrMsg, errMsg);

        if(log.isTraceEnabled()) {
            log.trace("<test02AutomaticUpdateNotAllowed");
        }

    }

    /**
     * Sends a KeyUpdateRequest concerning a revoked certificate. A CMP error message is expected and no certificate renewal.
     * 
     * - Pre-configuration: Sets the operational mode to client mode (cmp.raoperationalmode=normal)
     * - Pre-configuration: Sets cmp.allowautomaticrenewal to 'true' and tests that the resetting of configuration has worked.
     * - Pre-configuration: Sets cmp.allowupdatewithsamekey to 'true'
     * - Creates a new user and obtains a certificate, cert, for this user. Tests whether obtaining the certificate was successful.
     * - Revokes cert and tests that the revocation was successful
     * - Generates a CMP KeyUpdate Request and tests that such request has been created.
     * - Signs the CMP request using cert and attaches cert to the CMP request. Tests that the CMP request is still not null
     * - Sends the request using HTTP and receives a response.
     * - Examines the response:
     *      - Checks that the response is not empty or null
     *      - Checks that the protection algorithm is sha1WithRSAEncryption
     *      - Checks that the signer is the expected CA
     *      - Verifies the response's signature
     *      - Checks that the response's senderNonce is 16 bytes long
     *      - Checks that the request's senderNonce is the same as the response's recipientNonce
     *      - Checks that the request and the response has the same transactionID
     *      - Parses the response and checks that the parsing did not result in a 'null'
     *      - Checks that the CMP response message tag number is '23', indicating a CMP error message
     *      - Checks that the CMP response message contain the expected error details text
     * 
     * @throws Exception
     */
    @Test
    public void test03UpdateRevokedCert() throws Exception {
        if(log.isTraceEnabled()) {
            log.trace(">test03UpdateRevokedCert");
        }
        
        cmpConfiguration.setKurAllowAutomaticUpdate(cmpAlias, true);
        globalConfigurationSession.saveConfiguration(admin, cmpConfiguration, Configuration.CMPConfigID);
        
        //--------------- create the user and issue his first certificate -----------------
        createUser(username, userDN, "foo123");
        KeyPair keys = KeyTools.genKeys("512", AlgorithmConstants.KEYALGORITHM_RSA);
        Certificate certificate = null;
        try {
            certificate = (X509Certificate) signSession.createCertificate(admin, username, "foo123", keys.getPublic());
        } catch (ObjectNotFoundException e) {
            throw new CertificateCreationException("Error encountered when creating certificate", e);
        } catch (CADoesntExistsException e) {
            throw new CertificateCreationException("Error encountered when creating certificate", e);
        } catch (EjbcaException e) {
            throw new CertificateCreationException("Error encountered when creating certificate", e);
        } catch (AuthorizationDeniedException e) {
            throw new CertificateCreationException("Error encountered when creating certificate", e);
        } catch (CesecoreException e) {
            throw new CertificateCreationException("Error encountered when creating certificate", e);
        }
        assertNotNull("Failed to create a test certificate", certificate);

        certStoreSession.setRevokeStatus(admin, certificate, RevokedCertInfo.REVOCATION_REASON_CESSATIONOFOPERATION, null);
        assertTrue("Failed to revoke the test certificate", certStoreSession.isRevoked(CertTools.getIssuerDN(certificate), CertTools.getSerialNumber(certificate)));
        
        AlgorithmIdentifier pAlg = new AlgorithmIdentifier(PKCSObjectIdentifiers.sha1WithRSAEncryption);
        PKIMessage req = genRenewalReq(userDN, cacert, nonce, transid, keys, false, null, null, pAlg, new DEROctetString(nonce));
        assertNotNull("Failed to generate a CMP renewal request", req);

        CMPCertificate[] extraCert = getCMPCert(certificate);
        req = CmpMessageHelper.buildCertBasedPKIProtection(req, extraCert, keys.getPrivate(), pAlg.getAlgorithm().getId(), "BC");
        assertNotNull(req);
        
        ByteArrayOutputStream bao = new ByteArrayOutputStream();
        DEROutputStream out = new DEROutputStream(bao);
        out.writeObject(req);
        byte[] ba = bao.toByteArray();
        // Send request and receive response
        byte[] resp = sendCmpHttp(ba, 200, cmpAlias);
        checkCmpResponseGeneral(resp, issuerDN, userDN, cacert, nonce, transid, false, null, PKCSObjectIdentifiers.sha1WithRSAEncryption.getId());
        
        PKIMessage respObject = null;
        ASN1InputStream asn1InputStream = new ASN1InputStream(new ByteArrayInputStream(resp));
        try {
            respObject = PKIMessage.getInstance(asn1InputStream.readObject());
        } finally {
            asn1InputStream.close();
        }
        assertNotNull(respObject);

        final PKIBody body = respObject.getBody();
        assertEquals(23, body.getType());
        ErrorMsgContent err = (ErrorMsgContent) body.getContent();
        final String errMsg = err.getPKIStatusInfo().getStatusString().getStringAt(0).getString();
        final String expectedErrMsg = "The certificate attached to the PKIMessage in the extraCert field is not active.";
        assertEquals(expectedErrMsg, errMsg);

        if(log.isTraceEnabled()) {
            log.trace("<test03UpdateRevokedCert");
        }

    }
    
    /**
     * Sends a KeyUpdateRequest concerning a certificate that does not exist in the database. A CMP error message is expected and no certificate renewal.
     * 
     * - Pre-configuration: Sets the operational mode to client mode (cmp.raoperationalmode=normal)
     * - Pre-configuration: Sets cmp.allowautomaticrenewal to 'true' and tests that the resetting of configuration has worked.
     * - Pre-configuration: Sets cmp.allowupdatewithsamekey to 'true'
     * - Generates a self-signed certificate, fakecert
     * - Generates a CMP KeyUpdate Request and tests that such request has been created.
     * - Signs the CMP request using fakecert and attaches fakecert to the CMP request. Tests that the CMP request is still not null
     * - Sends the request using HTTP and receives an response.
     * - Examines the response:
     * 		- Checks that the response is not empty or null
     * 		- Checks that the protection algorithm is sha1WithRSAEncryption
     * 		- Checks that the signer is the expected CA
     * 		- Verifies the response signature
     * 		- Checks that the response's senderNonce is 16 bytes long
     * 		- Checks that the request's senderNonce is the same as the response's recipientNonce
     * 		- Checks that the request and the response has the same transactionID
     * 		- Parses the response and checks that the parsing did not result in a 'null'
     * 		- Checks that the CMP response message tag number is '23', indicating a CMP error message
     * 		- Checks that the CMP response message contain the expected error details text
     * 
     * @throws Exception
     */
    @Test
    public void test04UpdateKeyWithFakeCert() throws Exception {
        if(log.isTraceEnabled()) {
            log.trace(">test04UpdateKeyWithFakeCert");
        }
        
        cmpConfiguration.setKurAllowAutomaticUpdate(cmpAlias, true);
        globalConfigurationSession.saveConfiguration(admin, cmpConfiguration, Configuration.CMPConfigID);
        
        //--------------- create the user and issue his first certificate -----------------
        final String fakeUsername = "fakeuser";
        final String fakeUserDN = "CN=" + fakeUsername + ",C=SE";
        createUser(fakeUsername, fakeUserDN, "foo123");

        KeyPair keys = KeyTools.genKeys("512", AlgorithmConstants.KEYALGORITHM_RSA);
        Certificate fakeCert = CertTools.genSelfCert(fakeUserDN, 30, null, keys.getPrivate(), keys.getPublic(),
                    AlgorithmConstants.SIGALG_SHA1_WITH_RSA, false);
        assertNotNull("Failed to create a test certificate", fakeCert);
        
        AlgorithmIdentifier pAlg = new AlgorithmIdentifier(PKCSObjectIdentifiers.sha1WithRSAEncryption);
        
        // Sending a request with a certificate that neither it nor the issuer CA is in the database
        PKIMessage req = genRenewalReq(userDN, cacert, nonce, transid, keys, false, null, null, pAlg, new DEROctetString(nonce));
        assertNotNull("Failed to generate a CMP renewal request", req);

        CMPCertificate[] extraCert = getCMPCert(fakeCert);
        req = CmpMessageHelper.buildCertBasedPKIProtection(req, extraCert, keys.getPrivate(), pAlg.getAlgorithm().getId(), "BC");
        assertNotNull(req);
        
        ByteArrayOutputStream bao = new ByteArrayOutputStream();
        DEROutputStream out = new DEROutputStream(bao);
        out.writeObject(req);
        byte[] ba = bao.toByteArray();
        // Send request and receive response
        byte[] resp = sendCmpHttp(ba, 200, cmpAlias);
        checkCmpResponseGeneral(resp, issuerDN, userDN, cacert, nonce, transid, false, null, PKCSObjectIdentifiers.sha1WithRSAEncryption.getId());
        
        PKIMessage respObject = null;
        ASN1InputStream asn1InputStream = new ASN1InputStream(new ByteArrayInputStream(resp));
        try {
            respObject = PKIMessage.getInstance(asn1InputStream.readObject());
        } finally {
            asn1InputStream.close();
        }
        assertNotNull(respObject);

        PKIBody body = respObject.getBody();
        assertEquals(23, body.getType());
        ErrorMsgContent err = (ErrorMsgContent) body.getContent();
        String errMsg = err.getPKIStatusInfo().getStatusString().getStringAt(0).getString();
        String expectedErrMsg = "The certificate attached to the PKIMessage in the extraCert field could not be found in the database.";
        assertEquals(expectedErrMsg, errMsg);

        // sending another renewal request with a certificate issued by an existing CA but the certificate itself is not in the database        
        // A certificate, not in the database, issued by TestCA
        byte[] fakecertBytes = Base64.decode( (
                "MIIB6TCCAVKgAwIBAgIIIKF3bEBbbyQwDQYJKoZIhvcNAQELBQAwETEPMA0GA1UE" +
                "AwwGVGVzdENBMB4XDTEzMDMxMjExMTcyMVoXDTEzMDMyMjExMjcyMFowIDERMA8G" +
                "A1UEAwwIZmFrZXVzZXIxCzAJBgNVBAYTAlNFMFwwDQYJKoZIhvcNAQEBBQADSwAw" +
                "SAJBAKZlXrI3TwziiDK9/E1V4n6PCXhpRERSLWPEpRvRPWfpvazpq7R2UZZRq5i2" +
                "hrqKDbfLdAouh2J7AIlUZG3cdJECAwEAAaN/MH0wHQYDVR0OBBYEFCb2tsZTXOh7" +
                "FjjVXpSxkJ79P3tJMAwGA1UdEwEB/wQCMAAwHwYDVR0jBBgwFoAURmtK3gFt81Bp" +
                "3z+YZuzBm65Ja6IwDgYDVR0PAQH/BAQDAgXgMB0GA1UdJQQWMBQGCCsGAQUFBwMC" +
                "BggrBgEFBQcDBDANBgkqhkiG9w0BAQsFAAOBgQAmclw6cwuQkiPSN4bHOP5S7bdU" +
                "+UKXLIkk1L84q0WQfblNzYkcDXMsxwJ1dv2Yd/dxIjtVjrhVIUrRMA70jtWs31CH" +
                "t9ofdgncIdtzZo49mLRQDwhTCApoLf0BCNb2rWpzCPWQTa97y0u5T65m7DAkBTV/" +
                "JAkFQIZCLSAci++qPA==").getBytes() );
        fakeCert = CertTools.getCertfromByteArray(fakecertBytes);
        
        req = genRenewalReq(fakeUserDN, cacert, nonce, transid, keys, false, null, null, pAlg, new DEROctetString(nonce));
        assertNotNull("Failed to generate a CMP renewal request", req);

        extraCert = getCMPCert(fakeCert);
        req = CmpMessageHelper.buildCertBasedPKIProtection(req, extraCert, keys.getPrivate(), pAlg.getAlgorithm().getId(), "BC");
        assertNotNull(req);
        
        bao = new ByteArrayOutputStream();
        out = new DEROutputStream(bao);
        out.writeObject(req);
        ba = bao.toByteArray();
        // Send request and receive response
        resp = sendCmpHttp(ba, 200, cmpAlias);
        checkCmpResponseGeneral(resp, issuerDN, userDN, cacert, nonce, transid, false, null, PKCSObjectIdentifiers.sha1WithRSAEncryption.getId());
        
        respObject = null;
        asn1InputStream = new ASN1InputStream(new ByteArrayInputStream(resp));
        try {
            respObject = PKIMessage.getInstance(asn1InputStream.readObject());
        } finally {
            asn1InputStream.close();
        }
        assertNotNull(respObject);

        body = respObject.getBody();
        assertEquals(23, body.getType());
        err = (ErrorMsgContent) body.getContent();
        errMsg = err.getPKIStatusInfo().getStatusString().getStringAt(0).getString();
        expectedErrMsg = "The certificate attached to the PKIMessage in the extraCert field could not be found in the database.";
        assertEquals(expectedErrMsg, errMsg);
        
        
        if(log.isTraceEnabled()) {
            log.trace("<test04UpdateKeyWithFakeCert");
        }

    }

    /**
     * Sends a KeyUpdateRequest using the same old keys and the configurations is NOT to allow the use of the same key. 
     * A CMP error message is expected and no certificate renewal.
     * 
     * - Pre-configuration: Sets the operational mode to client mode (cmp.raoperationalmode=normal)
     * - Pre-configuration: Sets cmp.allowautomaticrenewal to 'true' and tests that the resetting of configuration has worked.
     * - Pre-configuration: Sets cmp.allowupdatewithsamekey to 'false'
     * - Creates a new user and obtains a certificate, cert, for this user. Tests whether obtaining the certificate was successful.
     * - Generates a CMP KeyUpdate Request and tests that such request has been created.
     * - Signs the CMP request using cert and attaches cert to the CMP request. Tests that the CMP request is still not null
     * - Sends the request using HTTP and receives a response.
     * - Examines the response:
     * 		- Checks that the response is not empty or null
     * 		- Checks that the protection algorithm is sha1WithRSAEncryption
     * 		- Checks that the signer is the expected CA
     * 		- Verifies the response signature
     * 		- Checks that the response's senderNonce is 16 bytes long
     * 		- Checks that the request's senderNonce is the same as the response's recipientNonce
     * 		- Checks that the request and the response has the same transactionID
     * 		- Parses the response and checks that the parsing did not result in a 'null'
     * 		- Checks that the CMP response message tag number is '23', indicating a CMP error message
     * 		- Checks that the CMP response message contain the expected error details text
     * 
     * @throws Exception
     */
    @Test
    public void test05UpdateWithSameKeyNotAllowed() throws Exception {
        if(log.isTraceEnabled()) {
            log.trace(">test07UpdateWithSameKeyNotAllowed");
        }
        
        cmpConfiguration.setRAMode(cmpAlias, false);
        cmpConfiguration.setKurAllowAutomaticUpdate(cmpAlias, true);
        cmpConfiguration.setKurAllowSameKey(cmpAlias, false);
        globalConfigurationSession.saveConfiguration(admin, cmpConfiguration, Configuration.CMPConfigID);

        //--------------- create the user and issue his first certificate -----------------
        createUser(username, userDN, "foo123");
        KeyPair keys = KeyTools.genKeys("512", AlgorithmConstants.KEYALGORITHM_RSA);
        Certificate certificate = null;
        certificate = (X509Certificate) signSession.createCertificate(admin, username, "foo123", keys.getPublic());
        assertNotNull("Failed to create a test certificate", certificate);

        AlgorithmIdentifier pAlg = new AlgorithmIdentifier(PKCSObjectIdentifiers.sha1WithRSAEncryption);
        PKIMessage req = genRenewalReq(userDN, cacert, nonce, transid, keys, false, null, null, pAlg, new DEROctetString(nonce));
        assertNotNull("Failed to generate a CMP renewal request", req);

        CMPCertificate[] extraCert = getCMPCert(certificate);
        req = CmpMessageHelper.buildCertBasedPKIProtection(req, extraCert, keys.getPrivate(), pAlg.getAlgorithm().getId(), "BC");
        assertNotNull(req);
        
        ByteArrayOutputStream bao = new ByteArrayOutputStream();
        DEROutputStream out = new DEROutputStream(bao);
        out.writeObject(req);
        byte[] ba = bao.toByteArray();
        // Send request and receive response
        byte[] resp = sendCmpHttp(ba, 200, cmpAlias);
        checkCmpResponseGeneral(resp, issuerDN, userDN, cacert, nonce, transid, false, null, PKCSObjectIdentifiers.sha1WithRSAEncryption.getId());
        
        PKIMessage respObject = null;
        ASN1InputStream asn1InputStream = new ASN1InputStream(new ByteArrayInputStream(resp));
        try {
            respObject = PKIMessage.getInstance(asn1InputStream.readObject());
        } finally {
            asn1InputStream.close();
        }
        assertNotNull(respObject);

        final PKIBody body = respObject.getBody();
        assertEquals(23, body.getType());
        ErrorMsgContent err = (ErrorMsgContent) body.getContent();
        final String errMsg = err.getPKIStatusInfo().getStatusString().getStringAt(0).getString();
        final String expectedErrMsg = "Invalid key. The public key in the KeyUpdateRequest is the same as the public key in the existing end entity certificate";
        assertEquals(expectedErrMsg, errMsg);

        if(log.isTraceEnabled()) {
            log.trace("<test07UpdateWithSameKeyNotAllowed");
        }
    }

    /**
     * Sends a KeyUpdateRequest with a different key and the configurations is NOT to allow the use of the same keys. 
     * Successful operation is expected and a new certificate is received.
     * 
     * - Pre-configuration: Sets the operational mode to client mode (cmp.raoperationalmode=normal)
     * - Pre-configuration: Sets cmp.allowautomaticrenewal to 'true' and tests that the resetting of configuration has worked.
     * - Pre-configuration: Sets cmp.allowupdatewithsamekey to 'false'
     * - Creates a new user and obtains a certificate, cert, for this user. Tests whether obtaining the certificate was successful.
     * - Generates a CMP KeyUpdate Request and tests that such request has been created.
     * - Signs the CMP request using cert and attaches cert to the CMP request. Tests that the CMP request is still not null
     * - Sends the request using HTTP and receives a response.
     * - Examines the response:
     * 		- Checks that the response is not empty or null
     * 		- Checks that the protection algorithm is sha1WithRSAEncryption
     * 		- Check that the signer is the expected CA
     * 		- Verifies the response signature
     * 		- Checks that the response's senderNonce is 16 bytes long
     * 		- Checks that the request's senderNonce is the same as the response's recipientNonce
     * 		- Checks that the request and the response has the same transactionID
     * 		- Obtains the certificate from the response
     * 		- Checks that the obtained certificate has the right subjectDN and issuerDN
     * 
     * @throws Exception
     */
    @Test
    public void test06UpdateWithDifferentKey() throws Exception {
        if(log.isTraceEnabled()) {
            log.trace(">test08UpdateWithDifferentKey");
        }
        
        cmpConfiguration.setRAMode(cmpAlias, false);
        cmpConfiguration.setKurAllowAutomaticUpdate(cmpAlias, true);
        cmpConfiguration.setKurAllowSameKey(cmpAlias, false);
        globalConfigurationSession.saveConfiguration(admin, cmpConfiguration, Configuration.CMPConfigID);
        
        //--------------- create the user and issue his first certificate -----------------
        createUser(username, userDN, "foo123");
        KeyPair keys = KeyTools.genKeys("512", AlgorithmConstants.KEYALGORITHM_RSA);
        Certificate certificate = null;
        certificate = (X509Certificate) signSession.createCertificate(admin, username, "foo123", keys.getPublic());
        assertNotNull("Failed to create a test certificate", certificate);
        
        KeyPair newkeys = KeyTools.genKeys("512", AlgorithmConstants.KEYALGORITHM_RSA);
        AlgorithmIdentifier pAlg = new AlgorithmIdentifier(PKCSObjectIdentifiers.sha1WithRSAEncryption);
        PKIMessage req = genRenewalReq(userDN, cacert, nonce, transid, newkeys, false, null, null, pAlg, new DEROctetString(nonce));
        assertNotNull("Failed to generate a CMP renewal request", req);
        CertReqMessages kur = (CertReqMessages) req.getBody().getContent();
        int reqId = kur.toCertReqMsgArray()[0].getCertReq().getCertReqId().getValue().intValue();

        CMPCertificate[] extraCert = getCMPCert(certificate);
        req = CmpMessageHelper.buildCertBasedPKIProtection(req, extraCert, keys.getPrivate(), pAlg.getAlgorithm().getId(), "BC");
        assertNotNull(req);
        //******************************************''''''
        final Signature sig = Signature.getInstance(req.getHeader().getProtectionAlg().getAlgorithm().getId(), "BC");
        sig.initVerify(certificate.getPublicKey());
        sig.update(CmpMessageHelper.getProtectedBytes(req));
        boolean verified = sig.verify(req.getProtection().getBytes());
        assertTrue("Signing the message failed.", verified);
        //***************************************************
        
        ByteArrayOutputStream bao = new ByteArrayOutputStream();
        DEROutputStream out = new DEROutputStream(bao);
        out.writeObject(req);
        byte[] ba = bao.toByteArray();
        // Send request and receive response
        byte[] resp = sendCmpHttp(ba, 200, cmpAlias);
        checkCmpResponseGeneral(resp, issuerDN, userDN, cacert, nonce, transid, true, null, PKCSObjectIdentifiers.sha1WithRSAEncryption.getId());
        X509Certificate cert = checkKurCertRepMessage(userDN, cacert, resp, reqId);
        assertNotNull("Failed to renew the certificate", cert);
        assertTrue("The new certificate's keys are incorrect.", cert.getPublicKey().equals(newkeys.getPublic()));
        assertFalse("The new certificate's keys are the same as the old certificate's keys.", cert.getPublicKey().equals(keys.getPublic()));
        
        if(log.isTraceEnabled()) {
            log.trace("<test08UpdateWithDifferentKey");
        }
    }
    
    /**
     * Sends a KeyUpdateRequest in RA mode. 
     * Successful operation is expected and a new certificate is received.
     * 
     * - Pre-configuration: Sets the operational mode to RA mode (cmp.raoperationalmode=ra)
     * - Pre-configuration: Sets the cmp.authenticationmodule to 'EndEntityCertificate'
     * - Pre-configuration: Sets the cmp.authenticationparameters to 'TestCA'
     * - Pre-configuration: Set cmp.checkadminauthorization to 'true'
     * - Creates a new user and obtains a certificate, cert, for this user. Tests whether obtaining the certificate was successful.
     * - Generates a CMP KeyUpdate Request and tests that such request has been created.
     * - Signs the CMP request using cert and attaches cert to the CMP request. Tests that the CMP request is still not null
     * - Verifies the signature of the CMP request
     * - Sends the request using HTTP and receives an response.
     * - Examines the response:
     *      - Checks that the response is not empty or null
     *      - Checks that the protection algorithm is sha1WithRSAEncryption
     *      - Check that the signer is the expected CA
     *      - Verifies the response signature
     *      - Checks that the response's senderNonce is 16 bytes long
     *      - Checks that the request's senderNonce is the same as the response's recipientNonce
     *      - Checks that the request and the response has the same transactionID
     *      - Obtains the certificate from the response
     *      - Checks that the obtained certificate has the right subjectDN and issuerDN
     * 
     * @throws Exception
     */
    @Test
    public void test07RAMode() throws Exception {
        if(log.isTraceEnabled()) {
            log.trace("test09RAMode()");
        }
        
        cmpConfiguration.setRAMode(cmpAlias, true);
        cmpConfiguration.setAuthenticationModule(cmpAlias, CmpConfiguration.AUTHMODULE_ENDENTITY_CERTIFICATE);
        cmpConfiguration.setAuthenticationParameters(cmpAlias, "TestCA");
        cmpConfiguration.setKurAllowAutomaticUpdate(cmpAlias, true);
        globalConfigurationSession.saveConfiguration(admin, cmpConfiguration, Configuration.CMPConfigID);

        //------------------ create the user and issue his first certificate -------------
        createUser(username, userDN, "foo123");
        KeyPair keys = KeyTools.genKeys("512", AlgorithmConstants.KEYALGORITHM_RSA);
        Certificate certificate = null;
        certificate = (X509Certificate) signSession.createCertificate(admin, username, "foo123", keys.getPublic());
        assertNotNull("Failed to create a test certificate", certificate);

        AlgorithmIdentifier pAlg = new AlgorithmIdentifier(PKCSObjectIdentifiers.sha1WithRSAEncryption);
        PKIMessage req = genRenewalReq(userDN, cacert, nonce, transid, keys, false, userDN, issuerDN, pAlg, new DEROctetString("CMPTESTPROFILE".getBytes()));
        assertNotNull("Failed to generate a CMP renewal request", req);
        CertReqMessages kur = (CertReqMessages) req.getBody().getContent();
        int reqId = kur.toCertReqMsgArray()[0].getCertReq().getCertReqId().getValue().intValue();
        
        createUser("cmpTestAdmin", "CN=cmpTestAdmin,C=SE", "foo123");
        KeyPair admkeys = KeyTools.genKeys("1024", "RSA");
        AuthenticationToken admToken = createAdminToken(admkeys, "cmpTestAdmin", "CN=cmpTestAdmin,C=SE");
        Certificate admCert = getCertFromCredentials(admToken);
        CMPCertificate[] extraCert = getCMPCert(admCert);
        req = CmpMessageHelper.buildCertBasedPKIProtection(req, extraCert, admkeys.getPrivate(), pAlg.getAlgorithm().getId(), "BC");

        assertNotNull(req);
        
        ByteArrayOutputStream bao = new ByteArrayOutputStream();
        DEROutputStream out = new DEROutputStream(bao);
        out.writeObject(req);
        byte[] ba = bao.toByteArray();
        //send request and recieve response
        byte[] resp = sendCmpHttp(ba, 200, cmpAlias);
        checkCmpResponseGeneral(resp, issuerDN, userDN, cacert, nonce, transid, true, null, PKCSObjectIdentifiers.sha1WithRSAEncryption.getId());
        X509Certificate cert = checkKurCertRepMessage(userDN, cacert, resp, reqId);
        assertNotNull("Failed to renew the certificate", cert);

        removeAuthenticationToken(admToken, admCert, "cmpTestAdmin");

        if(log.isTraceEnabled()) {
            log.trace("<test09RAMode()");
        }
    }

    /**
     * Sends a KeyUpdateRequest in RA mode and the request sender is not an authorized administrator. 
     * A CMP error message is expected and no certificate renewal.
     * 
     * - Pre-configuration: Sets the operational mode to client mode (cmp.raoperationalmode=normal)
     * - Pre-configuration: Sets the cmp.authenticationmodule to 'EndEntityCertificate'
     * - Pre-configuration: Sets the cmp.authenticationparameters to 'TestCA'
     * - Pre-configuration: Set cmp.checkadminauthorization to 'true'
     * - Creates a new user and obtains a certificate, cert, for this user. Tests whether obtaining the certificate was successful.
     * - Generates a CMP KeyUpdate Request and tests that such request has been created.
     * - Signs the CMP request using cert and attaches cert to the CMP request. Tests that the CMP request is still not null
     * - Verifies the signature of the CMP request
     * - Sends the request using HTTP and receives an response.
     * - Examines the response:
     *      - Checks that the response is not empty or null
     *      - Checks that the protection algorithm is sha1WithRSAEncryption
     *      - Check that the signer is the expected CA
     *      - Verifies the response signature
     *      - Checks that the response's senderNonce is 16 bytes long
     *      - Checks that the request's senderNonce is the same as the response's recipientNonce
     *      - Checks that the request and the response has the same transactionID
     *      - Parse the response and make sure that the parsing did not result in a 'null'
     *      - Check that the CMP response message tag number is '23', indicating a CMP error message
     *      - Check that the CMP response message contain the expected error details text
     * 
     * @throws Exception
     */
    @Test
    public void test08RAModeNonAdmin() throws Exception {
        if(log.isTraceEnabled()) {
            log.trace("test10RAModeNonAdmin()");
        }
        
        cmpConfiguration.setRAMode(cmpAlias, true);
        cmpConfiguration.setAuthenticationModule(cmpAlias, CmpConfiguration.AUTHMODULE_ENDENTITY_CERTIFICATE);
        cmpConfiguration.setAuthenticationParameters(cmpAlias, "TestCA");
        globalConfigurationSession.saveConfiguration(admin, cmpConfiguration, Configuration.CMPConfigID);

        //------------------ create the user and issue his first certificate -------------
        createUser(username, userDN, "foo123");
        KeyPair keys = KeyTools.genKeys("512", AlgorithmConstants.KEYALGORITHM_RSA);
        Certificate certificate = (X509Certificate) signSession.createCertificate(admin, username, "foo123", keys.getPublic());
        assertNotNull("Failed to create a test certificate", certificate);

        AlgorithmIdentifier pAlg = new AlgorithmIdentifier(PKCSObjectIdentifiers.sha1WithRSAEncryption);
        PKIMessage req = genRenewalReq(userDN, cacert, nonce, transid, keys, false, userDN, issuerDN, pAlg, new DEROctetString("CMPTESTPROFILE".getBytes()));
        assertNotNull("Failed to generate a CMP renewal request", req);
        
        CMPCertificate[] extraCert = getCMPCert(certificate);
        req = CmpMessageHelper.buildCertBasedPKIProtection(req, extraCert, keys.getPrivate(), pAlg.getAlgorithm().getId(), "BC");
        assertNotNull(req);

        ByteArrayOutputStream bao = new ByteArrayOutputStream();
        DEROutputStream out = new DEROutputStream(bao);
        out.writeObject(req);
        byte[] ba = bao.toByteArray();
        //send request and recieve response
        byte[] resp = sendCmpHttp(ba, 200, cmpAlias);
        checkCmpResponseGeneral(resp, issuerDN, userDN, cacert, nonce, transid, false, null, PKCSObjectIdentifiers.sha1WithRSAEncryption.getId());
        
        PKIMessage respObject = null;
        ASN1InputStream asn1InputStream = new ASN1InputStream(new ByteArrayInputStream(resp));
        try {
            respObject = PKIMessage.getInstance(asn1InputStream.readObject());
        } finally {
            asn1InputStream.close();
        }
        assertNotNull(respObject);

        final PKIBody body = respObject.getBody();
        assertEquals(23, body.getType());
        ErrorMsgContent err = (ErrorMsgContent) body.getContent();
        final String errMsg = err.getPKIStatusInfo().getStatusString().getStringAt(0).getString();
        final String expectedErrMsg = "'" + userDN + "' is not an authorized administrator.";
        assertEquals(expectedErrMsg, errMsg);

        if(log.isTraceEnabled()) {
            log.trace("<test10RAModeNonAdmin()");
        }

    }
    
    /**
     * Sends a KeyUpdateRequest in RA mode without filling the 'issuerDN' field in the request. 
     * Successful operation is expected and a new certificate is received.
     * 
     * - Pre-configuration: Sets the operational mode to RA mode (cmp.raoperationalmode=ra)
     * - Pre-configuration: Sets the cmp.authenticationmodule to 'EndEntityCertificate'
     * - Pre-configuration: Sets the cmp.authenticationparameters to 'TestCA'
     * - Pre-configuration: Set cmp.checkadminauthorization to 'true'
     * - Creates a new user and obtains a certificate, cert, for this user. Tests whether obtaining the certificate was successful.
     * - Generates a CMP KeyUpdate Request and tests that such request has been created.
     * - Signs the CMP request using cert and attaches cert to the CMP request. Tests that the CMP request is still not null
     * - Verifies the signature of the CMP request
     * - Sends the request using HTTP and receives an response.
     * - Examines the response:
     *      - Checks that the response is not empty or null
     *      - Checks that the protection algorithm is sha1WithRSAEncryption
     *      - Check that the signer is the expected CA
     *      - Verifies the response signature
     *      - Checks that the response's senderNonce is 16 bytes long
     *      - Checks that the request's senderNonce is the same as the response's recipientNonce
     *      - Checks that the request and the response has the same transactionID
     *      - Obtains the certificate from the response
     *      - Checks that the obtained certificate has the right subjectDN and issuerDN
     * 
     * @throws Exception
     */
    @Test
    public void test09RANoIssuer() throws Exception {
        if(log.isTraceEnabled()) {
            log.trace("test11RANoIssuer()");
        }
        
        cmpConfiguration.setRAMode(cmpAlias, true);
        cmpConfiguration.setAuthenticationModule(cmpAlias, CmpConfiguration.AUTHMODULE_ENDENTITY_CERTIFICATE);
        cmpConfiguration.setAuthenticationParameters(cmpAlias, "TestCA");
        cmpConfiguration.setKurAllowAutomaticUpdate(cmpAlias, true);
        globalConfigurationSession.saveConfiguration(admin, cmpConfiguration, Configuration.CMPConfigID);
        
        //------------------ create the user and issue his first certificate -------------
        createUser(username, userDN, "foo123");
        KeyPair keys = KeyTools.genKeys("512", AlgorithmConstants.KEYALGORITHM_RSA);
        Certificate certificate = (X509Certificate) signSession.createCertificate(admin, username, "foo123", keys.getPublic());
        assertNotNull("Failed to create a test certificate", certificate);

        AlgorithmIdentifier pAlg = new AlgorithmIdentifier(PKCSObjectIdentifiers.sha1WithRSAEncryption);
        PKIMessage req = genRenewalReq(userDN, cacert, nonce, transid, keys, false, userDN, null, pAlg, new DEROctetString("CMPTESTPROFILE".getBytes()));
        assertNotNull("Failed to generate a CMP renewal request", req);
        CertReqMessages kur = (CertReqMessages) req.getBody().getContent();
        int reqId = kur.toCertReqMsgArray()[0].getCertReq().getCertReqId().getValue().intValue();
        
        createUser("cmpTestAdmin", "CN=cmpTestAdmin,C=SE", "foo123");
        KeyPair admkeys = KeyTools.genKeys("1024", "RSA");
        AuthenticationToken admToken = createAdminToken(admkeys, "cmpTestAdmin", "CN=cmpTestAdmin,C=SE");
        Certificate admCert = getCertFromCredentials(admToken);
        CMPCertificate[] extraCert = getCMPCert(admCert);
        req = CmpMessageHelper.buildCertBasedPKIProtection(req, extraCert, admkeys.getPrivate(), pAlg.getAlgorithm().getId(), "BC");
        assertNotNull(req);

        ByteArrayOutputStream bao = new ByteArrayOutputStream();
        DEROutputStream out = new DEROutputStream(bao);
        out.writeObject(req);
        byte[] ba = bao.toByteArray();
        //send request and recieve response
        byte[] resp = sendCmpHttp(ba, 200, cmpAlias);
        checkCmpResponseGeneral(resp, issuerDN, userDN, cacert, nonce, transid, false, null, PKCSObjectIdentifiers.sha1WithRSAEncryption.getId());
        X509Certificate cert = checkKurCertRepMessage(userDN, cacert, resp, reqId);
        assertNotNull("Failed to renew the certificate", cert);
        
        removeAuthenticationToken(admToken, admCert, "cmpTestAdmin");
        
        if(log.isTraceEnabled()) {
            log.trace("<test11RANoIssuer()");
        }

    }
    
    /**
     * Sends a KeyUpdateRequest in RA mode with neither subjectDN nor issuerDN are set in the request. 
     * A CMP error message is expected and no certificate renewal.
     * 
     * - Pre-configuration: Sets the operational mode to client mode (cmp.raoperationalmode=normal)
     * - Pre-configuration: Sets the cmp.authenticationmodule to 'EndEntityCertificate'
     * - Pre-configuration: Sets the cmp.authenticationparameters to 'TestCA'
     * - Pre-configuration: Set cmp.checkadminauthorization to 'true'
     * - Creates a new user and obtains a certificate, cert, for this user. Tests whether obtaining the certificate was successful.
     * - Generates a CMP KeyUpdate Request and tests that such request has been created.
     * - Signs the CMP request using cert and attaches cert to the CMP request. Tests that the CMP request is still not null
     * - Verifies the signature of the CMP request
     * - Sends the request using HTTP and receives an response.
     * - Examines the response:
     *      - Checks that the response is not empty or null
     *      - Checks that the protection algorithm is sha1WithRSAEncryption
     *      - Check that the signer is the expected CA
     *      - Verifies the response signature
     *      - Checks that the response's senderNonce is 16 bytes long
     *      - Checks that the request's senderNonce is the same as the response's recipientNonce
     *      - Checks that the request and the response has the same transactionID
     *      - Parse the response and make sure that the parsing did not result in a 'null'
     *      - Check that the CMP response message tag number is '23', indicating a CMP error message
     *      - Check that the CMP response message contain the expected error details text
     * 
     * @throws Exception
     */
    @Test
    public void test10RANoIssuerNoSubjectDN() throws Exception {
        if(log.isTraceEnabled()) {
            log.trace("test12RANoIssuerNoSubjetDN()");
        }
        
        cmpConfiguration.setRAMode(cmpAlias, true);
        cmpConfiguration.setAuthenticationModule(cmpAlias, CmpConfiguration.AUTHMODULE_ENDENTITY_CERTIFICATE);
        cmpConfiguration.setAuthenticationParameters(cmpAlias, "TestCA");
        globalConfigurationSession.saveConfiguration(admin, cmpConfiguration, Configuration.CMPConfigID);

        //------------------ create the user and issue his first certificate -------------
        createUser(username, userDN, "foo123");
        KeyPair keys = KeyTools.genKeys("512", AlgorithmConstants.KEYALGORITHM_RSA);
        Certificate certificate = (X509Certificate) signSession.createCertificate(admin, username, "foo123", keys.getPublic());
        assertNotNull("Failed to create a test certificate", certificate);

        AlgorithmIdentifier pAlg = new AlgorithmIdentifier(PKCSObjectIdentifiers.sha1WithRSAEncryption);
        PKIMessage req = genRenewalReq(userDN, cacert, nonce, transid, keys, false, null, null, pAlg, new DEROctetString("CMPTESTPROFILE".getBytes()));
        assertNotNull("Failed to generate a CMP renewal request", req);
        
        createUser("cmpTestAdmin", "CN=cmpTestAdmin,C=SE", "foo123");
        KeyPair admkeys = KeyTools.genKeys("1024", "RSA");
        AuthenticationToken admToken = createAdminToken(admkeys, "cmpTestAdmin", "CN=cmpTestAdmin,C=SE");
        Certificate admCert = getCertFromCredentials(admToken);
        CMPCertificate[] extraCert = getCMPCert(admCert);
        req = CmpMessageHelper.buildCertBasedPKIProtection(req, extraCert, admkeys.getPrivate(), pAlg.getAlgorithm().getId(), "BC");
        assertNotNull(req);

        ByteArrayOutputStream bao = new ByteArrayOutputStream();
        DEROutputStream out = new DEROutputStream(bao);
        out.writeObject(req);
        byte[] ba = bao.toByteArray();
        //send request and recieve response
        byte[] resp = sendCmpHttp(ba, 200, cmpAlias);
        checkCmpResponseGeneral(resp, issuerDN, userDN, cacert, nonce, transid, false, null, PKCSObjectIdentifiers.sha1WithRSAEncryption.getId());
        
        PKIMessage respObject = null;
        ASN1InputStream asn1InputStream = new ASN1InputStream(new ByteArrayInputStream(resp));
        try {
            respObject = PKIMessage.getInstance(asn1InputStream.readObject());
        } finally {
            asn1InputStream.close();
        }
        assertNotNull(respObject);

        final PKIBody body = respObject.getBody();
        assertEquals(23, body.getType());
        ErrorMsgContent err = (ErrorMsgContent) body.getContent();
        final String errMsg = err.getPKIStatusInfo().getStatusString().getStringAt(0).getString();
        final String expectedErrMsg = "Cannot find a SubjectDN in the request";
        assertEquals(expectedErrMsg, errMsg);

        removeAuthenticationToken(admToken, admCert, "cmpTestAdmin");
        
        if(log.isTraceEnabled()) {
            log.trace("<test12RANoIssuerNoSubjectDN()");
        }

    }
    
    /**
     * Sends a KeyUpdateRequest in RA mode when there are more than one authentication module configured. 
     * Successful operation is expected and a new certificate is received.
     * 
     * - Pre-configuration: Sets the operational mode to RA mode (cmp.raoperationalmode=ra)
     * - Pre-configuration: Sets the cmp.authenticationmodule to "HMAC;DnPartPwd;EndEntityCertificate"
     * - Pre-configuration: Sets the cmp.authenticationparameters to "-;OU;TestCA"
     * - Pre-configuration: Set cmp.checkadminauthorization to 'true'
     * - Creates a new user and obtains a certificate, cert, for this user. Tests whether obtaining the certificate was successful.
     * - Generates a CMP KeyUpdate Request and tests that such request has been created.
     * - Signs the CMP request using cert and attaches cert to the CMP request. Tests that the CMP request is still not null
     * - Verifies the signature of the CMP request
     * - Sends the request using HTTP and receives an response.
     * - Examines the response:
     *      - Checks that the response is not empty or null
     *      - Checks that the protection algorithm is sha1WithRSAEncryption
     *      - Check that the signer is the expected CA
     *      - Verifies the response signature
     *      - Checks that the response's senderNonce is 16 bytes long
     *      - Checks that the request's senderNonce is the same as the response's recipientNonce
     *      - Checks that the request and the response has the same transactionID
     *      - Obtains the certificate from the response
     *      - Checks that the obtained certificate has the right subjectDN and issuerDN
     * 
     * @throws Exception
     */
    @Test
    public void test11RAMultipleAuthenticationModules() throws Exception {
        if(log.isTraceEnabled()) {
            log.trace("test13RAMultipleAuthenticationModules");
        }
        
        cmpConfiguration.setRAMode(cmpAlias, true);
        String authmodules = CmpConfiguration.AUTHMODULE_HMAC + ";" + CmpConfiguration.AUTHMODULE_DN_PART_PWD + ";" + CmpConfiguration.AUTHMODULE_ENDENTITY_CERTIFICATE;
        cmpConfiguration.setAuthenticationModule(cmpAlias, authmodules);
        cmpConfiguration.setAuthenticationParameters(cmpAlias, "-;OU;TestCA");
        cmpConfiguration.setKurAllowAutomaticUpdate(cmpAlias, true);
        globalConfigurationSession.saveConfiguration(admin, cmpConfiguration, Configuration.CMPConfigID);

        //------------------ create the user and issue his first certificate -------------
        createUser(username, userDN, "foo123");
        KeyPair keys = KeyTools.genKeys("512", AlgorithmConstants.KEYALGORITHM_RSA);
        Certificate certificate = (X509Certificate) signSession.createCertificate(admin, username, "foo123", keys.getPublic());
        assertNotNull("Failed to create a test certificate", certificate);

        AlgorithmIdentifier pAlg = new AlgorithmIdentifier(PKCSObjectIdentifiers.sha1WithRSAEncryption);
        PKIMessage req = genRenewalReq(userDN, cacert, nonce, transid, keys, false, userDN, null, pAlg, new DEROctetString("CMPTESTPROFILE".getBytes()));
        assertNotNull("Failed to generate a CMP renewal request", req);
        CertReqMessages kur = (CertReqMessages) req.getBody().getContent();
        int reqId = kur.toCertReqMsgArray()[0].getCertReq().getCertReqId().getValue().intValue();
        
        createUser("cmpTestAdmin", "CN=cmpTestAdmin,C=SE", "foo123");
        KeyPair admkeys = KeyTools.genKeys("1024", "RSA");
        AuthenticationToken admToken = createAdminToken(admkeys, "cmpTestAdmin", "CN=cmpTestAdmin,C=SE");
        Certificate admCert = getCertFromCredentials(admToken);
        CMPCertificate[] extraCert = getCMPCert(admCert);
        req = CmpMessageHelper.buildCertBasedPKIProtection(req, extraCert, admkeys.getPrivate(), pAlg.getAlgorithm().getId(), "BC");
        assertNotNull(req);

        ByteArrayOutputStream bao = new ByteArrayOutputStream();
        DEROutputStream out = new DEROutputStream(bao);
        out.writeObject(req);
        byte[] ba = bao.toByteArray();
        //send request and recieve response
        byte[] resp = sendCmpHttp(ba, 200, cmpAlias);
        checkCmpResponseGeneral(resp, issuerDN, userDN, cacert, nonce, transid, false, null, PKCSObjectIdentifiers.sha1WithRSAEncryption.getId());
        X509Certificate cert = checkKurCertRepMessage(userDN, cacert, resp, reqId);
        assertNotNull("Failed to renew the certificate", cert);
        
        removeAuthenticationToken(admToken, admCert, "cmpTestAdmin");
        
        if(log.isTraceEnabled()) {
            log.trace("<test13RAMultipleAuthenticationModules()");
        }

    }

    /**
     * Sends a KeyUpdateRequest in RA mode when the authentication module is NOT set to 'EndEntityCertificate'. 
     * A CMP error message is expected and no certificate renewal.
     * 
     * - Pre-configuration: Sets the operational mode to RA mode (cmp.raoperationalmode=ra)
     * - Pre-configuration: Sets the cmp.authenticationmodule to 'DnPartPwd'
     * - Pre-configuration: Sets the cmp.authenticationparameters to 'OU'
     * - Creates a new user and obtains a certificate, cert, for this user. Tests whether obtaining the certificate was successful.
     * - Generates a CMP KeyUpdate Request and tests that such request has been created.
     * - Signs the CMP request using cert and attaches cert to the CMP request. Tests that the CMP request is still not null
     * - Verifies the signature of the CMP request
     * - Sends the request using HTTP and receives an response.
     * - Examines the response:
     *      - Checks that the response is not empty or null
     *      - Checks that the protection algorithm is sha1WithRSAEncryption
     *      - Check that the signer is the expected CA
     *      - Verifies the response signature
     *      - Checks that the response's senderNonce is 16 bytes long
     *      - Checks that the request's senderNonce is the same as the response's recipientNonce
     *      - Checks that the request and the response has the same transactionID
     *      - Obtains the certificate from the response
     *      - Checks that the obtained certificate has the right subjectDN and issuerDN
     * 
     * @throws Exception
     */
    @Test
    public void test12ECCNotSetInRA() throws Exception {
        if(log.isTraceEnabled()) {
            log.trace("test12ECCNotSetInRA()");
        }
        
        cmpConfiguration.setRAMode(cmpAlias, true);
        cmpConfiguration.setAuthenticationModule(cmpAlias, CmpConfiguration.AUTHMODULE_DN_PART_PWD);
        cmpConfiguration.setAuthenticationParameters(cmpAlias, "OU");
        cmpConfiguration.setKurAllowAutomaticUpdate(cmpAlias, true);
        cmpConfiguration.setKurAllowSameKey(cmpAlias, true);
        cmpConfiguration.setCMPDefaultCA(cmpAlias, "");
        globalConfigurationSession.saveConfiguration(admin, cmpConfiguration, Configuration.CMPConfigID);

        //------------------ create the user and issue his first certificate -------------
        createUser(username, userDN, "foo123");
        KeyPair keys = KeyTools.genKeys("512", AlgorithmConstants.KEYALGORITHM_RSA);
        Certificate certificate = (X509Certificate) signSession.createCertificate(admin, username, "foo123", keys.getPublic());
        assertNotNull("Failed to create a test certificate", certificate);

        AlgorithmIdentifier pAlg = new AlgorithmIdentifier(PKCSObjectIdentifiers.sha1WithRSAEncryption);
        PKIMessage req = genRenewalReq(userDN, cacert, nonce, transid, keys, false, userDN, null, pAlg, null);
        assertNotNull("Failed to generate a CMP renewal request", req);
        
        createUser("cmpTestAdmin", "CN=cmpTestAdmin,C=SE", "foo123");
        KeyPair admkeys = KeyTools.genKeys("1024", "RSA");
        AuthenticationToken admToken = createAdminToken(admkeys, "cmpTestAdmin", "CN=cmpTestAdmin,C=SE");
        Certificate admCert = getCertFromCredentials(admToken);
        CMPCertificate[] extraCert = getCMPCert(admCert);
        req = CmpMessageHelper.buildCertBasedPKIProtection(req, extraCert, admkeys.getPrivate(), pAlg.getAlgorithm().getId(), "BC");
        assertNotNull(req);

        ByteArrayOutputStream bao = new ByteArrayOutputStream();
        DEROutputStream out = new DEROutputStream(bao);
        out.writeObject(req);
        byte[] ba = bao.toByteArray();
        //send request and recieve response
        byte[] resp = sendCmpHttp(ba, 200, cmpAlias);
        checkCmpResponseGeneral(resp, issuerDN, userDN, cacert, nonce, transid, false, null, PKCSObjectIdentifiers.sha1WithRSAEncryption.getId());
        
        PKIMessage respObject = null;
        ASN1InputStream asn1InputStream = new ASN1InputStream(new ByteArrayInputStream(resp));
        try {
            respObject = PKIMessage.getInstance(asn1InputStream.readObject());
        } finally {
            asn1InputStream.close();
        }
        assertNotNull(respObject);

        final PKIBody body = respObject.getBody();
        assertEquals(23, body.getType());
        ErrorMsgContent err = (ErrorMsgContent) body.getContent();
        final String errMsg = err.getPKIStatusInfo().getStatusString().getStringAt(0).getString();
        final String expectedErrMsg = "EndEnityCertificate authentication module is not configured. For a KeyUpdate request to be authentication " +
        		                        "in RA mode, EndEntityCertificate authentication module has to be set and configured";
        assertEquals(expectedErrMsg, errMsg);

        removeAuthenticationToken(admToken, admCert, "cmpTestAdmin");
        
        if(log.isTraceEnabled()) {
            log.trace("<test12ECCNotSetInRA()");
        }

    }
 
    /**
     * Sends a KeyUpdateRequest by an admin concerning a certificate of another EndEntity in client mode. 
     * If the CA enforces unique public key, a CMP error message is expected and no certificate renewal.
     * If the CA does not enforce unique public key, a certificate will be renewed, though not the expected EndEntity certificate, but the admin certificate is renewed.
     * 
     * - Pre-configuration: Sets the operational mode to client mode (cmp.raoperationalmode=normal)
     * - Pre-configuration: Sets the cmp.authenticationmodule to 'EndEntityCertificate'
     * - Pre-configuration: Sets the cmp.authenticationparameters to 'TestCA'
     * - Pre-configuration: Sets the cmp.allowautomatickeyupdate to 'true'
     * - Creates a new user and obtains a certificate, cert, for this user. Tests whether obtaining the certificate was successful.
     * - Generates a CMP KeyUpdate Request and tests that such request has been created.
     * - Signs the CMP request using cert and attaches cert to the CMP request. Tests that the CMP request is still not null
     * - Verifies the signature of the CMP request
     * - Sends the request using HTTP and receives an response.
     * - Examines the response:
     *      - Checks that the response is not empty or null
     *      - Checks that the protection algorithm is sha1WithRSAEncryption
     *      - Check that the signer is the expected CA
     *      - Verifies the response signature
     *      - Checks that the response's senderNonce is 16 bytes long
     *      - Checks that the request's senderNonce is the same as the response's recipientNonce
     *      - Checks that the request and the response has the same transactionID
     *      - Obtains the certificate from the response
     *      - Checks that the obtained certificate has the right subjectDN and issuerDN
     * 
     * @throws Exception
     */
    @Test
    public void test13AdminInClientMode() throws Exception {
        if(log.isTraceEnabled()) {
            log.trace("test09RAMode()");
        }
        
        cmpConfiguration.setRAMode(cmpAlias, false);
        cmpConfiguration.setAuthenticationModule(cmpAlias, CmpConfiguration.AUTHMODULE_ENDENTITY_CERTIFICATE);
        cmpConfiguration.setAuthenticationParameters(cmpAlias, "TestCA");
        cmpConfiguration.setKurAllowAutomaticUpdate(cmpAlias, true);
        globalConfigurationSession.saveConfiguration(admin, cmpConfiguration, Configuration.CMPConfigID);
        
        //------------------ create the user and issue his first certificate -------------
        createUser(username, userDN, "foo123");
        KeyPair keys = KeyTools.genKeys("512", AlgorithmConstants.KEYALGORITHM_RSA);
        Certificate certificate = null;
        certificate = (X509Certificate) signSession.createCertificate(admin, username, "foo123", keys.getPublic());
        assertNotNull("Failed to create a test certificate", certificate);

        AlgorithmIdentifier pAlg = new AlgorithmIdentifier(PKCSObjectIdentifiers.sha1WithRSAEncryption);
        PKIMessage req = genRenewalReq(userDN, cacert, nonce, transid, keys, false, userDN, issuerDN, pAlg, new DEROctetString("CMPTESTPROFILE".getBytes()));
        assertNotNull("Failed to generate a CMP renewal request", req);
        //int reqId = req.getBody().getKur().getCertReqMsg(0).getCertReq().getCertReqId().getValue().intValue();
        
        createUser("cmpTestAdmin", "CN=cmpTestAdmin,C=SE", "foo123");
        KeyPair admkeys = KeyTools.genKeys("1024", "RSA");
        AuthenticationToken admToken = createAdminToken(admkeys, "cmpTestAdmin", "CN=cmpTestAdmin,C=SE");
        Certificate admCert = getCertFromCredentials(admToken);
        CMPCertificate[] extraCert = getCMPCert(admCert);
        req = CmpMessageHelper.buildCertBasedPKIProtection(req, extraCert, admkeys.getPrivate(), pAlg.getAlgorithm().getId(), "BC");
        assertNotNull(req);
        
        ByteArrayOutputStream bao = new ByteArrayOutputStream();
        DEROutputStream out = new DEROutputStream(bao);
        out.writeObject(req);
        byte[] ba = bao.toByteArray();
        //send request and recieve response
        byte[] resp = sendCmpHttp(ba, 200, cmpAlias);
        checkCmpResponseGeneral(resp, issuerDN, userDN, cacert, nonce, transid, false, null, PKCSObjectIdentifiers.sha1WithRSAEncryption.getId());
        PKIMessage respObject = null;
        ASN1InputStream asn1InputStream = new ASN1InputStream(new ByteArrayInputStream(resp));
        try {
            respObject = PKIMessage.getInstance(asn1InputStream.readObject());
        } finally {
            asn1InputStream.close();
        }
        assertNotNull(respObject);
        
        
        CAInfo cainfo = caSession.getCAInfo(admin, caid);
        if(cainfo.isDoEnforceUniquePublicKeys()) {
            final PKIBody body = respObject.getBody();
            assertEquals(23, body.getType());
            ErrorMsgContent err = (ErrorMsgContent) body.getContent();
            final String errMsg = err.getPKIStatusInfo().getStatusString().getStringAt(0).getString();
            final String expectedErrMsg = "User 'cmpTestAdmin' is not allowed to use same key as the user(s) '" + username + "' is/are using.";
            assertEquals(expectedErrMsg, errMsg);
        } else {
            PKIBody body = respObject.getBody();
            int tag = body.getType();
            assertEquals(8, tag);
            CertRepMessage c = (CertRepMessage) body.getContent();
            assertNotNull(c);            
            CMPCertificate cmpcert = c.getResponse()[0].getCertifiedKeyPair().getCertOrEncCert().getCertificate();
            assertNotNull(cmpcert);
            X509Certificate cert = (X509Certificate) CertTools.getCertfromByteArray(cmpcert.getEncoded());
            assertNotNull("Failed to renew the certificate", cert);
            assertEquals("CN=cmpTestAdmin, C=SE", cert.getSubjectX500Principal().toString());
        }

        removeAuthenticationToken(admToken, admCert, "cmpTestAdmin");

        if(log.isTraceEnabled()) {
            log.trace("<test09RAMode()");
        }
    }
    
    /**
     * Sends a KeyUpdateRequest by an EndEntity concerning its own certificate in RA mode. 
     * A CMP error message is expected and no certificate renewal.
     * 
     * @throws Exception
     */
    @Test
    public void test14EndEntityRequestingInRAMode() throws Exception {
        if(log.isTraceEnabled()) {
            log.trace(">test14KeyUpdateRequestOK");
        }
        
        cmpConfiguration.setRAMode(cmpAlias, true);
        cmpConfiguration.setAuthenticationModule(cmpAlias, CmpConfiguration.AUTHMODULE_ENDENTITY_CERTIFICATE);
        cmpConfiguration.setAuthenticationParameters(cmpAlias, "TestCA");
        cmpConfiguration.setKurAllowAutomaticUpdate(cmpAlias, true);
        cmpConfiguration.setKurAllowSameKey(cmpAlias, true);
        globalConfigurationSession.saveConfiguration(admin, cmpConfiguration, Configuration.CMPConfigID);
        
        //--------------- create the user and issue his first certificate -----------------
        createUser(username, userDN, "foo123");
        KeyPair keys = KeyTools.genKeys("512", AlgorithmConstants.KEYALGORITHM_RSA);
        Certificate certificate = null;
        try {
            certificate = (X509Certificate) signSession.createCertificate(admin, username, "foo123", keys.getPublic());
        } catch (ObjectNotFoundException e) {
            throw new CertificateCreationException("Error encountered when creating certificate", e);
        } catch (CADoesntExistsException e) {
            throw new CertificateCreationException("Error encountered when creating certificate", e);
        } catch (EjbcaException e) {
            throw new CertificateCreationException("Error encountered when creating certificate", e);
        } catch (AuthorizationDeniedException e) {
            throw new CertificateCreationException("Error encountered when creating certificate", e);
        } catch (CesecoreException e) {
            throw new CertificateCreationException("Error encountered when creating certificate", e);
        }
        assertNotNull("Failed to create a test certificate", certificate);

        AlgorithmIdentifier pAlg = new AlgorithmIdentifier(PKCSObjectIdentifiers.sha1WithRSAEncryption);
        PKIMessage req = genRenewalReq(userDN, cacert, nonce, transid, keys, false, null, null, pAlg, new DEROctetString(nonce));
        assertNotNull("Failed to generate a CMP renewal request", req);
        //int reqId = req.getBody().getKur().getCertReqMsg(0).getCertReq().getCertReqId().getValue().intValue();

        CMPCertificate[] extraCert = getCMPCert(certificate);
        req = CmpMessageHelper.buildCertBasedPKIProtection(req, extraCert, keys.getPrivate(), pAlg.getAlgorithm().getId(), "BC");
        assertNotNull(req);
        
        ByteArrayOutputStream bao = new ByteArrayOutputStream();
        DEROutputStream out = new DEROutputStream(bao);
        out.writeObject(req);
        byte[] ba = bao.toByteArray();
        // Send request and receive response
        byte[] resp = sendCmpHttp(ba, 200, cmpAlias);
        checkCmpResponseGeneral(resp, issuerDN, userDN, cacert, nonce, transid, false, null, PKCSObjectIdentifiers.sha1WithRSAEncryption.getId());
        
        PKIMessage respObject = null;
        ASN1InputStream asn1InputStream = new ASN1InputStream(new ByteArrayInputStream(resp));
        try {
            respObject = PKIMessage.getInstance(asn1InputStream.readObject());
        } finally {
            asn1InputStream.close();
        }
        assertNotNull(respObject);

        final PKIBody body = respObject.getBody();
        assertEquals(23, body.getType());
        ErrorMsgContent err = (ErrorMsgContent) body.getContent();
        final String errMsg = err.getPKIStatusInfo().getStatusString().getStringAt(0).getString();
        
        final String expectedErrMsg = "'CN=certRenewalUser,O=PrimeKey Solutions AB,C=SE' is not an authorized administrator.";
        assertEquals(expectedErrMsg, errMsg);
        
        if(log.isTraceEnabled()) {
            log.trace("<test14KeyUpdateRequestOK");
        }
    }

    
    /**
     * Tests the possibility to use different signature algorithms in CMP requests and responses.
     * 
     * A KeyUpdate request, signed using ECDSA with SHA256, is sent to a CA that uses RSA with SHA256 as signature algorithm.
     * The expected response is signed by RSA with SHA256.
     * 
     * @throws Exception
     */
    @Test
    public void test15KeyUpdateMixAlgorithms() throws Exception {
        if(log.isTraceEnabled()) {
            log.trace(">test15KeyUpdateMixAlgorithms");
        }
        
        cmpConfiguration.setKurAllowAutomaticUpdate(cmpAlias, true);
        cmpConfiguration.setKurAllowSameKey(cmpAlias, true);
        globalConfigurationSession.saveConfiguration(admin, cmpConfiguration, Configuration.CMPConfigID);
        
        //--------------- create the user and issue his first certificate -----------------
        createUser(username, userDN, "foo123");
        KeyPair keys = KeyTools.genKeys("prime192v1", AlgorithmConstants.KEYALGORITHM_ECDSA);
        Certificate certificate = null;
        try {
            certificate = (X509Certificate) signSession.createCertificate(admin, username, "foo123", keys.getPublic());
        } catch (ObjectNotFoundException e) {
            throw new CertificateCreationException("Error encountered when creating certificate", e);
        } catch (CADoesntExistsException e) {
            throw new CertificateCreationException("Error encountered when creating certificate", e);
        } catch (EjbcaException e) {
            throw new CertificateCreationException("Error encountered when creating certificate", e);
        } catch (AuthorizationDeniedException e) {
            throw new CertificateCreationException("Error encountered when creating certificate", e);
        } catch (CesecoreException e) {
            throw new CertificateCreationException("Error encountered when creating certificate", e);
        }
        assertNotNull("Failed to create a test certificate", certificate);

        AlgorithmIdentifier pAlg = new AlgorithmIdentifier(X9ObjectIdentifiers.ecdsa_with_SHA256);
        PKIMessage req = genRenewalReq(userDN, cacert, nonce, transid, keys, false, null, null, pAlg, new DEROctetString(nonce));
        assertNotNull("Failed to generate a CMP renewal request", req);
        CertReqMessages kur = (CertReqMessages) req.getBody().getContent();
        int reqId = kur.toCertReqMsgArray()[0].getCertReq().getCertReqId().getValue().intValue();
        CMPCertificate[] extraCert = getCMPCert(certificate);
        req = CmpMessageHelper.buildCertBasedPKIProtection(req, extraCert, keys.getPrivate(), CMSSignedGenerator.DIGEST_SHA256, "BC");
        assertNotNull(req);
        
        ByteArrayOutputStream bao = new ByteArrayOutputStream();
        DEROutputStream out = new DEROutputStream(bao);
        out.writeObject(req);
        byte[] ba = bao.toByteArray();
        // Send request and receive response
        byte[] resp = sendCmpHttp(ba, 200, cmpAlias);
        checkCmpResponseGeneral(resp, issuerDN, userDN, cacert, nonce, transid, true, null, PKCSObjectIdentifiers.sha256WithRSAEncryption.getId());
        X509Certificate cert = checkKurCertRepMessage(userDN, cacert, resp, reqId);
        assertNotNull("Failed to renew the certificate", cert);
        assertTrue("The new certificate's keys are incorrect.", cert.getPublicKey().equals(keys.getPublic()));
        
        if(log.isTraceEnabled()) {
            log.trace("<test15KeyUpdateMixAlgorithms");
        }

    }

    

    
    
    private CMPCertificate[] getCMPCert(Certificate cert) throws CertificateEncodingException, IOException {
        ASN1InputStream ins = new ASN1InputStream(cert.getEncoded());
        ASN1Primitive pcert = ins.readObject();
        ins.close();
        org.bouncycastle.asn1.x509.Certificate c = org.bouncycastle.asn1.x509.Certificate.getInstance(pcert.toASN1Primitive());
        CMPCertificate[] res = {new CMPCertificate(c)};
        return res;
    }

    private EndEntityInformation createUser(String username, String subjectDN, String password) throws AuthorizationDeniedException, UserDoesntFullfillEndEntityProfile, 
                WaitingForApprovalException, EjbcaException, Exception {

        EndEntityInformation user = new EndEntityInformation(username, subjectDN, caid, null, username+"@primekey.se", new EndEntityType(EndEntityTypes.ENDUSER), SecConst.EMPTY_ENDENTITYPROFILE,
        CertificateProfileConstants.CERTPROFILE_FIXED_ENDUSER, SecConst.TOKEN_SOFT_PEM, 0, null);
        user.setPassword(password);
        try {
            //endEntityManagementSession.addUser(ADMIN, user, true);
            endEntityManagementSession.addUser(admin, username, password, subjectDN, "rfc822name=" + username + "@primekey.se", username + "@primekey.se",
                    true, SecConst.EMPTY_ENDENTITYPROFILE, CertificateProfileConstants.CERTPROFILE_FIXED_ENDUSER, EndEntityTypes.ENDUSER.toEndEntityType(), SecConst.TOKEN_SOFT_PEM, 0,
                    caid);
            log.debug("created user: " + username);
        } catch (Exception e) {
            log.debug("User " + username + " already exists. Setting the user status to NEW");
            endEntityManagementSession.changeUser(admin, user, true);
            endEntityManagementSession.setUserStatus(admin, username, EndEntityConstants.STATUS_NEW);
            log.debug("Reset status to NEW");
        }

        return user;

    }

    @Override
    public String getRoleName() {
        return this.getClass().getSimpleName(); 
    }
    
    private X509Certificate checkKurCertRepMessage(String userDN, Certificate cacert, byte[] retMsg, int requestId) throws IOException,
                        CertificateException {
        //
        // Parse response message
        //
        
        PKIMessage respObject = null;
        ASN1InputStream asn1InputStream = new ASN1InputStream(new ByteArrayInputStream(retMsg));
        try {
            respObject = PKIMessage.getInstance(asn1InputStream.readObject());
        } finally {
            asn1InputStream.close();
        }
        
        assertNotNull(respObject);

        PKIBody body = respObject.getBody();
        int tag = body.getType();
        assertEquals(8, tag);
        CertRepMessage c = (CertRepMessage) body.getContent();
        assertNotNull(c);
        CertResponse resp = c.getResponse()[0];
        assertNotNull(resp);
        assertEquals(resp.getCertReqId().getValue().intValue(), requestId);
        PKIStatusInfo info = resp.getStatus();
        assertNotNull(info);
        assertEquals(0, info.getStatus().intValue());
        CMPCertificate cmpcert = c.getCaPubs()[0]; //cc.getCertificate();
        assertNotNull(cmpcert);
        X509Certificate cert = (X509Certificate) CertTools.getCertfromByteArray(cmpcert.getEncoded());
        X500Name name = new X500Name(CertTools.getSubjectDN(cert));
        checkDN(userDN, name);
        assertEquals(CertTools.stringToBCDNString(CertTools.getIssuerDN(cert)), CertTools.getSubjectDN(cacert));
        return cert;
    }
    
    private X509Certificate getCertFromCredentials(AuthenticationToken authToken) {
        X509Certificate certificate = null;
        Set<?> inputcreds = authToken.getCredentials();
        if (inputcreds != null) {
            for (Object object : inputcreds) {
                if (object instanceof X509Certificate) {
                    certificate = (X509Certificate) object;
                }
            }
        }
        return certificate;
    }

    private AuthenticationToken createAdminToken(KeyPair keys, String name, String dn) throws RoleExistsException, RoleNotFoundException,
            CreateException, AuthorizationDeniedException {
        Set<Principal> principals = new HashSet<Principal>();
        X500Principal p = new X500Principal(dn);
        principals.add(p);
        AuthenticationSubject subject = new AuthenticationSubject(principals, null);
        AuthenticationToken token = createTokenWithCert(name, subject, keys);
        X509Certificate cert = (X509Certificate) token.getCredentials().iterator().next();

        // Initialize the role mgmt system with this role that is allowed to edit roles

        String roleName = "Super Administrator Role";
        RoleData roledata = roleAccessSessionRemote.findRole(roleName);
        // Create a user aspect that matches the authentication token, and add that to the role.
        List<AccessUserAspectData> accessUsers = new ArrayList<AccessUserAspectData>();
        accessUsers.add(new AccessUserAspectData(roleName, CertTools.getIssuerDN(cert).hashCode(), X500PrincipalAccessMatchValue.WITH_COMMONNAME,
                AccessMatchType.TYPE_EQUALCASEINS, CertTools.getPartFromDN(CertTools.getSubjectDN(cert), "CN")));
        roleManagementSession.addSubjectsToRole(admin, roledata, accessUsers);

        return token;
    }

    private AuthenticationToken createTokenWithCert(String adminName, AuthenticationSubject subject, KeyPair keys) {

        // A small check if we have added a "fail" credential to the subject.
        // If we have we will return null, so we can test authentication failure.
        Set<?> usercredentials = subject.getCredentials();
        if ((usercredentials != null) && (usercredentials.size() > 0)) {
            Object o = usercredentials.iterator().next();
            if (o instanceof String) {
                String str = (String) o;
                if (StringUtils.equals("fail", str)) {
                    return null;
                }
            }
        }

        X509Certificate certificate = null;
        // If we have a certificate as input, use that, otherwise generate a self signed certificate
        Set<X509Certificate> credentials = new HashSet<X509Certificate>();

        // If there was no certificate input, create a self signed
        String dn = "C=SE,O=Test,CN=Test"; // default
        // If we have created a subject with an X500Principal we will use this DN to create the dummy certificate.
        if (subject != null) {
            Set<Principal> principals = subject.getPrincipals();
            if ((principals != null) && (principals.size() > 0)) {
                Principal p = principals.iterator().next();
                if (p instanceof X500Principal) {
                    X500Principal xp = (X500Principal) p;
                    dn = xp.getName();
                }
            }
        }

        try {
            createUser(adminName, dn, "foo123");
        } catch (AuthorizationDeniedException e1) {
            throw new CertificateCreationException("Error encountered when creating admin user", e1);
        } catch (UserDoesntFullfillEndEntityProfile e1) {
            throw new CertificateCreationException("Error encountered when creating admin user", e1);
        } catch (WaitingForApprovalException e1) {
            throw new CertificateCreationException("Error encountered when creating admin user", e1);
        } catch (EjbcaException e1) {
            throw new CertificateCreationException("Error encountered when creating admin user", e1);
        } catch (Exception e1) {
            throw new CertificateCreationException("Error encountered when creating admin user", e1);
        }

        try {
            certificate = (X509Certificate) signSession.createCertificate(admin, adminName, "foo123", keys.getPublic());
        } catch (ObjectNotFoundException e) {
            throw new CertificateCreationException("Error encountered when creating certificate", e);
        } catch (CADoesntExistsException e) {
            throw new CertificateCreationException("Error encountered when creating certificate", e);
        } catch (EjbcaException e) {
            throw new CertificateCreationException("Error encountered when creating certificate", e);
        } catch (AuthorizationDeniedException e) {
            throw new CertificateCreationException("Error encountered when creating certificate", e);
        } catch (CesecoreException e) {
            throw new CertificateCreationException("Error encountered when creating certificate", e);
        }

        // Add the credentials and new principal
        credentials.add(certificate);
        Set<X500Principal> principals = new HashSet<X500Principal>();
        principals.add(certificate.getSubjectX500Principal());

        // We cannot use the X509CertificateAuthenticationToken here, since it can only be used internally in a JVM.
        AuthenticationToken result = new TestX509CertificateAuthenticationToken(principals, credentials);
        return result;
    }

    private void removeAuthenticationToken(AuthenticationToken authToken, Certificate cert, String adminName) throws RoleNotFoundException,
            AuthorizationDeniedException, ApprovalException, NotFoundException, WaitingForApprovalException, RemoveException {
        String rolename = "Super Administrator Role";

        RoleData roledata = roleAccessSessionRemote.findRole("Super Administrator Role");
        if (roledata != null) {

            List<AccessUserAspectData> accessUsers = new ArrayList<AccessUserAspectData>();
            accessUsers.add(new AccessUserAspectData(rolename, CertTools.getIssuerDN(cert).hashCode(), X500PrincipalAccessMatchValue.WITH_COMMONNAME,
                    AccessMatchType.TYPE_EQUALCASEINS, CertTools.getPartFromDN(CertTools.getSubjectDN(cert), "CN")));

            roleManagementSession.removeSubjectsFromRole(admin, roledata, accessUsers);
        }

        endEntityManagementSession.revokeAndDeleteUser(admin, adminName, RevokedCertInfo.REVOCATION_REASON_UNSPECIFIED);
    }

}
