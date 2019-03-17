package net.davidgf.bankcap;

import android.app.AlertDialog;
import android.content.DialogInterface;
import androidx.appcompat.app.AppCompatActivity;

public class BaseActivity extends AppCompatActivity {

    public void showErrorDialog(String description) {
        showAlertDialog(getResources().getString(R.string.error_dialog_title), description);
    }

    public void showAlertDialog(String title, String description) {
        new AlertDialog.Builder(BaseActivity.this)
                .setTitle(title)
                .setMessage(description)
                .setNeutralButton(getResources().getString(R.string.ok), new
                        DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int id) {
                                dialog.cancel();
                            }
                        }).show();
    }
}
