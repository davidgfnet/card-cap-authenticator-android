package com.example.bankcap.utils;

import java.io.ByteArrayOutputStream;
import java.util.HashMap;
import java.io.IOException;

public class TLV {
    // Processes the TLV and returns the map of data
    public TLV(byte[] data) {
        // Cdol essentially contains tags and lengths (no data)
        // It instructs us on how to create a Challenge Request
        // 9F02 06 9F03 06 9F1A 02 95 05 5F2A 02 9A 03 9C 01 9F37 04 9F35 01 9F34 03

        this.tlist = new HashMap<Integer, byte[]>();
        for (int i = 0; i < data.length; ) {
            // Tags can be 1 or 2 bytes
            int tagn = 0;
            if ((data[i] & 0x1F) == 0x1F)
                tagn = (((int) data[i++]) & 0xFF) * 256;
            tagn |= (int) (data[i++] & 0xFF);

            // Length can 1, 2 or 3 bytes
            int len = (int) (data[i++] & 0xFF);
            if (len < 128) {
                this.tlist.put(tagn, java.util.Arrays.copyOfRange(data, i, i+len));
                i += len;
            }
            else {
                int blen = 0;
                for (int j = 0; j < (len & 127); j++) {
                    blen = blen << 8;
                    blen = (int) (data[i++] & 0xFF);
                }
                this.tlist.put(tagn, java.util.Arrays.copyOfRange(data, i, i+blen));
                i += blen;
            }
        }
    }

    public byte[] get(int tagn) {
        return tlist.getOrDefault(tagn, new byte[0]);
    }

    public static byte[] createDOL(byte[] cdol1, int stage, int challenge) {
        byte[] chall = new byte[4];
        chall[0] = (byte) ((((challenge / 10000000) % 10) << 4) | ((challenge / 1000000) % 10));
        chall[1] = (byte) ((((challenge / 100000) % 10) << 4) | ((challenge / 10000) % 10));
        chall[2] = (byte) ((((challenge / 1000) % 10) << 4) | ((challenge / 100) % 10));
        chall[3] = (byte) ((((challenge / 10) % 10) << 4) | (challenge % 10));
        HashMap<Integer, byte[]> overr = new HashMap<Integer, byte[]>();
        overr.put(0x8A, new byte[]{0x5a, 0x33});
        overr.put(0x95, new byte[]{(byte) 128, 0, 0, 0, 0});
        overr.put(0x9A, new byte[]{1, 1, 1});
        overr.put(0x9F33, new byte[]{32, (byte) 128, 0});
        overr.put(0x9F34, new byte[]{1, 0, 2});
        overr.put(0x9F35, new byte[]{0x34});
        overr.put(0x9F37, chall);

        // Cdol essentially contains tags and lengths (no data)
        // It instructs us on how to create a Challenge Request
        // 9F02 06 9F03 06 9F1A 02 95 05 5F2A 02 9A 03 9C 01 9F37 04 9F35 01 9F34 03

        ByteArrayOutputStream req = new ByteArrayOutputStream();
        for (int i = 0; i < cdol1.length; ) {
            // Tags can be 1 or 2 bytes
            int tagn = 0;
            if ((cdol1[i] & 0x1F) == 0x1F)
                tagn = (((int) cdol1[i++]) & 0xFF) * 256;
            tagn |= (int) (cdol1[i++] & 0xFF);

            // Length can 1, 2 or 3 bytes
            int len = (int) (cdol1[i++] & 0xFF);
            if (len >= 128) {
                int blen = 0;
                for (int j = 0; j < (len & 127); j++) {
                    blen = blen << 8;
                    blen |= (int) (cdol1[i++] & 0xFF);
                }
                len = blen;
            }

            // Now write data
            try {
                if (overr.containsKey(tagn))
                    req.write(overr.get(tagn));
                else
                    req.write(new byte[len]);
            }
            catch (IOException ex) {

            }
        }
        int stageb = stage == 1 ? 0x80 : 0x00;
        byte[] desc = req.toByteArray();
        byte[] msg = new byte[5+desc.length];
        msg[0] = (byte)0x80;
        msg[1] = (byte)0xAE;
        msg[2] = (byte)stageb;
        msg[3] = 0;
        msg[4] = (byte)desc.length;
        System.arraycopy(desc, 0, msg, 5, desc.length);

        return msg;
    }

    private HashMap<Integer, byte[]> tlist;
}