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
package org.cesecore.keys.token;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.File;
import java.security.InvalidKeyException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import org.apache.log4j.Logger;
import org.cesecore.RoleUsingTestCase;
import org.cesecore.authentication.tokens.AuthenticationToken;
import org.cesecore.authentication.tokens.UsernamePrincipal;
import org.cesecore.authorization.AuthorizationDeniedException;
import org.cesecore.authorization.control.CryptoTokenRules;
import org.cesecore.authorization.rules.AccessRuleData;
import org.cesecore.authorization.rules.AccessRuleState;
import org.cesecore.certificates.ca.catoken.CAToken;
import org.cesecore.keys.token.p11.Pkcs11SlotLabelType;
import org.cesecore.mock.authentication.tokens.TestAlwaysAllowLocalAuthenticationToken;
import org.cesecore.roles.RoleData;
import org.cesecore.roles.access.RoleAccessSessionRemote;
import org.cesecore.roles.management.RoleManagementSessionRemote;
import org.cesecore.util.CryptoProviderTools;
import org.cesecore.util.EjbRemoteHelper;
import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

/**
 * Tests CryptoToken management API.
 * 
 * @version $Id: CryptoTokenManagementSessionTest.java 18208 2013-11-27 10:04:42Z anatom $
 */
public class CryptoTokenManagementSessionTest extends RoleUsingTestCase {

    private static final CryptoTokenManagementSessionRemote cryptoTokenManagementSession = EjbRemoteHelper.INSTANCE.getRemoteSession(CryptoTokenManagementSessionRemote.class);
    private static final CryptoTokenManagementProxySessionRemote cryptoTokenManagementProxySession = EjbRemoteHelper.INSTANCE.getRemoteSession(CryptoTokenManagementProxySessionRemote.class, EjbRemoteHelper.MODULE_TEST);
    private static final RoleAccessSessionRemote roleAccessSession = EjbRemoteHelper.INSTANCE.getRemoteSession(RoleAccessSessionRemote.class);
    private static final RoleManagementSessionRemote roleManagementSession = EjbRemoteHelper.INSTANCE.getRemoteSession(RoleManagementSessionRemote.class);

    private static final AuthenticationToken alwaysAllowToken = new TestAlwaysAllowLocalAuthenticationToken(new UsernamePrincipal(CryptoTokenManagementSessionTest.class.getSimpleName()));

    private static final Logger log = Logger.getLogger(CryptoTokenManagementSessionTest.class);
    
    @BeforeClass
    public static void setUpProviderAndCreateCA() throws Exception {
        CryptoProviderTools.installBCProvider();
    }

    @Before
    public void setUp() throws Exception {
        // Set up base role that can edit roles
        super.setUpAuthTokenAndRole(this.getClass().getSimpleName());
        // Now we have a role that can edit roles, we can edit this role to include more privileges
        final RoleData role = roleAccessSession.findRole(this.getClass().getSimpleName());
        // Add rules to the role
        final List<AccessRuleData> accessRules = new ArrayList<AccessRuleData>();
        accessRules.add(new AccessRuleData(role.getRoleName(), CryptoTokenRules.BASE.resource(), AccessRuleState.RULE_ACCEPT, true));
        roleManagementSession.addAccessRulesToRole(alwaysAllowToken, role, accessRules);
    }

    @After
    public void tearDown() throws Exception {
        super.tearDownRemoveRole();
    }

    @Test
    public void basicCryptoTokenForCAWithImpliedRSA() throws Exception {
        int cryptoTokenId = 0;
        try {
            cryptoTokenId = createCryptoTokenForCA(roleMgmgToken, "testCaRsa", "1024");
            subTest(cryptoTokenId, "1024");
        } finally {
            removeCryptoToken(roleMgmgToken, cryptoTokenId);
        }
    }
    
    @Test
    public void basicCryptoTokenForCAWithExplicitRSA() throws Exception {
        int cryptoTokenId = 0;
        try {
            cryptoTokenId = createCryptoTokenForCA(roleMgmgToken, "testCaRsa", "RSA1024");
            subTest(cryptoTokenId, "RSA1024");
        } finally {
            removeCryptoToken(roleMgmgToken, cryptoTokenId);
        }
    }

