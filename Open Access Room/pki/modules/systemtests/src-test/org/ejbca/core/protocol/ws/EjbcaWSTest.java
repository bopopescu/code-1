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
package org.ejbca.core.protocol.ws;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.File;
import java.security.KeyPair;
import java.security.NoSuchAlgorithmException;
import java.security.Principal;
import java.security.SecureRandom;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TimeZone;

import org.apache.commons.lang.time.FastDateFormat;
import org.apache.log4j.Logger;
import org.bouncycastle.asn1.DERSet;
import org.bouncycastle.pkcs.PKCS10CertificationRequest;
import org.cesecore.CaTestUtils;
import org.cesecore.ErrorCode;
import org.cesecore.authentication.tokens.AuthenticationSubject;
import org.cesecore.authentication.tokens.AuthenticationToken;
import org.cesecore.authorization.AuthorizationDeniedException;
import org.cesecore.certificates.ca.CADoesntExistsException;
import org.cesecore.certificates.ca.CAInfo;
import org.cesecore.certificates.ca.CaSessionRemote;
import org.cesecore.certificates.ca.catoken.CAToken;
import org.cesecore.certificates.certificate.CertificateStoreSessionRemote;
import org.cesecore.certificates.certificateprofile.CertificateProfile;
import org.cesecore.certificates.certificateprofile.CertificateProfileConstants;
import org.cesecore.certificates.certificateprofile.CertificateProfileSessionRemote;
import org.cesecore.certificates.crl.RevokedCertInfo;
import org.cesecore.certificates.endentity.EndEntityInformation;
import org.cesecore.certificates.endentity.EndEntityType;
import org.cesecore.certificates.endentity.EndEntityTypes;
import org.cesecore.certificates.endentity.ExtendedInformation;
import org.cesecore.certificates.util.AlgorithmConstants;
import org.cesecore.configuration.CesecoreConfigurationProxySessionRemote;
import org.cesecore.keys.token.CryptoTokenManagementSessionTest;
import org.cesecore.keys.util.KeyTools;
import org.cesecore.mock.authentication.SimpleAuthenticationProviderSessionRemote;
import org.cesecore.util.Base64;
import org.cesecore.util.CertTools;
import org.cesecore.util.EjbRemoteHelper;
import org.ejbca.config.Configuration;
import org.ejbca.config.EjbcaConfiguration;
import org.ejbca.config.GlobalConfiguration;
import org.ejbca.core.EjbcaException;
import org.ejbca.core.ejb.approval.ApprovalExecutionSessionRemote;
import org.ejbca.core.ejb.approval.ApprovalSessionRemote;
import org.ejbca.core.ejb.ca.caadmin.CAAdminSessionRemote;
import org.ejbca.core.ejb.config.GlobalConfigurationSessionRemote;
import org.ejbca.core.ejb.hardtoken.HardTokenSessionRemote;
import org.ejbca.core.ejb.ra.EndEntityAccessSessionRemote;
import org.ejbca.core.model.SecConst;
import org.ejbca.core.model.approval.ApprovalDataVO;
import org.ejbca.core.model.approval.approvalrequests.RevocationApprovalTest;
import org.ejbca.core.model.hardtoken.HardTokenConstants;
import org.ejbca.core.protocol.ws.client.gen.AlreadyRevokedException_Exception;
import org.ejbca.core.protocol.ws.client.gen.ApprovalException_Exception;
import org.ejbca.core.protocol.ws.client.gen.CertificateResponse;
import org.ejbca.core.protocol.ws.client.gen.HardTokenDataWS;
import org.ejbca.core.protocol.ws.client.gen.IllegalQueryException_Exception;
import org.ejbca.core.protocol.ws.client.gen.KeyStore;
import org.ejbca.core.protocol.ws.client.gen.PinDataWS;
import org.ejbca.core.protocol.ws.client.gen.RevokeStatus;
import org.ejbca.core.protocol.ws.client.gen.TokenCertificateRequestWS;
import org.ejbca.core.protocol.ws.client.gen.TokenCertificateResponseWS;
import org.ejbca.core.protocol.ws.client.gen.UserDataVOWS;
import org.ejbca.core.protocol.ws.client.gen.UserMatch;
import org.ejbca.core.protocol.ws.client.gen.WaitingForApprovalException_Exception;
import org.ejbca.core.protocol.ws.common.CertificateHelper;
import org.ejbca.core.protocol.ws.common.KeyStoreHelper;
import org.ejbca.ui.cli.batch.BatchMakeP12;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runners.MethodSorters;

