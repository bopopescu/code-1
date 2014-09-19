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
package org.cesecore.certificates.ca.internal;

import static org.junit.Assert.assertTrue;

import java.math.BigInteger;
import java.util.HashMap;

import org.junit.Test;


/**
 * Tests generation of serial numbers.
 *
 * @version $Id: SernoGeneratorTest.java 16152 2013-01-20 15:44:17Z anatom $
 */
public class SernoGeneratorTest {
//    private static final Logger log = Logger.getLogger(SernoGeneratorTest.class);

    @Test
    public void test01GenerateSernos8Octets() throws Exception {
        SernoGenerator gen = SernoGeneratorRandom.instance();
        HashMap<String, String> map = new HashMap<String, String>(500000);
        String hex = null;

        for (int j = 0; j < 500; j++) {
            for (int i = 1; i < 1001; i++) {
                BigInteger bi = gen.getSerno();

                //hex = Hex.encode(serno);
                hex = bi.toString(16);

                if (map.put(hex, hex) != null) {
//                    log.warn("Duplicate serno produced: " + hex);
//                    log.warn("Number of sernos produced before duplicate: "+(j*1000+i));
                    assertTrue("Duplicate serno produced after "+(j*1000+i)+" sernos.", false);
                }
            }

            //log.debug(((j + 1) * 1000) + " sernos produced: " + hex);

            //long seed = Math.abs((new Date().getTime()) + this.hashCode());
            //gen.setSeed(seed);
            //log.debug("Reseeding: " + seed);
        }

//        log.info("Map now contains " + map.size() + " serial numbers. Last one: "+hex);
//        log.info("Number of duplicates: "+duplicates);
    }
    
    /** Using only 32 bit serial numbers will produce collisions 
     * about 1-5 times for 100.000 serial numbers
     */
    @Test
    public void test02GenerateSernos4Octets() throws Exception {
        SernoGenerator gen = SernoGeneratorRandom.instance();
        gen.setSernoOctetSize(4);
        gen.setAlgorithm("SHA1PRNG");
        HashMap<String, String> map = new HashMap<String, String>(100000);
        String hex = null;

        int duplicates = 0;
        for (int j = 0; j < 100; j++) {
            for (int i = 1; i < 1001; i++) {
                BigInteger bi = gen.getSerno();

                //hex = Hex.encode(serno);
                hex = bi.toString(16);

                if (map.put(hex, hex) != null) {
                	duplicates++;
//                    log.warn("Duplicate serno produced: " + hex);
//                    log.warn("Number of sernos produced before duplicate: "+(j*1000+i));
                    if (duplicates > 10) {
                        assertTrue("More then 10 duplicates produced, "+duplicates, false);                    	
                    }
                }
            }

        }

//        log.info("Map now contains " + map.size() + " serial numbers. Last one: "+hex);
//        log.info("Number of duplicates: "+duplicates);
    }

}