    @Test
    public void basicCryptoTokenForCAWithDSA() throws Exception {
        int cryptoTokenId = 0;
        try {
            cryptoTokenId = createCryptoTokenForCA(roleMgmgToken, "testCaDsa", "DSA1024");
            subTest(cryptoTokenId, "DSA1024");
        } finally {
            removeCryptoToken(roleMgmgToken, cryptoTokenId);
        }
    }

    @Test
    public void basicCryptoTokenForCAWithECDSA() throws Exception {
        int cryptoTokenId = 0;
        try {
            cryptoTokenId = createCryptoTokenForCA(roleMgmgToken, "testCaEcdsa", "secp256r1");
            subTest(cryptoTokenId, "secp256r1");
        } finally {
            removeCryptoToken(roleMgmgToken, cryptoTokenId);
        }
    }

    private void subTest(final int cryptoTokenId, final String keySpec) throws Exception {
        // Test additional key creation and informatin retrieval
        final String KEYALIAS1 = "newAlias1";
        final String KEYALIAS2 = "newAlias2";
        final String KEYALIAS_BAD = "notAnAlias";
        cryptoTokenManagementSession.createKeyPair(roleMgmgToken, cryptoTokenId, KEYALIAS1, keySpec);
        try {
            cryptoTokenManagementSession.createKeyPair(roleMgmgToken, cryptoTokenId, KEYALIAS1, keySpec);
            fail("Should not be able to generate a key pair with the same alias twice.");
        } catch (InvalidKeyException e) {
            // Expected
        }
        try {
            cryptoTokenManagementSession.createKeyPairWithSameKeySpec(roleMgmgToken, cryptoTokenId, KEYALIAS1, KEYALIAS1);
            fail("Should not be able to generate a key pair with the same alias twice.");
        } catch (InvalidKeyException e) {
            // Expected
        }
        cryptoTokenManagementSession.createKeyPairWithSameKeySpec(roleMgmgToken, cryptoTokenId, KEYALIAS1, KEYALIAS2);
        assertNull("Non-existing key alias should not return info.", cryptoTokenManagementSession.getKeyPairInfo(roleMgmgToken, cryptoTokenId, KEYALIAS_BAD));
        final KeyPairInfo keyPairInfo1 = cryptoTokenManagementSession.getKeyPairInfo(roleMgmgToken, cryptoTokenId, KEYALIAS1);
        assertEquals("Got wrong info for the requested alias.", KEYALIAS1, keyPairInfo1.getAlias());
        final KeyPairInfo keyPairInfo2 = cryptoTokenManagementSession.getKeyPairInfo(roleMgmgToken, cryptoTokenId, KEYALIAS2);
        assertEquals("Got wrong info for the requested alias.", KEYALIAS2, keyPairInfo2.getAlias());
        assertEquals("Key spec re-use failed.", keyPairInfo1.getKeyAlgorithm(), keyPairInfo2.getKeyAlgorithm());
        assertEquals("Key spec re-use failed.", keyPairInfo1.getKeySpecification(), keyPairInfo2.getKeySpecification());
        // Test key listing
        final List<KeyPairInfo> keyPairInfos = cryptoTokenManagementSession.getKeyPairInfos(roleMgmgToken, cryptoTokenId);
        final List<String> aliases = cryptoTokenManagementSession.getKeyPairAliases(roleMgmgToken, cryptoTokenId);
        assertEquals("Number of aliases and returned key pair informations should be the same.", keyPairInfos.size(), aliases.size());
        for (final KeyPairInfo keyPairInfo : keyPairInfos) {
            assertTrue("List of aliases was missing " + keyPairInfo.getAlias(), aliases.contains(keyPairInfo.getAlias()));
        }
        // Test key test
        cryptoTokenManagementSession.testKeyPair(roleMgmgToken, cryptoTokenId, KEYALIAS1);
        cryptoTokenManagementSession.testKeyPair(roleMgmgToken, cryptoTokenId, KEYALIAS2);
        try {
            cryptoTokenManagementSession.testKeyPair(roleMgmgToken, cryptoTokenId, KEYALIAS_BAD);
            fail("Key test should throw for non-existing key.");
        } catch (CryptoTokenOfflineException e) {
            // Expected
        }
        // Test key removal
        cryptoTokenManagementSession.removeKeyPair(roleMgmgToken, cryptoTokenId, KEYALIAS2);
        assertNull("Non-existing key alias should not return info.", cryptoTokenManagementSession.getKeyPairInfo(roleMgmgToken, cryptoTokenId, KEYALIAS2));
        try {
            cryptoTokenManagementSession.removeKeyPair(roleMgmgToken, cryptoTokenId, KEYALIAS2);
            fail("Key removal should throw for non-existing key.");
        } catch (InvalidKeyException e) {
            // Expected
        }
        // Verify auto-activation behavior
        assertTrue("Expected CryptoToken to be active.", cryptoTokenManagementSession.isCryptoTokenStatusActive(roleMgmgToken, cryptoTokenId));
        cryptoTokenManagementSession.deactivate(roleMgmgToken, cryptoTokenId);
        assertTrue("Expected auto-activated CryptoToken to still be active.", cryptoTokenManagementSession.isCryptoTokenStatusActive(roleMgmgToken, cryptoTokenId));
        cryptoTokenManagementSession.activate(roleMgmgToken, cryptoTokenId, "badCode".toCharArray());
        assertTrue("Expected auto-activated CryptoToken to still be active.", cryptoTokenManagementSession.isCryptoTokenStatusActive(roleMgmgToken, cryptoTokenId));
    }

