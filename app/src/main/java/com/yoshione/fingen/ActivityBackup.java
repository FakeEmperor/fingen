package com.yoshione.fingen;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.provider.DocumentsContract;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.Menu;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;
import androidx.documentfile.provider.DocumentFile;
import androidx.preference.PreferenceManager;

import com.dropbox.core.DbxException;
import com.dropbox.core.InvalidAccessTokenException;
import com.dropbox.core.android.Auth;
import com.dropbox.core.v2.DbxClientV2;
import com.dropbox.core.v2.files.FileMetadata;
import com.dropbox.core.v2.files.Metadata;
import com.dropbox.core.v2.users.FullAccount;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.textfield.TextInputLayout;
import com.yoshione.fingen.dropbox.DropboxClient;
import com.yoshione.fingen.dropbox.UploadTask;
import com.yoshione.fingen.utils.DateTimeFormatter;
import com.yoshione.fingen.utils.FabMenuController;
import com.yoshione.fingen.utils.FileUtils;
import com.yoshione.fingen.widgets.ToolbarActivity;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import butterknife.BindView;
import butterknife.ButterKnife;

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
    private ActivityResultLauncher<Intent> mSharedFolderChooser;
    private ActivityResultLauncher<Intent> mBackupFileChooser;
    private final ExecutorService mExecutor = Executors.newSingleThreadExecutor();
    private final Handler mUiHandler = new Handler(Looper.getMainLooper());


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ButterKnife.bind(this);
        prefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
        initFabMenu();
        initPasswordProtection();
        initDropbox();
        mHandler = new UpdateRwHandler(this);
        mSharedFolderChooser = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == Activity.RESULT_OK) {
                        // There are no request codes
                        if (result.getData() != null) {
                            Uri uri = result.getData().getData();
                            getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                            prefs.edit().putString(FgConst.PREF_MANAGED_LOCATION, uri.toString()).apply();
                        }


                    }
                });
        mBackupFileChooser = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == Activity.RESULT_OK) {
                        // There are no request codes
                        if (result.getData() != null) {
                            Uri uri = result.getData().getData();
                            DBHelper.getInstance(this).showRestoreDialog((dialogInterface, which) -> DBHelper.getInstance(ActivityBackup.this).restoreDBFromUri(uri, ActivityBackup.this), this);
                        }


                    }
                });

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
        mSwitchCompatEnablePasswordProtection.setOnCheckedChangeListener((compoundButton, isChecked) -> {
            String password = prefs.getString("backup_password", "");
            prefs.edit().putBoolean("enable_backup_password", isChecked & !password.isEmpty()).apply();
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
        final String key = getString(R.string.DROPBOX_APP_KEY);
        mEditTextDropboxAccount.setOnClickListener(view -> {
            if (token == null) {
                Auth.startOAuth2Authentication(ActivityBackup.this, key);
            }
        });
        mButtonLogoutFromDropbox.setVisibility(token == null ? View.GONE : View.VISIBLE);
        mButtonLogoutFromDropbox.setOnClickListener(view -> {
            mExecutor.execute(() -> {
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

    @SuppressLint("ApplySharedPref")
    protected void getUserAccount() {
        final SharedPreferences dropboxPrefs = getSharedPreferences("com.yoshione.fingen.dropbox", Context.MODE_PRIVATE);
        mEditTextDropboxAccount.setText(dropboxPrefs.getString(FgConst.PREF_DROPBOX_ACCOUNT, ""));
        String token = dropboxPrefs.getString("dropbox-token", null);
        if (token == null) return;
        mExecutor.execute(() -> {
            try {
                FullAccount account = DropboxClient.getClient(token).users().getCurrentAccount();
                //Print account's info
                Log.d("User", account.getEmail());
                Log.d("User", account.getName().getDisplayName());
                Log.d("User", account.getAccountType().name());
                dropboxPrefs.edit().putString(FgConst.PREF_DROPBOX_ACCOUNT, account.getEmail()).apply();
                mUiHandler.post(() -> {
                    mEditTextDropboxAccount.setText(account.getEmail());
                });
            } catch (InvalidAccessTokenException e) {
                dropboxPrefs.edit().remove("dropbox-token").apply();
                dropboxPrefs.edit().remove(FgConst.PREF_DROPBOX_ACCOUNT).apply();
                mUiHandler.post(() -> {
                    mEditTextDropboxAccount.setText("");
                    initDropbox();
                });
            } catch (DbxException e) {
                e.printStackTrace();
                Log.d(TAG, "Error receiving account details.");
            }
        });
    }

    private void initFabMenu() {
        mFabMenuController = new FabMenuController(fabMenuButtonRoot, fabBGLayout, this, mFabBackupLayout, mFabRestoreLayout, mFabRestoreFromDropboxLayout, mFabBackupToOriginDBLayout);
        fabBackup.setOnClickListener(v -> {
            backupDB();
            mFabMenuController.closeFABMenu();
        });
        fabRestore.setOnClickListener(v -> {
            restoreDB();
            mFabMenuController.closeFABMenu();
        });
        fabRestoreFromDropbox.setOnClickListener(v -> {
            restoreDBFromDropbox();
            mFabMenuController.closeFABMenu();
        });
        fabBackupToOrigin.setOnClickListener(v -> {
            backupToOriginDB();
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

    void uploadBackupToDropbox(@NonNull DocumentFile zip) {
        SharedPreferences dropboxPrefs = getSharedPreferences("com.yoshione.fingen.dropbox", Context.MODE_PRIVATE);
        String token = dropboxPrefs.getString("dropbox-token", null);

        if (token != null && zip.exists()) {
            mExecutor.execute( () -> {
                InputStream inputStream = null;
                try {
                    inputStream = getContentResolver().openInputStream(zip.getUri());
                } catch (FileNotFoundException e) {
                    e.printStackTrace();
                    mUiHandler.post(() ->{
                        Toast.makeText(this, String.format("Failed to upload to DropBox: %s", e.getClass()), Toast.LENGTH_SHORT).show();
                    });
                }
                try {
                    UploadTask.Upload(DropboxClient.getClient(token), inputStream, zip.getName());
                    mUiHandler.post(()->{
                        Toast.makeText(this, "Backup uploaded successfully", Toast.LENGTH_SHORT).show();
                        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(ActivityBackup.this);
                        preferences.edit().putLong(FgConst.PREF_SHOW_LAST_SUCCESFUL_BACKUP_TO_DROPBOX, new Date().getTime()).apply();
                        initLastDropboxBackupField();
                    });
                } catch (IOException|DbxException e) {
                    e.printStackTrace();
                    mUiHandler.post(() ->{
                        Toast.makeText(ActivityBackup.this, String.format("Failed to upload backup: %s", e.getClass()), Toast.LENGTH_SHORT).show();
                    });
                }
            });
        } else if (zip.exists()) {
            Toast.makeText(this, "Local backup succeeded.", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this, "Local backup failed.", Toast.LENGTH_SHORT).show();
        }
    }

    void backupDB() {
        DocumentFile backupFolder = ensureManagedFolder();
        if (backupFolder == null) {
            Log.d(TAG, "No saved document folder or it's invalid");
            return;
        }
        try {
            DocumentFile zip = DBHelper.getInstance(getApplicationContext()).backupDB(true, backupFolder);
            if (zip == null) {
                throw new FileNotFoundException("Cannot create backup zip");
            }
            uploadBackupToDropbox(zip);
        } catch (IOException e) {
            Toast.makeText(this, "Failed to save backup", Toast.LENGTH_SHORT).show();
            e.printStackTrace();
        }
    }

    void restoreDB() {
        DocumentFile managedFolder = null;
        try {
            managedFolder = FileUtils.getSavedManagedFolder(this);
        } catch (IOException ignored) {}
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/zip");
        intent.putExtra(Intent.EXTRA_TITLE, "Backup file for Fingen");
        if (managedFolder != null) {
            intent.putExtra(DocumentsContract.EXTRA_INITIAL_URI, managedFolder.getUri());
        }
        mBackupFileChooser.launch(intent);
    }

    DocumentFile ensureManagedFolder() {
        try {
            return FileUtils.getSavedManagedFolder(this);
        } catch (IOException e) {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
            intent.putExtra(Intent.EXTRA_TITLE, "Backup folder for Fingen");
            mSharedFolderChooser.launch(intent);
        }
        return null;
    }

    void restoreDBFromDropbox() {
        ensureManagedFolder();

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
                Log.e(TAG, "Error read list of files from Dropbox");
                e.printStackTrace();
                Toast.makeText(this, "Failed to read list of files from Dropbox", Toast.LENGTH_SHORT).show();
            }
            mHandler.sendMessage(mHandler.obtainMessage(MSG_SHOW_DIALOG, items));
        });
        t.start();
    }

    void backupToOriginDB() {
        DocumentFile backupFolder = ensureManagedFolder();
        if (backupFolder == null) {
            Log.d(TAG, "No saved document folder or it's invalid");
            return;
        }
        try {
            DocumentFile zip = DBHelper.getInstance(getApplicationContext()).backupToOriginDB(true, backupFolder);
            if (zip == null) {
                throw new FileNotFoundException("Could not create zip backup");
            }
            uploadBackupToDropbox(zip);
        } catch (IOException e) {
            Toast.makeText(this, "Failed to save backup", Toast.LENGTH_SHORT).show();
            e.printStackTrace();
        }
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
                            } catch (DbxException|IOException e) {
                                e.printStackTrace();
                            }
                        });
                        thread.start();
                    });
                    builderSingle.show();
                    break;
                case MSG_DOWNLOAD_FILE:
                    File file = (File)msg.obj;
                    DBHelper.getInstance(activity).showRestoreDialog((dialogInterface, which) -> DBHelper.getInstance(activity).restoreDBFromFile(file, activity), activity);
            }

        }
    }

    private class MetadataItem {
        private final FileMetadata mMetadata;

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
            DocumentFile backupFolder = ensureManagedFolder();
            AlertDialog.Builder builderSingle = new AlertDialog.Builder(activity);
            builderSingle.setTitle(activity.getResources().getString(R.string.ttl_select_db_file));
            List<DocumentFile> files = FileUtils.getListFiles(backupFolder, ".zip");
            List<String> names = new ArrayList<>();
            String path;
            for (int i = files.size() - 1; i >= 0; i--) {
                path = files.get(i).getName();
                if (path != null) {
                    names.add(path.substring(path.lastIndexOf("/") + 1));
                }
            }

            final ArrayAdapter<String> arrayAdapter = new ArrayAdapter<>(activity, android.R.layout.select_dialog_singlechoice);
            arrayAdapter.addAll(names);

            builderSingle.setNegativeButton(
                    activity.getResources().getString(android.R.string.cancel),
                    (dialog, which) -> dialog.dismiss());

            builderSingle.setAdapter(arrayAdapter, (dialog, which) -> {
                ListView lw = ((AlertDialog) dialog).getListView();
                String fileName = (String) lw.getAdapter().getItem(which);
                final String finalBackupName = backupFolder.getUri().buildUpon().appendPath(fileName).build().toString();

            });
            builderSingle.show();
        }
    }
}
