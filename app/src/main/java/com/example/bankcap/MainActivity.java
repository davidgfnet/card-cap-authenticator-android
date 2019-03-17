package com.example.bankcap;

import java.util.HashMap;
import java.nio.charset.StandardCharsets;
import android.content.Intent;
import android.hardware.usb.UsbEndpoint;
import android.content.Context;
import android.support.v7.app.AppCompatActivity;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.content.IntentFilter;
import android.content.BroadcastReceiver;
import android.os.Bundle;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbConstants;
import android.hardware.usb.UsbInterface;
import android.hardware.usb.UsbManager;
import android.hardware.usb.UsbDeviceConnection;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.view.View;
import android.util.Log;
/// import apdu4j.LogginCardTerminal;

public class MainActivity extends AppCompatActivity {

    public void showError(String e) {
        AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
        builder.setTitle("Card reader error");
        builder.setMessage(e);
        builder.create();
        builder.show();
    }

    public byte[] ccidMsg(int msgType, byte [] edata) {
        byte [] msg = new byte[10 + edata.length];
        msg[0] = (byte)msgType;
        msg[1] = (byte)edata.length;
        msg[2] = (byte)(edata.length >> 8);
        msg[3] = (byte)(edata.length >> 16);
        msg[4] = (byte)(edata.length >> 24);
        msg[5] = 0; // slotn
        msg[6] = (byte)req_counter++; // seqn
        msg[7] = 0; // Lc
        msg[8] = 0;
        msg[9] = 0;

        // Now attach sHeader and edata
        System.arraycopy(edata, 0, msg, 10, edata.length);
        return msg;
    }

    public byte[] sendAPDU(UsbDeviceConnection conn, byte[] pload) {
        byte[] imsg = ccidMsg(0x6F, pload);
        if (conn.bulkTransfer(outep_, imsg, imsg.length, 5000) <= 0) {
            showError("Error while sending APDU");
            return new byte[0];
        }
        byte[] response = new byte[512];
        int received = conn.bulkTransfer(inep_, response, response.length, 5000);
        if (received <= 0) {
            showError("Error while receiving APDU response");
            return new byte[0];
        }
        if (received < 12)
            return new byte[0];

        return java.util.Arrays.copyOfRange(response, 10, received);
    }

    private UsbEndpoint inep_ = null;
    private UsbEndpoint outep_ = null;
    private UsbDevice dev_ = null;
    private UsbInterface iface_ = null;

    private int req_counter = 0;

    private static final String ACTION_USB_PERMISSION =
            "com.android.example.USB_PERMISSION";
    private final BroadcastReceiver usbReceiver = new BroadcastReceiver() {

        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (ACTION_USB_PERMISSION.equals(action)) {
                synchronized (this) {
                    UsbDevice device = (UsbDevice)intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);

                    if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                        if(device != null){
                            //call method to set up device communication
                            Log.i("foo", "permission Accepted? " + device);
                        }
                    }
                    else {
                        Log.d("foo", "permission denied for device " + device);
                    }
                }
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        UsbManager manager = (UsbManager) getSystemService(Context.USB_SERVICE);
        PendingIntent permissionIntent = PendingIntent.getBroadcast(this, 0, new Intent(ACTION_USB_PERMISSION), 0);
        registerReceiver(usbReceiver, new IntentFilter(ACTION_USB_PERMISSION));

        // Enumerate devices and try to find a standard Card reader
        HashMap<String, UsbDevice> deviceList = manager.getDeviceList();
        for (UsbDevice dev : deviceList.values()) {
            inep_ = null;
            outep_ = null;
            // Smart cards usually have an interface of class 11
            for (int inum = 0; inum < dev.getInterfaceCount(); inum++) {
                UsbInterface iface = dev.getInterface(inum);
                if (iface.getInterfaceClass() == 11) {
                    for (int e = 0; e < iface.getEndpointCount(); e++) {
                        UsbEndpoint ep = iface.getEndpoint(e);
                        if (ep.getType() == UsbConstants.USB_ENDPOINT_XFER_BULK) {
                            if (ep.getDirection() == UsbConstants.USB_DIR_OUT)
                                outep_ = ep;
                            else
                                inep_ = ep;
                        }
                    }
                }
                // Claim the iface if it looks good and stop there!
                if (inep_ != null && outep_ != null) {
                    manager.requestPermission(dev, permissionIntent);
                    dev_ = dev;
                    iface_ = iface;
                    break;
                }
            }
        }
        if (dev_ == null || inep_ == null || outep_ == null) {
            showError("Could not find a suitable USB card reader");
            return;
        }

