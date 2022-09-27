package com.yoshione.fingen;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.provider.DocumentsContract;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.Menu;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;
import androidx.preference.PreferenceManager;

import com.dropbox.core.DbxException;
import com.dropbox.core.android.Auth;
import com.dropbox.core.v2.DbxClientV2;
import com.dropbox.core.v2.files.FileMetadata;
import com.dropbox.core.v2.files.Metadata;
import com.dropbox.core.v2.users.FullAccount;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.textfield.TextInputLayout;
import com.yoshione.fingen.dropbox.DropboxClient;
import com.yoshione.fingen.dropbox.UploadTask;
import com.yoshione.fingen.dropbox.UserAccountTask;
import com.yoshione.fingen.utils.DateTimeFormatter;
import com.yoshione.fingen.utils.FabMenuController;
import com.yoshione.fingen.utils.FileUtils;
import com.yoshione.fingen.utils.RequestCodes;
import com.yoshione.fingen.widgets.ToolbarActivity;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import butterknife.BindView;
import butterknife.ButterKnife;
import permissions.dispatcher.NeedsPermission;
import permissions.dispatcher.OnNeverAskAgain;
import permissions.dispatcher.OnPermissionDenied;
import permissions.dispatcher.OnShowRationale;
import permissions.dispatcher.PermissionRequest;
import permissions.dispatcher.RuntimePermissions;

@RuntimePermissions
public class ActivityBackup extends ToolbarActivity {

    private static final String TAG = "ActivityBackup";

    private static final int MSG_SHOW_DIALOG = 0;
    private static final int MSG_DOWNLOAD_FILE = 1;

    @BindView(R.id.fabBackup)
    FloatingActionButton fabBackup;
    @BindView(R.id.fabRestore)
    FloatingActionButton fabRestore;
    @BindView(R.id.fabRestoreFromDropbox)
    FloatingActionButton fabRestoreFromDropbox;
    @BindView(R.id.fabBackupToOriginDB)
    FloatingActionButton fabBackupToOrigin;
    @BindView(R.id.fabMenuButtonRoot)
    FloatingActionButton fabMenuButtonRoot;
    @BindView(R.id.switchCompatEnablePasswordProtection)
    SwitchCompat mSwitchCompatEnablePasswordProtection;
    @BindView(R.id.editTextPassword)
    EditText mEditTextPassword;
    @BindView(R.id.editTextDropboxAccount)
    EditText mEditTextDropboxAccount;
    @BindView(R.id.textInputLayoutDropboxAccount)
    TextInputLayout mTextInputLayoutDropboxAccount;
    @BindView(R.id.textViewLastBackupToDropbox)
    TextView mTextViewLastBackupToDropbox;
    @BindView(R.id.buttonLogoutFromDropbox)
    Button mButtonLogoutFromDropbox;
    @BindView(R.id.fabBGLayout)
    View fabBGLayout;
    @BindView(R.id.fabBackupLayout)
    LinearLayout mFabBackupLayout;
    @BindView(R.id.fabRestoreLayout)
    LinearLayout mFabRestoreLayout;
    @BindView(R.id.fabRestoreFromDropboxLayout)
    LinearLayout mFabRestoreFromDropboxLayout;
    @BindView(R.id.fabBackupToOriginDBLayout)
    LinearLayout mFabBackupToOriginDBLayout;