/**
 * This test uses remote EJB calls to setup the environment.
 * 
 * @version $Id: EjbcaWSTest.java 18246 2013-12-06 10:57:56Z anatom $
 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class EjbcaWSTest extends CommonEjbcaWS {

    private static final Logger log = Logger.getLogger(EjbcaWSTest.class);

    public final static String WS_ADMIN_ROLENAME = "WsTEstRole";
    public final static String WS_TEST_ROLENAME = "WsTestRoleMgmt";
    private final static String WS_TEST_CERTIFICATE_PROFILE_NAME = "WSTESTPROFILE"; 
    
    private final String cliUserName = EjbcaConfiguration.getCliDefaultUser();
    private final String cliPassword = EjbcaConfiguration.getCliDefaultPassword();
    
    private final ApprovalExecutionSessionRemote approvalExecutionSession = EjbRemoteHelper.INSTANCE.getRemoteSession(ApprovalExecutionSessionRemote.class);
    private final ApprovalSessionRemote approvalSession = EjbRemoteHelper.INSTANCE.getRemoteSession(ApprovalSessionRemote.class);
    private final CAAdminSessionRemote caAdminSessionRemote = EjbRemoteHelper.INSTANCE.getRemoteSession(CAAdminSessionRemote.class);
    private final CaSessionRemote caSession = EjbRemoteHelper.INSTANCE.getRemoteSession(CaSessionRemote.class);
    private final CertificateStoreSessionRemote certificateStoreSession = EjbRemoteHelper.INSTANCE.getRemoteSession(CertificateStoreSessionRemote.class);
    private final EndEntityAccessSessionRemote endEntityAccessSession = EjbRemoteHelper.INSTANCE.getRemoteSession(EndEntityAccessSessionRemote.class);
    private final HardTokenSessionRemote hardTokenSessionRemote = EjbRemoteHelper.INSTANCE.getRemoteSession(HardTokenSessionRemote.class);
    private final GlobalConfigurationSessionRemote raAdminSession = EjbRemoteHelper.INSTANCE.getRemoteSession(GlobalConfigurationSessionRemote.class);

    private static final CesecoreConfigurationProxySessionRemote cesecoreConfigurationProxySession = EjbRemoteHelper.INSTANCE.getRemoteSession(CesecoreConfigurationProxySessionRemote.class, EjbRemoteHelper.MODULE_TEST);
    private final SimpleAuthenticationProviderSessionRemote simpleAuthenticationProvider = EjbRemoteHelper.INSTANCE.getRemoteSession(SimpleAuthenticationProviderSessionRemote.class, EjbRemoteHelper.MODULE_TEST);
    
    private static String originalForbiddenChars;
    private final static SecureRandom secureRandom;
    private final static String forbiddenCharsKey = "forbidden.characters";
    static {
        try {
            secureRandom = SecureRandom.getInstance("SHA1PRNG");
        } catch (NoSuchAlgorithmException e) {
            throw new Error(e);
        }
    }

    @BeforeClass
    public static void beforeClass() throws Exception {
        adminBeforeClass();
        setupAccessRights(WS_ADMIN_ROLENAME);
        originalForbiddenChars = cesecoreConfigurationProxySession.getConfigurationValue(forbiddenCharsKey);
    }

    @Before
    public void setUpAdmin() throws Exception {
        adminSetUpAdmin();
    }
    
    @AfterClass
    public static void afterClass() throws Exception {
        cleanUpAdmins(WS_ADMIN_ROLENAME);
        cleanUpAdmins(WS_TEST_ROLENAME);
        cesecoreConfigurationProxySession.setConfigurationValue(forbiddenCharsKey, originalForbiddenChars);
        CertificateProfileSessionRemote certificateProfileSession = EjbRemoteHelper.INSTANCE.getRemoteSession(CertificateProfileSessionRemote.class);
        certificateProfileSession.removeCertificateProfile(intAdmin, WS_TEST_CERTIFICATE_PROFILE_NAME);
    }

    @Override
    public String getRoleName() {
        return WS_TEST_ROLENAME;
    }

    @After
    public void tearDown() throws Exception {
        super.tearDown();
    }

    @Test
    public void test01EditUser() throws Exception {
        super.editUser();
    }

    @Test
    public void test02FindUser() throws Exception {
        findUser();
    }

    @Test
    public void test03_1GeneratePkcs10() throws Exception {
        generatePkcs10();
    }

    @Test
    public void test03_2GenerateCrmf() throws Exception {
        generateCrmf();
    }

    @Test
    public void test03_3GenerateSpkac() throws Exception {
        generateSpkac();
    }

    @Test
    public void test03_4GeneratePkcs10Request() throws Exception {
        generatePkcs10Request();
    }

    @Test
    public void test03_5CertificateRequest() throws Exception {
        certificateRequest();
    }

    @Test
    public void test03_6EnforcementOfUniquePublicKeys() throws Exception {
        enforcementOfUniquePublicKeys();
    }

    @Test
    public void test03_6EnforcementOfUniqueSubjectDN() throws Exception {
        enforcementOfUniqueSubjectDN();
    }

    @Test
    public void test04GeneratePkcs12() throws Exception {
        generatePkcs12();
    }

    @Test
    public void test05FindCerts() throws Exception {
        findCerts();
    }

    @Test
    public void test060RevokeCert() throws Exception {
        revokeCert();
    }

    @Test
    public void test061RevokeCertBackdated() throws Exception {
        revokeCertBackdated();
    }

    @Test
    public void test07RevokeToken() throws Exception {
        revokeToken();
    }

    @Test
    public void test08CheckRevokeStatus() throws Exception {
        checkRevokeStatus();
    }

    @Test
    public void test09Utf8EditUser() throws Exception {
        utf8EditUser();
    }

    @Test
    public void test10GetLastCertChain() throws Exception {
        getLastCertChain();
    }

    @Test
    public void test11RevokeUser() throws Exception {
        revokeUser();
    }

    @Test
    public void test12IsAuthorized() throws Exception {
        // This is a superadmin keystore, improve in the future
        isAuthorized(true);
    }

    @Test
    public void test13genTokenCertificates() throws Exception {
        genTokenCertificates(false);
    }

    @Test
    public void test14getExistsHardToken() throws Exception {
        getExistsHardToken();
    }

    @Test
    public void test15getHardTokenData() throws Exception {
        getHardTokenData("12345678", false);
    }

    @Test
    public void test16getHardTokenDatas() throws Exception {
        getHardTokenDatas();
    }

    @Test
    public void test17CustomLog() throws Exception {
        customLog();
    }

    @Test
    public void test18GetCertificate() throws Exception {
        getCertificate();
    }

    @Test
    public void test19RevocationApprovals() throws Exception {
        log.trace(">test19RevocationApprovals");
        final String APPROVINGADMINNAME = "superadmin";
        final String TOKENSERIALNUMBER = "42424242";
        final String TOKENUSERNAME = "WSTESTTOKENUSER3";
        final String ERRORNOTSENTFORAPPROVAL = "The request was never sent for approval.";
        final String ERRORNOTSUPPORTEDSUCCEEDED = "Reactivation of users is not supported, but succeeded anyway.";

        // Generate random username and CA name
        String randomPostfix = Integer.toString(secureRandom.nextInt(999999));
        String caname = "wsRevocationCA" + randomPostfix;
        String username = "wsRevocationUser" + randomPostfix;
        int cryptoTokenId = 0;
        int caID = -1;
        try {
            cryptoTokenId = CryptoTokenManagementSessionTest.createCryptoTokenForCA(intAdmin, caname, "1024");
            final CAToken catoken = CaTestUtils.createCaToken(cryptoTokenId, AlgorithmConstants.SIGALG_SHA1_WITH_RSA, AlgorithmConstants.SIGALG_SHA1_WITH_RSA);
            caID = RevocationApprovalTest.createApprovalCA(intAdmin, caname, CAInfo.REQ_APPROVAL_REVOCATION, caAdminSessionRemote, caSession, catoken);
            X509Certificate adminCert = (X509Certificate) certificateStoreSession.findCertificatesByUsername(APPROVINGADMINNAME).iterator().next();
            Set<X509Certificate> credentials = new HashSet<X509Certificate>();
            credentials.add(adminCert);
            Set<Principal> principals = new HashSet<Principal>();
            principals.add(adminCert.getSubjectX500Principal());
            AuthenticationToken approvingAdmin = simpleAuthenticationProvider.authenticate(new AuthenticationSubject(principals, credentials));
            //AuthenticationToken approvingAdmin = new X509CertificateAuthenticationToken(principals, credentials);
            //Admin approvingAdmin = new Admin(adminCert, APPROVINGADMINNAME, null);
            try {
                X509Certificate cert = createUserAndCert(username, caID);
                String issuerdn = cert.getIssuerDN().toString();
                String serno = cert.getSerialNumber().toString(16);
                // revoke via WS and verify response
                try {
                    ejbcaraws.revokeCert(issuerdn, serno, RevokedCertInfo.REVOCATION_REASON_CERTIFICATEHOLD);
                    assertTrue(ERRORNOTSENTFORAPPROVAL, false);
                } catch (WaitingForApprovalException_Exception e1) {
                }
                try {
                    ejbcaraws.revokeCert(issuerdn, serno, RevokedCertInfo.REVOCATION_REASON_CERTIFICATEHOLD);
                    assertTrue(ERRORNOTSENTFORAPPROVAL, false);
                } catch (ApprovalException_Exception e1) {
                }
                RevokeStatus revokestatus = ejbcaraws.checkRevokationStatus(issuerdn, serno);
                assertNotNull(revokestatus);
                assertTrue(revokestatus.getReason() == RevokedCertInfo.NOT_REVOKED);
                // Approve revocation and verify success
                approveRevocation(intAdmin, approvingAdmin, username, RevokedCertInfo.REVOCATION_REASON_CERTIFICATEHOLD,
                        ApprovalDataVO.APPROVALTYPE_REVOKECERTIFICATE, certificateStoreSession, approvalSession, approvalExecutionSession, caID);
                // Try to unrevoke certificate
                try {
                    ejbcaraws.revokeCert(issuerdn, serno, RevokedCertInfo.NOT_REVOKED);
                    assertTrue(ERRORNOTSENTFORAPPROVAL, false);
                } catch (WaitingForApprovalException_Exception e) {
                }
                try {
                    ejbcaraws.revokeCert(issuerdn, serno, RevokedCertInfo.NOT_REVOKED);
                    assertTrue(ERRORNOTSENTFORAPPROVAL, false);
                } catch (ApprovalException_Exception e) {
                }
                // Approve revocation and verify success
                approveRevocation(intAdmin, approvingAdmin, username, RevokedCertInfo.NOT_REVOKED, ApprovalDataVO.APPROVALTYPE_REVOKECERTIFICATE,
                        certificateStoreSession, approvalSession, approvalExecutionSession, caID);
                // Revoke user
                try {
                    ejbcaraws.revokeUser(username, RevokedCertInfo.REVOCATION_REASON_CERTIFICATEHOLD, false);
                    assertTrue(ERRORNOTSENTFORAPPROVAL, false);
                } catch (WaitingForApprovalException_Exception e) {
                }
                try {
                    ejbcaraws.revokeUser(username, RevokedCertInfo.REVOCATION_REASON_CERTIFICATEHOLD, false);
                    assertTrue(ERRORNOTSENTFORAPPROVAL, false);
                } catch (ApprovalException_Exception e) {
                }
                // Approve revocation and verify success
                approveRevocation(intAdmin, approvingAdmin, username, RevokedCertInfo.REVOCATION_REASON_CERTIFICATEHOLD,
                        ApprovalDataVO.APPROVALTYPE_REVOKEENDENTITY, certificateStoreSession, approvalSession, approvalExecutionSession, caID);
                // Try to reactivate user
                try {
                    ejbcaraws.revokeUser(username, RevokedCertInfo.NOT_REVOKED, false);
                    assertTrue(ERRORNOTSUPPORTEDSUCCEEDED, false);
                } catch (AlreadyRevokedException_Exception e) {
                }
            } finally {
                endEntityManagementSession.deleteUser(intAdmin, username);
            }
            try {
                // Create a hard token issued by this CA
                createHardToken(TOKENUSERNAME, caname, TOKENSERIALNUMBER);
                assertTrue(ejbcaraws.existsHardToken(TOKENSERIALNUMBER));
                // Revoke token
                try {
                    ejbcaraws.revokeToken(TOKENSERIALNUMBER, RevokedCertInfo.REVOCATION_REASON_CERTIFICATEHOLD);
                    assertTrue(ERRORNOTSENTFORAPPROVAL, false);
                } catch (WaitingForApprovalException_Exception e) {
                }
                try {
                    ejbcaraws.revokeToken(TOKENSERIALNUMBER, RevokedCertInfo.REVOCATION_REASON_CERTIFICATEHOLD);
                    assertTrue(ERRORNOTSENTFORAPPROVAL, false);
                } catch (ApprovalException_Exception e) {
                }
                // Approve actions and verify success
                approveRevocation(intAdmin, approvingAdmin, TOKENUSERNAME, RevokedCertInfo.REVOCATION_REASON_CERTIFICATEHOLD,
                        ApprovalDataVO.APPROVALTYPE_REVOKECERTIFICATE, certificateStoreSession, approvalSession, approvalExecutionSession, caID);
            } finally {
                hardTokenSessionRemote.removeHardToken(intAdmin, TOKENSERIALNUMBER);
            }
        } finally {
            // Nuke CA
            try {
                caAdminSessionRemote.revokeCA(intAdmin, caID, RevokedCertInfo.REVOCATION_REASON_UNSPECIFIED);
            } finally {
                caSession.removeCA(intAdmin, caID);
                CryptoTokenManagementSessionTest.removeCryptoToken(intAdmin, cryptoTokenId);
            }
        }
        log.trace("<test19RevocationApprovals");
    }

    @Test
    public void test20KeyRecoverNewest() throws Exception {
        keyRecover();
    }

    @Test
    public void test20bKeyRecoverAny() throws Exception {
        keyRecoverAny();
    }
    
    @Test
    public void test21GetAvailableCAs() throws Exception {
        getAvailableCAs();
    }

    @Test
    public void test22GetAuthorizedEndEntityProfiles() throws Exception {
        getAuthorizedEndEntityProfiles();
    }

    @Test
    public void test23GetAvailableCertificateProfiles() throws Exception {
        getAvailableCertificateProfiles();
    }

    @Test
    public void test24GetAvailableCAsInProfile() throws Exception {
        getAvailableCAsInProfile();
    }
    
    @Test
    public void test25CreateandGetCRL() throws Exception {
        createAndGetCRL();
    }
    
    @Test
    public void test27EjbcaVersion() throws Exception {
        ejbcaVersion();
    }

    @Test
    public void test29ErrorOnEditUser() throws Exception {
        errorOnEditUser();
    }

    @Test
    public void test30ErrorOnGeneratePkcs10() throws Exception {
        errorOnGeneratePkcs10();
    }

    @Test
    public void test31ErrorOnGeneratePkcs12() throws Exception {
        errorOnGeneratePkcs12();
    }

    @Test
    public void test32OperationOnNonexistingCA() throws Exception {
        operationOnNonexistingCA();
    }

    @Test
    public void test33CheckQueueLength() throws Exception {
        checkQueueLength();
    }

    /** In EJBCA 4.0.0 we changed the date format to ISO 8601. This verifies the that we still accept old requests, but returns UserDataVOWS objects using the new DateFormat 
     * @throws AuthorizationDeniedException */
    @Test
    public void test36EjbcaWsHelperTimeFormatConversion() throws CADoesntExistsException, ClassCastException, EjbcaException, AuthorizationDeniedException {
        log.trace(">test36EjbcaWsHelperTimeFormatConversion()");
        final Date nowWithOutSeconds = new Date((new Date().getTime()/60000)*60000);    // To avoid false negatives.. we will loose precision when we convert back and forth..
        final String oldTimeFormat = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT, Locale.US).format(nowWithOutSeconds);
        final String newTimeFormatStorage = FastDateFormat.getInstance("yyyy-MM-dd HH:mm", TimeZone.getTimeZone("UTC")).format(nowWithOutSeconds);
        final String newTimeFormatRequest = FastDateFormat.getInstance("yyyy-MM-dd HH:mm:ssZZ", TimeZone.getTimeZone("CEST")).format(nowWithOutSeconds);
        final String newTimeFormatResponse = FastDateFormat.getInstance("yyyy-MM-dd HH:mm:ssZZ", TimeZone.getTimeZone("UTC")).format(nowWithOutSeconds);
        final String relativeTimeFormat = "0123:12:31";
        log.debug("oldTimeFormat=" + oldTimeFormat);
        log.debug("newTimeFormatStorage=" + newTimeFormatStorage);
        log.debug("newTimeFormatRequest=" + newTimeFormatRequest);
        // Convert from UserDataVOWS with US Locale DateFormat to endEntityInformation
        final org.ejbca.core.protocol.ws.objects.UserDataVOWS userDataVoWs = new org.ejbca.core.protocol.ws.objects.UserDataVOWS("username", "password", false, "CN=User U", "CA1", null, null, 10, "P12", "EMPTY", "ENDUSER", null);
        userDataVoWs.setStartTime(oldTimeFormat);
        userDataVoWs.setEndTime(oldTimeFormat);
        final EndEntityInformation endEntityInformation1 = EjbcaWSHelper.convertUserDataVOWS(userDataVoWs, 1, 2, 3, 4, 5);
        assertEquals("CUSTOM_STARTTIME in old format was not correctly handled (VOWS to VO).", newTimeFormatStorage, endEntityInformation1.getExtendedinformation().getCustomData(ExtendedInformation.CUSTOM_STARTTIME));
        assertEquals("CUSTOM_ENDTIME in old format was not correctly handled (VOWS to VO).", newTimeFormatStorage, endEntityInformation1.getExtendedinformation().getCustomData(ExtendedInformation.CUSTOM_ENDTIME));
        // Convert from UserDataVOWS with standard DateFormat to endEntityInformation
        userDataVoWs.setStartTime(newTimeFormatRequest);
        userDataVoWs.setEndTime(newTimeFormatRequest);
        final EndEntityInformation endEntityInformation2 = EjbcaWSHelper.convertUserDataVOWS(userDataVoWs, 1, 2, 3, 4, 5);
        assertEquals("ExtendedInformation.CUSTOM_STARTTIME in new format was not correctly handled.", newTimeFormatStorage, endEntityInformation2.getExtendedinformation().getCustomData(ExtendedInformation.CUSTOM_STARTTIME));
        assertEquals("ExtendedInformation.CUSTOM_ENDTIME in new format was not correctly handled.", newTimeFormatStorage, endEntityInformation2.getExtendedinformation().getCustomData(ExtendedInformation.CUSTOM_ENDTIME));
        // Convert from UserDataVOWS with relative date format to endEntityInformation
        userDataVoWs.setStartTime(relativeTimeFormat);
        userDataVoWs.setEndTime(relativeTimeFormat);
        final EndEntityInformation endEntityInformation3 = EjbcaWSHelper.convertUserDataVOWS(userDataVoWs, 1, 2, 3, 4, 5);
        assertEquals("ExtendedInformation.CUSTOM_STARTTIME in relative format was not correctly handled.", relativeTimeFormat, endEntityInformation3.getExtendedinformation().getCustomData(ExtendedInformation.CUSTOM_STARTTIME));
        assertEquals("ExtendedInformation.CUSTOM_ENDTIME in relative format was not correctly handled.", relativeTimeFormat, endEntityInformation3.getExtendedinformation().getCustomData(ExtendedInformation.CUSTOM_ENDTIME));
        // Convert from endEntityInformation with standard DateFormat to UserDataVOWS
        final org.ejbca.core.protocol.ws.objects.UserDataVOWS userDataVoWs1 = EjbcaWSHelper.convertEndEntityInformation(endEntityInformation1, "CA1", "EEPROFILE", "CERTPROFILE", "HARDTOKENISSUER", "P12");
        // We expect that the server will respond using UTC
        assertEquals("CUSTOM_STARTTIME in new format was not correctly handled (VO to VOWS).", newTimeFormatResponse, userDataVoWs1.getStartTime());
        assertEquals("CUSTOM_ENDTIME in new format was not correctly handled (VO to VOWS).", newTimeFormatResponse, userDataVoWs1.getEndTime());
        // Convert from EndEntityInformation with relative date format to UserDataVOWS
        final org.ejbca.core.protocol.ws.objects.UserDataVOWS userDataVoWs3 = EjbcaWSHelper.convertEndEntityInformation(endEntityInformation3, "CA1", "EEPROFILE", "CERTPROFILE", "HARDTOKENISSUER", "P12");
        assertEquals("CUSTOM_STARTTIME in relative format was not correctly handled (VO to VOWS).", relativeTimeFormat, userDataVoWs3.getStartTime());
        assertEquals("CUSTOM_ENDTIME in relative format was not correctly handled (VO to VOWS).", relativeTimeFormat, userDataVoWs3.getEndTime());
        // Try some invalid start time date format
        userDataVoWs.setStartTime("12:32 2011-02-28");  // Invalid
        userDataVoWs.setEndTime("2011-02-28 12:32:00+00:00");   // Valid
        try {
            EjbcaWSHelper.convertUserDataVOWS(userDataVoWs, 1, 2, 3, 4, 5);
            fail("Conversion of illegal time format did not generate exception.");
        } catch (EjbcaException e) {
            assertEquals("Unexpected error code in exception.", ErrorCode.FIELD_VALUE_NOT_VALID, e.getErrorCode());
        }
        // Try some invalid end time date format
        userDataVoWs.setStartTime("2011-02-28 12:32:00+00:00"); // Valid
        userDataVoWs.setEndTime("12:32 2011-02-28");    // Invalid
        try {
            EjbcaWSHelper.convertUserDataVOWS(userDataVoWs, 1, 2, 3, 4, 5);
            fail("Conversion of illegal time format did not generate exception.");
        } catch (EjbcaException e) {
            assertEquals("Unexpected error code in exception.", ErrorCode.FIELD_VALUE_NOT_VALID, e.getErrorCode());
        }
        log.trace("<test36EjbcaWsHelperTimeFormatConversion()");
    }
    
    /**
     * Simulate a simple SQL injection by sending the illegal char "'".
     * 
     * @throws Exception
     */
    @Test
    public void test40EvilFind01() throws Exception {
        log.trace(">testEvilFind01()");
        UserMatch usermatch = new UserMatch();
        usermatch.setMatchwith(org.ejbca.util.query.UserMatch.MATCH_WITH_USERNAME);
        usermatch.setMatchtype(org.ejbca.util.query.UserMatch.MATCH_TYPE_EQUALS);
        usermatch.setMatchvalue("A' OR '1=1");
        try {
            List<UserDataVOWS> userdatas = ejbcaraws.findUser(usermatch);
            fail("SQL injection did not cause an error! " + userdatas.size());
        } catch (IllegalQueryException_Exception e) {
            // NOPMD, this should be thrown and we ignore it because we fail if
            // it is not thrown
        }
        log.trace("<testEvilFind01()");
    }

    /**
     * Use single transaction method for requesting KeyStore with special
     * characters in the certificate SubjectDN.
     */
    @Test
    public void test41CertificateRequestWithSpecialChars01() throws Exception {
        long rnd = secureRandom.nextLong();
        testCertificateRequestWithSpecialChars(
                "CN=test" + rnd + ", O=foo\\+bar\\\"\\,, C=SE",
                "CN=test" + rnd + ",O=foo\\+bar\\\"\\,,C=SE");
    }

    /**
     * Use single transaction method for requesting KeyStore with special
     * characters in the certificate SubjectDN.
     */
    @Test
    public void test42CertificateRequestWithSpecialChars02() throws Exception {
        long rnd = secureRandom.nextLong();
        testCertificateRequestWithSpecialChars(
                "CN=test" + rnd + ",O=foo/bar\\;123, C=SE",
                "CN=test" + rnd + ",O=foo/bar\\;123,C=SE");
    }

    /**
     * Use single transaction method for requesting KeyStore with special
     * characters in the certificate SubjectDN.
     */
    @Test
    public void test43CertificateRequestWithSpecialChars03() throws Exception {
        long rnd = secureRandom.nextLong();
        testCertificateRequestWithSpecialChars(
                "CN=test" + rnd + ", O=foo+bar\\+123, C=SE",
                "CN=test" + rnd + ",O=foo\\+bar\\+123,C=SE");
    }

    /**
     * Use single transaction method for requesting KeyStore with special
     * characters in the certificate SubjectDN.
     */
    @Test
    public void test44CertificateRequestWithSpecialChars04() throws Exception {
        long rnd = secureRandom.nextLong();
        testCertificateRequestWithSpecialChars(
                "CN=test" + rnd + ", O=foo\\=bar, C=SE",
                "CN=test" + rnd + ",O=foo\\=bar,C=SE");
    }

    /**
     * Use single transaction method for requesting KeyStore with special
     * characters in the certificate SubjectDN.
     */
    @Test
    public void test45CertificateRequestWithSpecialChars05() throws Exception {
        long rnd = secureRandom.nextLong();
        testCertificateRequestWithSpecialChars(
                "CN=test" + rnd + ", O=\"foo=bar, C=SE\"",
                "CN=test" + rnd + ",O=foo\\=bar\\, C\\=SE");
    }

    /**
     * Use single transaction method for requesting KeyStore with special
     * characters in the certificate SubjectDN.
     */
    @Test
    public void test46CertificateRequestWithSpecialChars06() throws Exception {
        long rnd = secureRandom.nextLong();
        testCertificateRequestWithSpecialChars(
                "CN=test" + rnd + ", O=\"foo+b\\+ar, C=SE\"",
                "CN=test" + rnd + ",O=foo\\+b\\+ar\\, C\\=SE");
    }

    /**
     * Use single transaction method for requesting KeyStore with special
     * characters in the certificate SubjectDN.
     */
    @Test
    public void test47CertificateRequestWithSpecialChars07() throws Exception {
        long rnd = secureRandom.nextLong();
        testCertificateRequestWithSpecialChars(
                "CN=test" + rnd + ", O=\\\"foo+b\\+ar\\, C=SE\\\"",
                "CN=test" + rnd + ",O=\\\"foo\\+b\\+ar\\, C\\=SE\\\"");
    }

    /**
     * Test that all but one default certificate forbidden characters are substituted
     * with '/'.
     * The one not tested is the null character ('\0'). It is not tested since
     * it is not a valid xml character so the WS protocol can not handle it.
     */
    @Test
    public void test48CertificateRequestWithForbiddenCharsDefault() throws Exception {
        long rnd = secureRandom.nextLong();
        cesecoreConfigurationProxySession.setConfigurationValue(forbiddenCharsKey, null);
        testCertificateRequestWithSpecialChars(
                "CN=test" + rnd + ",O=|\n|\r|;|A|!|`|?|$|~|, C=SE",
                "CN=test" + rnd +   ",O=|/|/|/|A|/|/|/|/|/|,C=SE");
    }

    /**
     * Same as {@link #test48CertificateRequestWithForbiddenCharsDefault()} but setting
     * default values in config.
     */
    @Test
    public void test49CertificateRequestWithForbiddenCharsDefinedAsDefault() throws Exception {
        long rnd = secureRandom.nextLong();
        cesecoreConfigurationProxySession.setConfigurationValue(forbiddenCharsKey, "\n\r;!\u0000%`?$~");
        testCertificateRequestWithSpecialChars(
                "CN=test" + rnd + ",O=|\n|\r|;|A|!|`|?|$|~|, C=SE",
                "CN=test" + rnd +   ",O=|/|/|/|A|/|/|/|/|/|,C=SE");
    }

    /**
     * Test to define some forbidden chars.
     */
    @Test
    public void test50CertificateRequestWithForbiddenCharsDefinedBogus() throws Exception {
        long rnd = secureRandom.nextLong();
        cesecoreConfigurationProxySession.setConfigurationValue(forbiddenCharsKey, "tset");
        try {
            testCertificateRequestWithSpecialChars(
                    "CN=test" + rnd +   ",O=|\n|\r|;|A|!|`|?|$|~|, C=SE",
                    "CN=////" + rnd + ",O=|\n|\r|\\;|A|!|`|?|$|~|,C=SE");
        } finally {
            // we must remove this bogus settings otherwise next setupAdmin() will fail
            cesecoreConfigurationProxySession.setConfigurationValue(forbiddenCharsKey, "");
        }
    }

    /**
     * Test that no forbidden chars work
     */
    @Test
    public void test51CertificateRequestWithNoForbiddenChars() throws Exception {
        long rnd = secureRandom.nextLong();
        cesecoreConfigurationProxySession.setConfigurationValue(forbiddenCharsKey, "");
        testCertificateRequestWithSpecialChars(
                "CN=test" + rnd +   ",O=|\n|\r|;|A|!|`|?|$|~|, C=SE",
                "CN=test" + rnd + ",O=|\n|\r|\\;|A|!|`|?|$|~|,C=SE");
    }


    /**
     * Tests that the provided cardnumber is stored in the EndEntityInformation 
     * and that when querying for EndEntityInformation the cardnumber is 
     * returned.
     * @throws Exception in case of error
     */
    @Test
    public void test48CertificateRequestWithCardNumber() throws Exception {
        String userName = "wsRequestCardNumber" + new SecureRandom().nextLong();
        
        // Generate a CSR
        KeyPair keys = KeyTools.genKeys("1024", AlgorithmConstants.KEYALGORITHM_RSA);
        PKCS10CertificationRequest pkcs10 = CertTools.genPKCS10CertificationRequest("SHA1WithRSA", CertTools.stringToBcX500Name("CN=NOUSED"),
                keys.getPublic(), new DERSet(), keys.getPrivate(), null);
        final String csr = new String(Base64.encode(pkcs10.toASN1Structure().getEncoded()));
        
        // Set some user data
        final UserDataVOWS userData = new UserDataVOWS();
        userData.setUsername(userName);
        userData.setPassword(PASSWORD);
        userData.setClearPwd(true);
        userData.setSubjectDN("CN=test" + secureRandom.nextLong() + ", UID=" + userName + ", O=Test, C=SE");
        userData.setCaName(getAdminCAName());
        userData.setEmail(null);
        userData.setSubjectAltName(null);
        userData.setStatus(UserDataVOWS.STATUS_NEW);
        userData.setTokenType(UserDataVOWS.TOKEN_TYPE_P12);
        userData.setEndEntityProfileName("EMPTY");
        userData.setCertificateProfileName("ENDUSER");

        // Set the card number
        userData.setCardNumber("1234fa");
        
        // Issue a certificate
        CertificateResponse response = ejbcaraws.certificateRequest(userData, csr, CertificateHelper.CERT_REQ_TYPE_PKCS10, null, CertificateHelper.RESPONSETYPE_CERTIFICATE);
        assertNotNull("null response", response);
        
        // Check that the cardnumber was stored in the EndEntityInformation
        EndEntityInformation endEntity = endEntityAccessSession.findUser(intAdmin, userName);
        assertEquals("stored cardnumber ejb", "1234fa", endEntity.getCardNumber());
        
        // Check that the cardnumber is also available when querying using WS
        UserMatch criteria = new UserMatch();
        criteria.setMatchtype(UserMatch.MATCH_TYPE_EQUALS);
        criteria.setMatchwith(UserMatch.MATCH_WITH_USERNAME);
        criteria.setMatchvalue(userName);
        UserDataVOWS user = ejbcaraws.findUser(criteria).get(0);
        assertEquals("stored cardnumber ws", "1234fa", user.getCardNumber());
    }

    private void testCertificateRequestWithSpecialChars(String requestedSubjectDN, String expectedSubjectDN) throws Exception {
        String userName = "wsSpecialChars" + secureRandom.nextLong();
        final UserDataVOWS userData = new UserDataVOWS();
        userData.setUsername(userName);
        userData.setPassword(PASSWORD);
        userData.setClearPwd(true);
        userData.setSubjectDN(requestedSubjectDN);
        userData.setCaName(getAdminCAName());
        userData.setEmail(null);
        userData.setSubjectAltName(null);
        userData.setStatus(UserDataVOWS.STATUS_NEW);
        userData.setTokenType(UserDataVOWS.TOKEN_TYPE_P12);
        userData.setEndEntityProfileName("EMPTY");
        userData.setCertificateProfileName("ENDUSER");

        KeyStore ksenv = ejbcaraws.softTokenRequest(userData, null, "1024", AlgorithmConstants.KEYALGORITHM_RSA);
        java.security.KeyStore keyStore = KeyStoreHelper.getKeyStore(ksenv.getKeystoreData(), "PKCS12", PASSWORD);
        assertNotNull(keyStore);
        Enumeration<String> en = keyStore.aliases();
        String alias = en.nextElement();
        if(!keyStore.isKeyEntry(alias)) {
            alias = en.nextElement();
        }
        X509Certificate cert = (X509Certificate) keyStore.getCertificate(alias);
        
        String resultingSubjectDN = cert.getSubjectDN().toString();
        assertEquals(requestedSubjectDN + " was transformed into " + resultingSubjectDN + " (not the expected " + expectedSubjectDN + ")", expectedSubjectDN,
                resultingSubjectDN);
    }

    /**
     * Creates a "hardtoken" with certficates.
     */
    private void createHardToken(String username, String caName, String serialNumber) throws Exception {
        GlobalConfiguration gc = (GlobalConfiguration) raAdminSession.getCachedConfiguration(Configuration.GlobalConfigID);
        boolean originalProfileSetting = gc.getEnableEndEntityProfileLimitations();
        gc.setEnableEndEntityProfileLimitations(false);
        raAdminSession.saveConfiguration(intAdmin, gc, Configuration.GlobalConfigID);
        if (certificateProfileSession.getCertificateProfileId(WS_TEST_CERTIFICATE_PROFILE_NAME) != 0) {
            certificateProfileSession.removeCertificateProfile(intAdmin, WS_TEST_CERTIFICATE_PROFILE_NAME);
        }
        CertificateProfile profile = new CertificateProfile(CertificateProfileConstants.CERTPROFILE_FIXED_ENDUSER);
        profile.setAllowValidityOverride(true);
        certificateProfileSession.addCertificateProfile(intAdmin, WS_TEST_CERTIFICATE_PROFILE_NAME, profile);
        UserDataVOWS tokenUser1 = new UserDataVOWS();
        tokenUser1.setUsername(username);
        tokenUser1.setPassword(PASSWORD);
        tokenUser1.setClearPwd(true);
        tokenUser1.setSubjectDN("CN=" + username);
        tokenUser1.setCaName(caName);
        tokenUser1.setEmail(null);
        tokenUser1.setSubjectAltName(null);
        tokenUser1.setStatus(UserDataVOWS.STATUS_NEW);
        tokenUser1.setTokenType(UserDataVOWS.TOKEN_TYPE_USERGENERATED);
        tokenUser1.setEndEntityProfileName("EMPTY");
        tokenUser1.setCertificateProfileName("ENDUSER");
        KeyPair basickeys = KeyTools.genKeys("1024", AlgorithmConstants.KEYALGORITHM_RSA);
        PKCS10CertificationRequest basicpkcs10 = CertTools.genPKCS10CertificationRequest("SHA1WithRSA", CertTools.stringToBcX500Name("CN=NOTUSED"), basickeys
                .getPublic(), new DERSet(), basickeys.getPrivate(), null);
        ArrayList<TokenCertificateRequestWS> requests = new ArrayList<TokenCertificateRequestWS>();
        TokenCertificateRequestWS tokenCertReqWS = new TokenCertificateRequestWS();
        tokenCertReqWS.setCAName(caName);
        tokenCertReqWS.setCertificateProfileName(WS_TEST_CERTIFICATE_PROFILE_NAME);
        tokenCertReqWS.setValidityIdDays("1");
        tokenCertReqWS.setPkcs10Data(basicpkcs10.getEncoded());
        tokenCertReqWS.setType(HardTokenConstants.REQUESTTYPE_PKCS10_REQUEST);
        requests.add(tokenCertReqWS);
        tokenCertReqWS = new TokenCertificateRequestWS();
        tokenCertReqWS.setCAName(caName);
        tokenCertReqWS.setCertificateProfileName("ENDUSER");
        tokenCertReqWS.setKeyalg("RSA");
        tokenCertReqWS.setKeyspec("1024");
        tokenCertReqWS.setType(HardTokenConstants.REQUESTTYPE_KEYSTORE_REQUEST);
        requests.add(tokenCertReqWS);
        HardTokenDataWS hardTokenDataWS = new HardTokenDataWS();
        hardTokenDataWS.setLabel(HardTokenConstants.LABEL_PROJECTCARD);
        hardTokenDataWS.setTokenType(HardTokenConstants.TOKENTYPE_SWEDISHEID);
        hardTokenDataWS.setHardTokenSN(serialNumber);
        PinDataWS basicPinDataWS = new PinDataWS();
        basicPinDataWS.setType(HardTokenConstants.PINTYPE_BASIC);
        basicPinDataWS.setInitialPIN("1234");
        basicPinDataWS.setPUK("12345678");
        PinDataWS signaturePinDataWS = new PinDataWS();
        signaturePinDataWS.setType(HardTokenConstants.PINTYPE_SIGNATURE);
        signaturePinDataWS.setInitialPIN("5678");
        signaturePinDataWS.setPUK("23456789");
        hardTokenDataWS.getPinDatas().add(basicPinDataWS);
        hardTokenDataWS.getPinDatas().add(signaturePinDataWS);
        List<TokenCertificateResponseWS> responses = ejbcaraws.genTokenCertificates(tokenUser1, requests, hardTokenDataWS, true, false);
        assertTrue(responses.size() == 2);
        certificateProfileSession.removeCertificateProfile(intAdmin, WS_TEST_CERTIFICATE_PROFILE_NAME);
        gc.setEnableEndEntityProfileLimitations(originalProfileSetting);
        raAdminSession.saveConfiguration(intAdmin, gc, Configuration.GlobalConfigID);
    } // createHardToken

    /**
     * Create a user a generate cert.
     */
    private X509Certificate createUserAndCert(String username, int caID) throws Exception {
        EndEntityInformation userdata = new EndEntityInformation(username, "CN=" + username, caID, null, null, new EndEntityType(EndEntityTypes.ENDUSER), SecConst.EMPTY_ENDENTITYPROFILE,
                CertificateProfileConstants.CERTPROFILE_FIXED_ENDUSER, SecConst.TOKEN_SOFT_P12, 0, null);
        userdata.setPassword(PASSWORD);
        endEntityManagementSession.addUser(intAdmin, userdata, true);
        BatchMakeP12 makep12 = new BatchMakeP12();
        File tmpfile = File.createTempFile("ejbca", "p12");
        makep12.setMainStoreDir(tmpfile.getParent());
        makep12.createAllNew(cliUserName, cliPassword);
        Collection<Certificate> userCerts = certificateStoreSession.findCertificatesByUsername(username);
        assertTrue(userCerts.size() == 1);
        return (X509Certificate) userCerts.iterator().next();
    }

}
