package com.ea.utils;

import org.junit.jupiter.api.Test;

import static com.ea.dirtysdk.CryptSSC2.cryptSSC2StringDecrypt;
import static com.ea.dirtysdk.CryptSSC2.cryptSSC2StringEncrypt;

class CryptSSC2Test {
    @Test
    void cryptSSC2EncodeDecodeTest() {
        byte[] skey = { 0x51, (byte)0xba, (byte)0x8a, (byte)0xee, 0x64, (byte)0xdd, (byte)0xfa, (byte)0xca, (byte)0xe5, (byte)0xba, (byte)0xef, (byte)0xa6, (byte)0xbf, 0x61, (byte)0xe0, 0x09 };
        byte[] buf = new byte[32];
        String str = "pass";
        System.arraycopy(str.getBytes(), 0, buf, 0, str.length());

        System.out.println("encoding string: " + new String(buf).trim());

        cryptSSC2StringEncrypt(buf, buf.length, buf, skey, skey.length, skey.length);
        System.out.print("encoded bytes: ");
        for (int x = 0; x < buf.length; ++x)
            System.out.printf("%02x ", buf[x]);
        System.out.println();

        cryptSSC2StringDecrypt(buf, buf.length, buf, skey, skey.length, skey.length);
        System.out.print("decoded bytes: ");
        for (int x = 0; x < buf.length; ++x)
            System.out.printf("%02x ", buf[x]);
        System.out.println();
        String decoded = new String(buf);
        int nullPos = decoded.indexOf('\0');
        if (nullPos != -1) {
            decoded = decoded.substring(0, nullPos);
        }
        System.out.println("decoded string: " + decoded);
    }


    @Test
    void cryptSSC2DecodeTest() {
        byte[] skey = { 0x51, (byte)0xba, (byte)0x8a, (byte)0xee, 0x64, (byte)0xdd, (byte)0xfa, (byte)0xca, (byte)0xe5, (byte)0xba, (byte)0xef, (byte)0xa6, (byte)0xbf, 0x61, (byte)0xe0, 0x09 };
        byte[] buf = { 0x4b, 0x62, 0x78, 0x26, 0x34, 0x66, 0x7c, 0x78, 0x71, 0x3e, 0x61, 0x5b, 0x7a, 0x4c, 0x7a, 0x30, 0x5a, 0x7f, 0x25, 0x52, 0x73, 0x39, 0x5b, 0x48, 0x65, 0x40, 0x79, 0x64, 0x79, 0x56, 0x62, 0x00 };

        cryptSSC2StringDecrypt(buf, buf.length, buf, skey, skey.length, skey.length);
        System.out.print("decoded bytes: ");
        for (int x = 0; x < buf.length; ++x)
            System.out.printf("%02x ", buf[x]);
        System.out.println();
        String decoded = new String(buf);
        int nullPos = decoded.indexOf('\0');
        if (nullPos != -1) {
            decoded = decoded.substring(0, nullPos);
        }
        System.out.println("decoded string: " + decoded);
    }


}