    @Test
    public void testIllegalCAKeyLengthRsa() throws Exception {
        try {
            createCryptoTokenForCA(roleMgmgToken, "testIllegalCAKeyLengthRsa", "512");
            fail("Shouldn't be able to generate CA keystore keys with 512 bit RSA");
        } catch (RuntimeException e) {
            assertEquals(InvalidKeyException.class.getName(), e.getCause().getClass().getName());
        }
    }

    @Test
    public void testIllegalCAKeyLengthDsa() throws Exception {
        try {
            createCryptoTokenForCA(roleMgmgToken, "testIllegalCAKeyLengthDsa", "DSA512");
            fail("Shouldn't be able to generate CA keystore keys with 512 bit DSA");
        } catch (RuntimeException e) {
            assertEquals(InvalidKeyException.class.getName(), e.getCause().getClass().getName());
        }
    }

    @Test
    public void testIllegalCAKeyLengthEcdsa() throws Exception {
        try {
            createCryptoTokenForCA(roleMgmgToken, "testIllegalCAKeyLengthEcdsa", "prime192v1");
            fail("Shouldn't be able to generate CA keystore keys with 'prime192v1' ECDSA");
        } catch (RuntimeException e) {
            log.debug("", e);
            assertEquals(InvalidKeyException.class.getName(), e.getCause().getClass().getName());
        }
    }