    private SharedPreferences prefs;
    private UpdateRwHandler mHandler;
    private FabMenuController mFabMenuController;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ButterKnife.bind(this);
        prefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        initFabMenu();
        initPasswordProtection();
        initDropbox();
        mHandler = new UpdateRwHandler(this);
    }

    @Override
    protected int getLayoutResourceId() {
        return R.layout.activity_backup;
    }

    @Override
    protected String getLayoutTitle() {
        return getString(R.string.ent_backup_data);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        menu.findItem(R.id.action_go_home).setVisible(true);
        return true;

    }

    private void initPasswordProtection() {
        mSwitchCompatEnablePasswordProtection.setChecked(prefs.getBoolean("enable_backup_password", false));
        mSwitchCompatEnablePasswordProtection.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean isChecked) {
                String password = prefs.getString("backup_password", "");
                prefs.edit().putBoolean("enable_backup_password", isChecked & !password.isEmpty()).apply();
            }
        });
        mEditTextPassword.setText(prefs.getString("backup_password", ""));
        mEditTextPassword.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence charSequence, int i, int i1, int i2) {

            }

            @Override
            public void onTextChanged(CharSequence charSequence, int i, int i1, int i2) {
                String password = String.valueOf(charSequence);
                prefs.edit().putString("backup_password", password).apply();
                prefs.edit().putBoolean("enable_backup_password", mSwitchCompatEnablePasswordProtection.isChecked()
                        & !password.isEmpty()).apply();
            }

            @Override
            public void afterTextChanged(Editable editable) {

            }
        });
    }

    private void initDropbox() {
        getUserAccount();
        final SharedPreferences dropboxPrefs = getSharedPreferences("com.yoshione.fingen.dropbox", Context.MODE_PRIVATE);
        final String token = dropboxPrefs.getString("dropbox-token", null);
        mEditTextDropboxAccount.setOnClickListener(view -> {
            if (token == null) {
                Auth.startOAuth2Authentication(ActivityBackup.this, getString(R.string.DROPBOX_APP_KEY));
            }
        });
        mButtonLogoutFromDropbox.setVisibility(token == null ? View.GONE : View.VISIBLE);
        mButtonLogoutFromDropbox.setOnClickListener(view -> {
            AsyncTask.execute(() -> {
                try {
                    DropboxClient.getClient(token).auth().tokenRevoke();
                } catch (DbxException e) {
                    e.printStackTrace();
                }
            });
            dropboxPrefs.edit().remove("dropbox-token").apply();
            dropboxPrefs.edit().remove(FgConst.PREF_DROPBOX_ACCOUNT).apply();
            initDropbox();
        });
        mFabMenuController.setViewVisibility(mFabRestoreFromDropboxLayout, token == null ? View.GONE : View.VISIBLE);
        initLastDropboxBackupField();
    }

    private void initLastDropboxBackupField() {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
        long dateLong = preferences.getLong(FgConst.PREF_SHOW_LAST_SUCCESFUL_BACKUP_TO_DROPBOX, 0);
        String title = getString(R.string.ttl_last_backup_to_dropbox);
        if (dateLong == 0) {
            title = String.format("%s -", title);
        } else {
            Date date = new Date(dateLong);
            DateTimeFormatter dtf = DateTimeFormatter.getInstance(this);
            title = String.format("%s %s %s", title, dtf.getDateMediumString(date), dtf.getTimeShortString(date));
        }
        mTextViewLastBackupToDropbox.setText(title);
    }

    public void saveAccessToken() {
        String accessToken = Auth.getOAuth2Token(); //generate Access Token
        if (accessToken != null) {
            //Store accessToken in SharedPreferences
            SharedPreferences dropboxPrefs = getSharedPreferences("com.yoshione.fingen.dropbox", Context.MODE_PRIVATE);
            dropboxPrefs.edit().putString("dropbox-token", accessToken).apply();
        }
    }

    @Override
    public void onResume() {
        saveAccessToken();
        initDropbox();
        super.onResume();
    }

    protected void getUserAccount() {
        final SharedPreferences dropboxPrefs = getSharedPreferences("com.yoshione.fingen.dropbox", Context.MODE_PRIVATE);
        mEditTextDropboxAccount.setText(dropboxPrefs.getString(FgConst.PREF_DROPBOX_ACCOUNT, ""));
        String token = dropboxPrefs.getString("dropbox-token", null);
        if (token == null) return;
        new UserAccountTask(DropboxClient.getClient(token), new UserAccountTask.TaskDelegate() {
            @Override
            public void onAccountReceived(FullAccount account) {
                //Print account's info
                Log.d("User", account.getEmail());
                Log.d("User", account.getName().getDisplayName());
                Log.d("User", account.getAccountType().name());
                mEditTextDropboxAccount.setText(account.getEmail());
                dropboxPrefs.edit().putString(FgConst.PREF_DROPBOX_ACCOUNT, account.getEmail()).apply();
            }

            @Override
            public void onError(Exception error) {
                Log.e("User", "Error receiving account details.", error);
            }
        }).execute();
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode,
                                 Intent resultData) {
        super.onActivityResult(requestCode, resultCode, resultData);
        if (requestCode == RequestCodes.REQUEST_CODE_SELECT_FOLDER && resultCode == Activity.RESULT_OK) {
            // The result data contains a URI for the document or directory that
            // the user selected.
            Uri uri = null;
            if (resultData != null) {
                uri = resultData.getData();
                getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                prefs.edit().putString("dropbox-token", uri.getPath()).apply();
            }
        }
    }


    private void initFabMenu() {
        mFabMenuController = new FabMenuController(fabMenuButtonRoot, fabBGLayout, this, mFabBackupLayout, mFabRestoreLayout, mFabRestoreFromDropboxLayout, mFabBackupToOriginDBLayout);
        fabBackup.setOnClickListener(v -> {
            ActivityBackupPermissionsDispatcher.backupDBWithPermissionCheck((ActivityBackup) v.getContext());
            mFabMenuController.closeFABMenu();
        });
        fabRestore.setOnClickListener(v -> {
            ActivityBackupPermissionsDispatcher.restoreDBWithPermissionCheck((ActivityBackup) v.getContext());
            mFabMenuController.closeFABMenu();
        });
        fabRestoreFromDropbox.setOnClickListener(v -> {
            ActivityBackupPermissionsDispatcher.restoreDBFromDropboxWithPermissionCheck((ActivityBackup) v.getContext());
            mFabMenuController.closeFABMenu();
        });
        fabBackupToOrigin.setOnClickListener(v -> {
            ActivityBackupPermissionsDispatcher.backupToOriginDBWithPermissionCheck((ActivityBackup) v.getContext());
            mFabMenuController.closeFABMenu();
        });
    }

    @Override
    public void onBackPressed() {
        if (mFabMenuController.isFABOpen()) {
            mFabMenuController.closeFABMenu();
        } else {
            super.onBackPressed();
        }
    }


    /**
     * Saves passed ZIP to DropBox, if it is configured.
     * @param zip Saved backup file. If null, method won't do anything, instead showing user a toast with warning.
     */
    void saveZipToDB(@Nullable File zip) {
        SharedPreferences dropboxPrefs = getSharedPreferences("com.yoshione.fingen.dropbox", Context.MODE_PRIVATE);
        String token = dropboxPrefs.getString("dropbox-token", null);
        if (zip == null) {
            Toast.makeText(ActivityBackup.this, "Nothing to back up (no entries?)", Toast.LENGTH_SHORT).show();
            return;
        }
        if (token == null) { return; }
        new UploadTask(DropboxClient.getClient(token), zip, () -> {
            Toast.makeText(ActivityBackup.this, "File uploaded successfully", Toast.LENGTH_SHORT).show();
            SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(ActivityBackup.this);
            preferences.edit().putLong(FgConst.PREF_SHOW_LAST_SUCCESFUL_BACKUP_TO_DROPBOX, new Date().getTime()).apply();
            initLastDropboxBackupField();
        }).execute();
    }

    @NeedsPermission({Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE})
    void backupDB() {
        File zip = null;
        boolean ok = true;
        try {
            String backupPath = prefs.getString("backup_folder", "");
            if (backupPath.isEmpty()) {
                requestFingenBackupFolder(null);
                backupPath = prefs.getString("backup_folder", "");
            }
            zip = DBHelper.getInstance(getApplicationContext()).backupDB(backupPath, true);
        } catch (IOException e) {
            Toast.makeText(ActivityBackup.this, "Cannot save DB locally, check permissions.", Toast.LENGTH_SHORT).show();
            ok = false;
            Log.e(TAG, "Failed to save ZIP", e);
        }
        if (ok) { saveZipToDB(zip); }
    }

    @NeedsPermission({Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE})
    void restoreDB() {
        new FileSelectDialog(this).showSelectBackupDialog();
    }

    @NeedsPermission({Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE})
    void restoreDBFromDropbox() {
        Thread t = new Thread(() -> {
            SharedPreferences dropboxPrefs = getApplicationContext().getSharedPreferences("com.yoshione.fingen.dropbox", Context.MODE_PRIVATE);
            String token = dropboxPrefs.getString("dropbox-token", null);
            List<Metadata> metadataList;
            List<MetadataItem> items = new ArrayList<>();
            try {
                metadataList = DropboxClient.getListFiles(DropboxClient.getClient(token));
                for (int i = metadataList.size() - 1; i >= 0; i--) {
                    if (metadataList.get(i).getName().toLowerCase().contains(".zip")) {
                        items.add(new MetadataItem((FileMetadata) metadataList.get(i)));
                    }
                }
            } catch (Exception e) {
                Log.d(TAG, "Error read list of files from Dropbox");
            }
            mHandler.sendMessage(mHandler.obtainMessage(MSG_SHOW_DIALOG, items));
        });
        t.start();
    }

    @NeedsPermission({Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE})
    void backupToOriginDB() {
        File zip = null;
        boolean ok = true;
        try {
            String backupPath = prefs.getString("backup_folder", "");
            zip = DBHelper.getInstance(getApplicationContext()).backupToOriginDB(backupPath, true);
        } catch (IOException e) {
            Toast.makeText(ActivityBackup.this, "Cannot save DB locally, check permissions.", Toast.LENGTH_SHORT).show();
            ok = false;
            Log.e(TAG, "Failed to save ZIP", e);
        }
        if (ok) { saveZipToDB(zip); }
    }

    public void requestFingenBackupFolder(@Nullable Uri initial) {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        if (initial != null) { intent.putExtra(DocumentsContract.EXTRA_INITIAL_URI, initial); }
        this.startActivityForResult(intent, RequestCodes.REQUEST_CODE_SELECT_FOLDER);
    }


    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        // NOTE: delegate the permission handling to generated method
        ActivityBackupPermissionsDispatcher.onRequestPermissionsResult(this, requestCode, grantResults);
    }

    @OnShowRationale({Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE})
    void showRationaleForContact(PermissionRequest request) {
        // NOTE: Show a rationale to explain why the permission is needed, e.g. with a dialog.
        // Call proceed() or cancel() on the provided PermissionRequest to continue or abort
        showRationaleDialog(R.string.msg_permission_rw_external_storage_rationale, request);
    }

    @OnPermissionDenied({Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE})
    void onCameraDenied() {
        // NOTE: Deal with a denied permission, e.g. by showing specific UI
        // or disabling certain functionality
        Toast.makeText(this, R.string.msg_permission_rw_external_storage_denied, Toast.LENGTH_SHORT).show();
    }

    @OnNeverAskAgain({Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE})
    void onCameraNeverAskAgain() {
        Toast.makeText(this, R.string.msg_permission_rw_external_storage_never_askagain, Toast.LENGTH_SHORT).show();
    }

    private void showRationaleDialog(@StringRes int messageResId, final PermissionRequest request) {
        new AlertDialog.Builder(this)
                .setPositiveButton(R.string.act_next, (dialog, which) -> request.proceed())
                .setNegativeButton(android.R.string.cancel, (dialog, which) -> request.cancel())
                .setCancelable(false)
                .setMessage(R.string.msg_permission_rw_external_storage_rationale)
                .show();
    }

    @Override
    protected void onStart() {
        super.onStart();
        ActivityBackupPermissionsDispatcher.checkPermissionsWithPermissionCheck(this);
    }

    @NeedsPermission({Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE})
    void checkPermissions() {
        Log.d(TAG, "Check permissions");
    }

    private static class UpdateRwHandler extends Handler {
        WeakReference<ActivityBackup> mActivity;

        UpdateRwHandler(ActivityBackup activity) {
            mActivity = new WeakReference<>(activity);
        }

        @Override
        public void handleMessage(Message msg) {
            final ActivityBackup activity = mActivity.get();
            if (activity.isFinishing()) return;
//            if (activity == null) return;
            switch (msg.what) {
                case MSG_SHOW_DIALOG:
                    AlertDialog.Builder builderSingle = new AlertDialog.Builder(activity);
                    builderSingle.setTitle(activity.getResources().getString(R.string.ttl_select_db_file));

                    final ArrayAdapter<MetadataItem> arrayAdapter = new ArrayAdapter<>(activity, android.R.layout.select_dialog_singlechoice);
                    arrayAdapter.addAll((List<MetadataItem>) msg.obj);

                    builderSingle.setNegativeButton(
                            activity.getResources().getString(android.R.string.cancel),
                            (dialog, which) -> dialog.dismiss());

                    builderSingle.setAdapter(arrayAdapter, (dialog, which) -> {
                        ListView lw = ((AlertDialog) dialog).getListView();
                        final MetadataItem item = (MetadataItem) lw.getAdapter().getItem(which);
                        Thread thread = new Thread(() -> {
                            File file = new File(activity.getCacheDir(), item.mMetadata.getName());
                            try {
                                OutputStream outputStream = new FileOutputStream(file);
                                SharedPreferences dropboxPrefs = activity.getSharedPreferences("com.yoshione.fingen.dropbox", Context.MODE_PRIVATE);
                                String token = dropboxPrefs.getString("dropbox-token", null);
                                DbxClientV2 dbxClient = DropboxClient.getClient(token);
                                dbxClient.files().download(item.mMetadata.getPathLower(), item.mMetadata.getRev())
                                        .download(outputStream);
                                activity.mHandler.sendMessage(activity.mHandler.obtainMessage(MSG_DOWNLOAD_FILE, file));
                            } catch (DbxException e) {
                                e.printStackTrace();
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        });
                        thread.start();
                    });
                    builderSingle.show();
                    break;
                case MSG_DOWNLOAD_FILE:
                    try {
                        DBHelper.getInstance(activity).showRestoreDialog(((File) msg.obj).getCanonicalPath(), activity);
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
            }

        }
    }

    private class MetadataItem {
        private FileMetadata mMetadata;

        public MetadataItem(FileMetadata metadata) {
            mMetadata = metadata;
        }

        public String toString() {
            return mMetadata.getName();
        }
    }

    private class FileSelectDialog {
        final AppCompatActivity activity;

        FileSelectDialog(AppCompatActivity activity) {
            this.activity = activity;
        }

        void showSelectBackupDialog() {
            AlertDialog.Builder builderSingle = new AlertDialog.Builder(activity);
            builderSingle.setTitle(activity.getResources().getString(R.string.ttl_select_db_file));
            String folderUri = prefs.getString("backup_folder", "");
            if (folderUri.isEmpty()) {
                requestFingenBackupFolder(null);
                folderUri = prefs.getString("backup_folder", "");
            }

            List<File> files = FileUtils.getListFiles(getApplicationContext(), new File(folderUri), ".zip");
            List<String> names = new ArrayList<>();
            String path;
            for (int i = files.size() - 1; i >= 0; i--) {
                path = files.get(i).toString();
                names.add(path.substring(path.lastIndexOf("/") + 1));
            }

            final ArrayAdapter<String> arrayAdapter = new ArrayAdapter<>(activity, android.R.layout.select_dialog_singlechoice);
            arrayAdapter.addAll(names);

            builderSingle.setNegativeButton(
                    activity.getResources().getString(android.R.string.cancel),
                    (dialog, which) -> dialog.dismiss());

            builderSingle.setAdapter(arrayAdapter, (dialog, which) -> {
                ListView lw = ((AlertDialog) dialog).getListView();
                String fileName = (String) lw.getAdapter().getItem(which);
                DBHelper.getInstance(activity).showRestoreDialog(prefs.getString("backup_folder", "") + fileName, activity);
            });
            builderSingle.show();
        }
    }
}
