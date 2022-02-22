package net.davidgf.bankcap.fragments;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbConstants;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbEndpoint;
import android.hardware.usb.UsbInterface;
import android.hardware.usb.UsbManager;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;

import net.davidgf.bankcap.R;
import net.davidgf.bankcap.activities.CalculationActivity;
import net.davidgf.bankcap.utils.HexUtils;
import net.davidgf.bankcap.utils.TLV;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.HashMap;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;

public class CalculationFragment extends Fragment {

    private EditText pinEditText;
    private EditText challengeEditText;

    private UsbEndpoint inep_ = null;
    private UsbEndpoint outep_ = null;
    private UsbDevice dev_ = null;
    private UsbInterface iface_ = null;

    private int req_counter = 0;

    private static final String ACTION_USB_PERMISSION = "com.android.example.USB_PERMISSION";
    private final BroadcastReceiver usbReceiver = new BroadcastReceiver() {

        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (ACTION_USB_PERMISSION.equals(action)) {
                synchronized (this) {
                    UsbDevice device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                    if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED,
                            false)) {
                        if (device != null) {
                            // call method to set up device communication
                            writeLog("permission Accepted? " + device);
                            return;
                        }
                    }
                    writeLog("permission denied for device " + device);
                }
            }
        }
    };

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        ViewGroup viewGroup = (ViewGroup) inflater.inflate(R.layout.fragment_calculation, container,
                false);

        this.pinEditText = viewGroup.findViewById(R.id.pin_input);
        this.challengeEditText = viewGroup.findViewById(R.id.challenge_input);
        Button calculateButton = viewGroup.findViewById(R.id.calculate_button);
        calculateButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String pin = pinEditText.getText().toString();
                String chl = challengeEditText.getText().toString();
                if (pin.isEmpty() || chl.isEmpty())
                    showErrorDialog("Please fill PIN and Challenge");
                else
                    calculate(pin, Integer.parseInt(chl));
            }
        });
        writeLog("CalculationFragment's view created");

        this.findDevice();

        return viewGroup;
    }

    private void calculate(String pcode, int challenge) {
        UsbManager manager = (UsbManager) getContext().getSystemService(Context.USB_SERVICE);
        UsbDeviceConnection conn = manager.openDevice(dev_);
        if (conn != null) {
            if (!conn.claimInterface(iface_, true))
                conn = null; // Failed to claim interface!
        }
        if (conn == null) {
            showErrorDialog("Could not connect to the USB device");
            return;
        }

        // OK we've got the two relevant EPs to communicate with the device.
        byte[] response = new byte[512];
        byte[] imsg = ccidMsg(0x62, new byte[0]);
        if (conn.bulkTransfer(outep_, imsg, imsg.length, 5000) <= 0) {
            showErrorDialog("Error while sending reset command");
            return;
        }
        int received = conn.bulkTransfer(inep_, response, response.length, 5000);
        if (received <= 0) {
            showErrorDialog("Error while receiving ATR");
            return;
        }

        // Selects Postfinance Application
        byte[] select_app = {0, (byte) 0xA4, 4, 0, 7, (byte) 0xA0, 0, 0, 1, 17, (byte) 0x80, 2};
        response = sendAPDU(conn, select_app);
        if (response.length != 2) {
            showErrorDialog("Error while sending App select command");
            return;
        }
        // and reads data back
        int rsize = response[1] & 0xFF;
        byte[] read_resp = {0, (byte) 0xC0, 0, 0, response[1]};
        byte[] response2 = sendAPDU(conn, read_resp);
        if (response2.length != rsize + 2) {
            showErrorDialog("Error while sending App select read command");
            return;
        }

        // The data we received must meet some Criteria :)
        TLV obj = new TLV(java.util.Arrays.copyOfRange(response2, 0, rsize - 2));
        TLV fci = new TLV(obj.get(0x6F));
        TLV fcip = new TLV(fci.get(0xA5));

        byte[] postfid;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.KITKAT) {
            postfid = "PostFinance ID".getBytes(StandardCharsets.US_ASCII);
        } else {
            postfid = "PostFinance ID".getBytes();
        }
        if (!java.util.Arrays.equals(fcip.get(0x50), postfid)) {
            showErrorDialog("The card does not look like a PostFinance Card");
            return;
        }

        // Gets the processing options
        byte[] getpr = {(byte) 0x80, (byte) 0xA8, 0, 0, 2, (byte) 0x83, 0};
        response = sendAPDU(conn, getpr);
        if (response.length != 2) {
            showErrorDialog("Error while sending Get Processing Opts command");
            return;
        }
        // and reads data back too.
        rsize = response[1] & 0xFF;
        byte[] read_resp2 = {0, (byte) 0xC0, 0, 0, response[1]};
        response2 = sendAPDU(conn, read_resp2);
        if (response2.length != rsize + 2) {
            showErrorDialog("Error while sending Reading Opts response");
            return;
        }

        // Reads SFI 01  // TODO: Loop and query all the file chunks
        byte[] readsfi = {0, (byte) 0xb2, 1, 12, 0};
        response = sendAPDU(conn, readsfi);
        if (response.length != 2) {
            showErrorDialog("Error while sending SFI read");
            return;
        }
        // and reads data back too.
        rsize = response[1] & 0xFF;
        byte[] read_resp3 = {0, (byte) 0xb2, 1, 12, response[1]};
        response2 = sendAPDU(conn, read_resp3);
        if (response2.length != rsize + 2) {
            showErrorDialog("Error while sending effecetive SFI read");
            return;
        }

        // We read the SFI file, parse it, since it contains useful info for challenge
        // generation.
        obj = new TLV(java.util.Arrays.copyOfRange(response2, 0, rsize - 2));
        TLV emv = new TLV(obj.get(0x70));
        byte[] chreq1 = TLV.createDOL(emv.get(0x8C), 1, challenge);
        byte[] chreq2 = TLV.createDOL(emv.get(0x8D), 2, challenge);
        byte[] filtmsg = emv.get(0x9F56);

        // Reads the PIN counter here
        byte[] read_pin_counter = {(byte) 0x80, (byte) 0xca, (byte) 0x9f, 23, 0};
        response = sendAPDU(conn, read_pin_counter);
        if (response.length != 2 || response[0] != (byte) 0x6C) {
            showErrorDialog("Error while querying try counter");
            return;
        }
        // and read actual data
        rsize = response[1] & 0xFF;
        byte[] read_pin_counter2 = {(byte) 0x80, (byte) 0xca, (byte) 0x9f, 23, response[1]};
        response2 = sendAPDU(conn, read_pin_counter2);
        if (response2.length != rsize + 2 || response2[0] != (byte) 0x9F ||
                response2[1] != 23 || response2[2] != 1) {
            showErrorDialog("Error while parsing read counter");
            return;
        }

        writeLog("Card has " + Integer.toString(response2[3]) + " PIN attempts");

        // Authenticate card via PIN number
        byte[] authmsg = new byte[13];
        authmsg[1] = 32;
        authmsg[3] = (byte) 128;
        authmsg[4] = 8;
        authmsg[5] = (byte) (0x20 + pcode.length());
        StringBuilder suffix = new StringBuilder(pcode);
        while (suffix.length() < 14)
            suffix.append("F");
        for (int i = 0; i < 7; i++) {
            authmsg[i + 6] = (byte) Integer.parseInt(suffix.substring(2 * i, 2 * i + 2), 16);
        }

        response = sendAPDU(conn, authmsg);
        if (response.length != 2 || response[0] != (byte) 0x90 || response[1] != 0) {
            showErrorDialog("PIN authentication failed?! Beware do not block your card");
            return;
        }

        // Now can send the first challenge
        response = sendAPDU(conn, chreq1);
        if (response.length != 2 || response[0] != 0x61) {
            showErrorDialog("Error while sending CH1 read");
            return;
        }
        // now read data
        rsize = response[1] & 0xFF;
        byte[] read_resp5 = {0, (byte) 0xC0, 0, 0, response[1]};
        byte[] ch1resp = sendAPDU(conn, read_resp5);
        if (ch1resp.length != rsize + 2 || ch1resp[rsize] != (byte) 0x90 || ch1resp[rsize + 1] != 0) {
            showErrorDialog("Error while reading chan1");
            return;
        }

        // Grab some response data, for OTP calc
        obj = new TLV(java.util.Arrays.copyOfRange(ch1resp, 0, rsize - 2));
        emv = new TLV(obj.get(0x77));
        byte[] r_iad = emv.get(0x9F10);
        byte[] r_ac = emv.get(0x9F26);
        byte[] r_cid = emv.get(0x9F27);
        byte[] r_atc = emv.get(0x9F36);

        // Generate the ipb mask
        byte[] srdata = new byte[r_cid.length + r_atc.length + r_ac.length + r_iad.length];
        System.arraycopy(r_cid, 0, srdata, 0, r_cid.length);
        System.arraycopy(r_atc, 0, srdata, r_cid.length, r_atc.length);
        System.arraycopy(r_ac, 0, srdata, r_cid.length + r_atc.length, r_ac.length);
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
        writeLog("DATA: " + HexUtils.bytesToHex(srdata));
        writeLog("FILT: " + HexUtils.bytesToHex(filtmsg));
        writeLog("Calculated OTP " + Integer.toString(otpn));

        /*
        // This is needed for MODE 2?
        // Now can send the second? challenge (is this really needed? IDKTS)
        response = sendAPDU(conn, chreq2);
        if (response.length != 2 || response[0] != 0x61) {
            showErrorDialog("Error while sending CH2 read");
            return;
        }
        // now read data
        rsize = response[1] & 0xFF;
        byte[] read_resp6 = {0, (byte) 0xC0, 0, 0, response[1]};
        byte[] ch2resp = sendAPDU(conn, read_resp6);
        if (ch2resp.length != rsize + 2 || ch2resp[rsize] != (byte) 0x90 || ch2resp[rsize + 1] != 0) {
            showErrorDialog("Error while reading chan2");
            return;
        }*/

        writeLog("Transaction finished correctly");

        // Log.i("foo", "OOOOOOOOOOO " + Integer.toString(received));
    }

    private void findDevice() {
        UsbManager manager = (UsbManager) getContext().getSystemService(Context.USB_SERVICE);
        PendingIntent permissionIntent = PendingIntent.getBroadcast(getContext(),
                0, new Intent(ACTION_USB_PERMISSION), 0);
        getContext().registerReceiver(usbReceiver, new IntentFilter(ACTION_USB_PERMISSION));

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
            showErrorDialog("Could not find a suitable USB card reader");
        }
    }

    private byte[] ccidMsg(int msgType, byte[] edata) {
        byte[] msg = new byte[10 + edata.length];
        msg[0] = (byte) msgType;
        msg[1] = (byte) edata.length;
        msg[2] = (byte) (edata.length >> 8);
        msg[3] = (byte) (edata.length >> 16);
        msg[4] = (byte) (edata.length >> 24);
        msg[5] = 0; // slotn
        msg[6] = (byte) req_counter++; // seqn
        msg[7] = 0; // Lc
        msg[8] = 0;
        msg[9] = 0;

        // Now attach sHeader and edata
        System.arraycopy(edata, 0, msg, 10, edata.length);
        return msg;
    }

    private byte[] sendAPDU(UsbDeviceConnection conn, byte[] pload) {
        byte[] imsg = ccidMsg(0x6F, pload);
        if (conn.bulkTransfer(outep_, imsg, imsg.length, 5000) <= 0) {
            showErrorDialog("Error while sending APDU");
            return new byte[0];
        }
        byte[] response = new byte[512];
        int received = conn.bulkTransfer(inep_, response, response.length, 5000);
        if (received <= 0) {
            showErrorDialog("Error while receiving APDU response");
            return new byte[0];
        }
        if (received < 12)
            return new byte[0];

        return Arrays.copyOfRange(response, 10, received);
    }


    private void showErrorDialog(String description) {
        CalculationActivity parentActivity = (CalculationActivity) getActivity();
        if (parentActivity == null) {
            return;
        }
        parentActivity.showErrorDialog(description);
    }

    private void writeLog(String log) {
        CalculationActivity parentActivity = (CalculationActivity) getActivity();
        if (parentActivity == null) {
            return;
        }
        parentActivity.writeLog(log);
    }
}