    @Test
    public void modifyPin() throws Exception {
        final boolean UPDATE_ONLY = true;
        final boolean SET_ALWAYS = false;
        final char[] PIN_ORG = "foo1234".toCharArray();
        final char[] PIN_SECOND = "foo123".toCharArray();
        final char[] PIN_WRONG = "wrong".toCharArray();
        int cryptoTokenId = 0;
        try {
            cryptoTokenId = createCryptoTokenForCA(roleMgmgToken, "modifyPin", "RSA1024");
            // Note the secondary testing of pin changes is the calls below
            final boolean usesAutoActivation1 = cryptoTokenManagementSession.updatePin(alwaysAllowToken, cryptoTokenId, PIN_ORG, PIN_SECOND, UPDATE_ONLY);
            assertTrue("Updating soft keystore pin should not remove auto activation", usesAutoActivation1);
            final boolean usesAutoActivation2 = cryptoTokenManagementSession.updatePin(alwaysAllowToken, cryptoTokenId, PIN_SECOND, PIN_ORG, SET_ALWAYS);
            assertTrue("Updating soft keystore pin should not remove auto activation", usesAutoActivation2);
            final boolean usesAutoActivation3 = cryptoTokenManagementSession.updatePin(alwaysAllowToken, cryptoTokenId, PIN_ORG, null, SET_ALWAYS);
            assertFalse("Auto activation should not be in use after removing 'auto activation pin'", usesAutoActivation3);
            final boolean usesAutoActivation4 = cryptoTokenManagementSession.updatePin(alwaysAllowToken, cryptoTokenId, PIN_ORG, PIN_SECOND, UPDATE_ONLY);
            assertFalse("Auto activation should not be in use when it was not before and we only update when present.", usesAutoActivation4);
            final boolean usesAutoActivation5 = cryptoTokenManagementSession.updatePin(alwaysAllowToken, cryptoTokenId, PIN_ORG, PIN_ORG, SET_ALWAYS);
            assertTrue("Auto activation should be in use when it was not before and we force it.", usesAutoActivation5);
            try {
                cryptoTokenManagementSession.updatePin(alwaysAllowToken, cryptoTokenId, PIN_WRONG, PIN_SECOND, UPDATE_ONLY);
                fail("It should not be possible to update a keystore pin using the wrong current pin.");
            } catch (CryptoTokenAuthenticationFailedException e) {
                // Expected
            }
            try {
                cryptoTokenManagementSession.updatePin(alwaysAllowToken, cryptoTokenId, PIN_WRONG, PIN_SECOND, SET_ALWAYS);
                fail("It should not be possible to update a keystore pin using the wrong current pin.");
            } catch (CryptoTokenAuthenticationFailedException e) {
                // Expected
            }
            try {
                cryptoTokenManagementSession.updatePin(alwaysAllowToken, cryptoTokenId, PIN_WRONG, null, UPDATE_ONLY);
                fail("It should not be possible to remove the auto-activation pin using the wrong current pin.");
            } catch (CryptoTokenAuthenticationFailedException e) {
                // Expected
            }
            try {
                cryptoTokenManagementSession.updatePin(alwaysAllowToken, cryptoTokenId, PIN_WRONG, null, SET_ALWAYS);
                fail("It should not be possible to remove the auto-activation pin using the wrong current pin.");
            } catch (CryptoTokenAuthenticationFailedException e) {
                // Expected
            }
            try {
                cryptoTokenManagementSession.updatePin(alwaysAllowToken, cryptoTokenId, null, PIN_SECOND, UPDATE_ONLY);
                fail("It should not be possible to update a keystore pin without the current pin.");
            } catch (CryptoTokenAuthenticationFailedException e) {
                // Expected
            }
            try {
                cryptoTokenManagementSession.updatePin(alwaysAllowToken, cryptoTokenId, null, PIN_SECOND, SET_ALWAYS);
                fail("It should not be possible to update a keystore pin without the current pin.");
            } catch (CryptoTokenAuthenticationFailedException e) {
                // Expected
            }
        } finally {
            removeCryptoToken(roleMgmgToken, cryptoTokenId);
        }
    }

    /** Strange way of handling tokens.. exporting the whole thing so we can do sign stuff client JVM JUnit tests! Try to avoid this. */
    @Deprecated
    public static CryptoToken getCryptoTokenFromServer(int cryptoTokenId, char[] tokenpin) throws CryptoTokenOfflineException, CryptoTokenAuthenticationFailedException {
        final CryptoToken cryptoToken = cryptoTokenManagementProxySession.getCryptoToken(cryptoTokenId);
        // Since we are now operating on the token in a different JVM it will not be active anymore unless it is autoactivated..
        cryptoToken.activate(tokenpin);
        return cryptoToken;
    }
    
    /** Create CryptoToken and generate CA's keys */
    public static int createCryptoTokenForCA(AuthenticationToken authenticationToken, String tokenName, String signKeySpec) {
        return createCryptoTokenForCA(authenticationToken, null, true, false, tokenName, signKeySpec);
    }

    public static int createCryptoTokenForCA(AuthenticationToken authenticationToken, char[] pin, boolean genenrateKeys, boolean pkcs11, String tokenName, String signKeySpec) {
        return createCryptoTokenForCAInternal(authenticationToken, pin, genenrateKeys, pkcs11, tokenName, signKeySpec);
    }