        final Button button = findViewById(R.id.buttonGo);
        button.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                final EditText chbox = findViewById(R.id.textChallenge);
                final EditText pbox = findViewById(R.id.textPIN);
                TextView logt = findViewById(R.id.textLog);

                UsbManager manager = (UsbManager) getSystemService(Context.USB_SERVICE);
                UsbDeviceConnection conn = manager.openDevice(dev_);
                if (conn != null) {
                    if (!conn.claimInterface(iface_, true))
                        conn = null; // Failed to claim interface!
                }
                if (conn == null) {
                    showError("Could not connect to the USB device");
                    return;
                }

                // OK we've got the two relevant EPs to communicate with the device.
                byte[] response = new byte[512];
                byte[] imsg = ccidMsg(0x62, new byte[0]);
                if (conn.bulkTransfer(outep_, imsg, imsg.length, 5000) <= 0) {
                    showError("Error while sending reset command");
                    return;
                }
                int received = conn.bulkTransfer(inep_, response, response.length, 5000);
                if (received <= 0) {
                    showError("Error while receiving ATR");
                    return;
                }

                // Selects Postfinance Application
                byte[] select_app = {0, (byte)0xA4, 4, 0, 7, (byte)0xA0, 0, 0, 1, 17, (byte)0x80, 2};
                response = sendAPDU(conn, select_app);
                if (response.length != 2) {
                    showError("Error while sending App select command");
                    return;
                }
                // and reads data back
                int rsize = response[1] & 0xFF;
                byte[] read_resp = {0, (byte) 0xC0, 0, 0, response[1]};
                byte[] response2 = sendAPDU(conn, read_resp);
                if (response2.length != rsize + 2) {
                    showError("Error while sending App select read command");
                    return;
                }

                // The data we received must meet some Criteria :)
                TLV obj = new TLV(java.util.Arrays.copyOfRange(response2, 0, rsize - 2));
                TLV fci = new TLV(obj.get(0x6F));
                TLV fcip = new TLV(fci.get(0xA5));

                byte[] postfid = "PostFinance ID".getBytes(StandardCharsets.US_ASCII);
                if (!java.util.Arrays.equals(fcip.get(0x50), postfid)) {
                    showError("The card does not look like a PostFinance Card");
                    return;
                }

                // Gets the processing options
                byte[] getpr = {(byte)0x80, (byte)0xA8, 0, 0, 2, (byte)0x83, 0};
                response = sendAPDU(conn, getpr);
                if (response.length != 2) {
                    showError("Error while sending Get Processing Opts command");
                    return;
                }
                // and reads data back too.
                rsize = response[1] & 0xFF;
                byte[] read_resp2 = {0, (byte) 0xC0, 0, 0, response[1]};
                response2 = sendAPDU(conn, read_resp2);
                if (response2.length != rsize + 2) {
                    showError("Error while sending Reading Opts response");
                    return;
                }

                // Reads SFI 01  // TODO: Loop and query all the file chunks
                byte[] readsfi = {0, (byte)0xb2, 1, 12, 0};
                response = sendAPDU(conn, readsfi);
                if (response.length != 2) {
                    showError("Error while sending SFI read");
                    return;
                }
                // and reads data back too.
                rsize = response[1] & 0xFF;
                byte[] read_resp3 = {0, (byte)0xb2, 1, 12, response[1]};
                response2 = sendAPDU(conn, read_resp3);
                if (response2.length != rsize + 2) {
                    showError("Error while sending effecetive SFI read");
                    return;
                }

                // We read the SFI file, parse it, since it contains useful info for challenge
                // generation.
                obj = new TLV(java.util.Arrays.copyOfRange(response2, 0, rsize - 2));
                TLV emv = new TLV(obj.get(0x70));
                byte[] chreq1 = TLV.createDOL(emv.get(0x8C), 1, Integer.parseInt(chbox.getText().toString()));
                byte[] chreq2 = TLV.createDOL(emv.get(0x8D), 2, Integer.parseInt(chbox.getText().toString()));
                byte[] filtmsg = emv.get(0x9F56);

                // Reads the PIN counter here
                byte[] read_pin_counter = {(byte)0x80, (byte)0xca, (byte)0x9f, 23, 0};
                response = sendAPDU(conn, read_pin_counter);
                if (response.length != 2 || response[0] != (byte)0x6C) {
                    showError("Error while querying try counter");
                    return;
                }
                // and read actual data
                rsize = response[1] & 0xFF;
                byte[] read_pin_counter2 = {(byte)0x80, (byte)0xca, (byte)0x9f, 23, response[1]};
                response2 = sendAPDU(conn, read_pin_counter2);
                if (response2.length != rsize + 2 || response2[0] != (byte)0x9F ||
                        response2[1] != 23 || response2[2] != 1) {
                    showError("Error while parsing read counter");
                    return;
                }