    private static int createCryptoTokenForCAInternal(AuthenticationToken authenticationToken, char[] pin, boolean genenrateKeys, boolean pkcs11, String tokenName, String signKeySpec) {
        if (authenticationToken == null) {
            authenticationToken = alwaysAllowToken;
        }
        
        // Generate full name of cryptotoken including class/method name etc.
        final String callingClassName = Thread.currentThread().getStackTrace()[4].getClassName();
        final String callingClassSimpleName = callingClassName.substring(callingClassName.lastIndexOf('.')+1);
        final String callingMethodName = Thread.currentThread().getStackTrace()[4].getMethodName();
        final String fullTokenName = callingClassSimpleName + "." + callingMethodName + "."+ tokenName;
        
        // Delete cryptotokens with the same name
        while (true) {
            final Integer oldCryptoTokenId = cryptoTokenManagementSession.getIdFromName(fullTokenName);
            if (oldCryptoTokenId == null) break;
            removeCryptoToken(authenticationToken, oldCryptoTokenId);
        }
        
        // Set up properties
        final Properties cryptoTokenProperties = new Properties();
        cryptoTokenProperties.setProperty(SoftCryptoToken.NODEFAULTPWD, "true");
        if (pin==null) {
            cryptoTokenProperties.setProperty(CryptoToken.AUTOACTIVATE_PIN_PROPERTY, "foo1234");
        }
        String cryptoTokenClassName = SoftCryptoToken.class.getName();
        if (pkcs11) {
            cryptoTokenProperties.setProperty(PKCS11CryptoToken.SHLIB_LABEL_KEY, getHSMLibrary());
            cryptoTokenProperties.setProperty(PKCS11CryptoToken.SLOT_LABEL_VALUE, "1");
            cryptoTokenProperties.setProperty(PKCS11CryptoToken.SLOT_LABEL_TYPE, Pkcs11SlotLabelType.SLOT_NUMBER.getKey());
            cryptoTokenClassName = PKCS11CryptoToken.class.getName();
        }
        
        // Create the cryptotoken
        int cryptoTokenId = 0;
        try {
            cryptoTokenId = cryptoTokenManagementSession.createCryptoToken(authenticationToken, fullTokenName, cryptoTokenClassName, cryptoTokenProperties, null, pin);
            if (genenrateKeys) {
                cryptoTokenManagementSession.createKeyPair(authenticationToken, cryptoTokenId, CAToken.SOFTPRIVATESIGNKEYALIAS, signKeySpec);
                cryptoTokenManagementSession.createKeyPair(authenticationToken, cryptoTokenId, CAToken.SOFTPRIVATEDECKEYALIAS, "1024");
            }
        } catch (Exception e) {
            // Cleanup token if we failed during the key creation stage
            try {
                removeCryptoToken(null, cryptoTokenId);
            } catch (Exception e2) {
                log.error("", e2);
            }
            throw new RuntimeException(e);
        }
        return cryptoTokenId;
    }

    /** @return HSM detected library location */
    private static String getHSMLibrary() {
        final File utimacoCSLinux = new File("/etc/utimaco/libcs2_pkcs11.so");
        final File utimacoCSWindows = new File("C:/Program Files/Utimaco/SafeGuard CryptoServer/Lib/cs2_pkcs11.dll");
        final File lunaSALinux64 = new File("/usr/lunasa/lib/libCryptoki2_64.so");
        final File protectServerLinux64 = new File("/opt/ETcpsdk/lib/linux-x86_64/libcryptoki.so");
        String ret = null;
        if (utimacoCSLinux.exists()) {
            ret = utimacoCSLinux.getAbsolutePath();
        } else if (utimacoCSWindows.exists()) {
            ret = utimacoCSWindows.getAbsolutePath();
        } else if (lunaSALinux64.exists()) {
            ret = lunaSALinux64.getAbsolutePath();
        } else if (protectServerLinux64.exists()) {
            ret = protectServerLinux64.getAbsolutePath();
        } else {
            fail("No supported HSM libarary found.");
        }
        return ret;
    }

    /** Remove the cryptoToken, if the crypto token with the given ID does not exist, nothing happens */
    public static void removeCryptoToken(AuthenticationToken authenticationToken, final int cryptoTokenId) {
        if (authenticationToken == null) {
            authenticationToken = alwaysAllowToken;
        }
        try {
            cryptoTokenManagementSession.deleteCryptoToken(authenticationToken, cryptoTokenId);
        } catch (AuthorizationDeniedException e) {
            throw new RuntimeException(e);  // Expect that calling method knows what it's doing
        }
    }    
}