                logt.append("Card has " + Integer.toString(response2[3]) + " PIN attempts\n");

                // Authenticate card via PIN number
                int pcode = Integer.parseInt(pbox.getText().toString());
                byte[] authmsg = new byte[13];
                authmsg[1] = 32;
                authmsg[3] = (byte)128;
                authmsg[4] = 8;
                authmsg[5] = 36;
                authmsg[6] = (byte)(((pcode / 1000) << 4) | ((pcode / 100) % 10));
                authmsg[7] = (byte)((((pcode / 10) % 10) << 4) | (pcode % 10));
                for (int i = 8; i < 13; i++)
                    authmsg[i] = (byte)0xff;

                response = sendAPDU(conn, authmsg);
                if (response.length != 2 || response[0] != (byte)0x90 || response[1] != 0) {
                    showError("PIN authentication failed?! Beware do not block your card");
                    return;
                }

                // Now can send the first challenge
                response = sendAPDU(conn, chreq1);
                if (response.length != 2 || response[0] != 0x61) {
                    showError("Error while sending CH1 read");
                    return;
                }
                // now read data
                rsize = response[1] & 0xFF;
                byte[] read_resp5 = {0, (byte) 0xC0, 0, 0, response[1]};
                byte[] ch1resp = sendAPDU(conn, read_resp5);
                if (ch1resp.length != rsize + 2 || ch1resp[rsize] != (byte)0x90 || ch1resp[rsize+1] != 0) {
                    showError("Error while reading chan1");
                    return;
                }

                // Grab some response data, for OTP calc
                obj = new TLV(java.util.Arrays.copyOfRange(ch1resp, 0, rsize - 2));
                emv = new TLV(obj.get(0x77));
                byte[] r_iad = emv.get(0x9F10);
                byte[] r_ac  = emv.get(0x9F26);
                byte[] r_cid = emv.get(0x9F27);
                byte[] r_atc = emv.get(0x9F36);

                // Generate the ipb mask
                byte[] srdata = new byte[r_cid.length + r_atc.length + r_ac.length + r_iad.length];
                System.arraycopy(r_cid, 0, srdata, 0, r_cid.length);
                System.arraycopy(r_atc, 0, srdata, r_cid.length, r_atc.length);
                System.arraycopy(r_ac, 0,  srdata, r_cid.length + r_atc.length, r_ac.length);
                System.arraycopy(r_iad, 0, srdata, r_cid.length + r_atc.length + r_ac.length, r_iad.length);
                int otpn = 0;
                for (int i = 0; i < filtmsg.length; i++) {
                    for (int j = 7; j >= 0; j--) {
                        if ((filtmsg[i] & (1 << j)) != 0) {
                            otpn = (otpn << 1);
                            if (i < srdata.length && (srdata[i] & (1 << j)) != 0)
                                otpn |= 1;
                        }
                    }
                }
                logt.append("DATA: " + bytesToHex(srdata) + "\n");
                logt.append("FILT: " + bytesToHex(filtmsg) + "\n");
                logt.append("Calculated OTP " + Integer.toString(otpn) + "\n");

                /* // This is needed for MODE 2?
                // Now can send the second? challenge (is this really needed? IDKTS)
                response = sendAPDU(conn, chreq2);
                if (response.length != 2 || response[0] != 0x61) {
                    showError("Error while sending CH2 read");
                    return;
                }
                // now read data
                rsize = response[1] & 0xFF;
                byte[] read_resp6 = {0, (byte) 0xC0, 0, 0, response[1]};
                byte[] ch2resp = sendAPDU(conn, read_resp6);
                if (ch2resp.length != rsize + 2 || ch2resp[rsize] != (byte)0x90 || ch2resp[rsize+1] != 0) {
                    showError("Error while reading chan2");
                    return;
                }*/

                logt.append("Transaction finished correctly\n");

                // Log.i("foo", "OOOOOOOOOOO " + Integer.toString(received));
            }
        });
    }

    private final static char[] hexArray = "0123456789ABCDEF".toCharArray();
    public static String bytesToHex(byte[] bytes) {
        char[] hexChars = new char[bytes.length * 2];
        for ( int j = 0; j < bytes.length; j++ ) {
            int v = bytes[j] & 0xFF;
            hexChars[j * 2] = hexArray[v >>> 4];
            hexChars[j * 2 + 1] = hexArray[v & 0x0F];
        }
        return new String(hexChars);
    }
}
