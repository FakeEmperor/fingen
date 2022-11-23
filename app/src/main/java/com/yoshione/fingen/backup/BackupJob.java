package com.yoshione.fingen.backup;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.documentfile.provider.DocumentFile;
import androidx.preference.PreferenceManager;

import com.dropbox.core.DbxException;
import com.dropbox.core.v2.DbxClientV2;
import com.dropbox.core.v2.files.WriteMode;
import com.evernote.android.job.DailyJob;
import com.evernote.android.job.JobRequest;
import com.yoshione.fingen.BuildConfig;
import com.yoshione.fingen.DBHelper;
import com.yoshione.fingen.FGApplication;
import com.yoshione.fingen.FgConst;
import com.yoshione.fingen.dropbox.DropboxClient;
import com.yoshione.fingen.utils.FileUtils;

import java.io.IOException;
import java.io.InputStream;
import java.util.Date;
import java.util.concurrent.TimeUnit;

/**
 * Created by slv on 27.10.2017.
 * /
 */

public class BackupJob extends DailyJob {

    public static final String TAG = "job_backup_tag";

    public static int schedule() {
        // schedule between 1 and 6 AM
        return DailyJob.schedule(new JobRequest.Builder(TAG),
                TimeUnit.HOURS.toMillis(1) + TimeUnit.MINUTES.toMillis(0),
                TimeUnit.HOURS.toMillis(6) + TimeUnit.MINUTES.toMillis(0));
    }

    @NonNull
    @Override
    protected DailyJobResult onRunDailyJob(@NonNull Params params) {
        if (BuildConfig.FLAVOR.equals("nd")) {return DailyJobResult.CANCEL;}

        Context context = FGApplication.getContext();
        SharedPreferences dropboxPrefs = context.getSharedPreferences("com.yoshione.fingen.dropbox", Context.MODE_PRIVATE);
        String token = dropboxPrefs.getString("dropbox-token", null);
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        // Do the task here
        try {
            DocumentFile backupFolder = FileUtils.getSavedManagedFolder(context);
            DocumentFile zip = DBHelper.getInstance(context).backupDB(true, backupFolder);
            if (token != null && zip.exists()) {
                DbxClientV2 dbxClient = DropboxClient.getClient(token);
                // Upload to Dropbox
                InputStream inputStream = context.getContentResolver().openInputStream(zip.getUri());
                try {
                    dbxClient.files().uploadBuilder("/" + zip.getName()) //Path in the user's Dropbox to save the file.
                            .withMode(WriteMode.OVERWRITE) //always overwrite existing file
                            .uploadAndFinish(inputStream);
                    prefs.edit().putLong(FgConst.PREF_SHOW_LAST_SUCCESFUL_BACKUP_TO_DROPBOX, new Date().getTime()).apply();
                } catch (DbxException e) {
                    e.printStackTrace();
                }
//                Log.d("Upload Status", "Success");

            } else if (!zip.exists()) {
                Log.e(TAG, "Failed to create local Fingen backup");
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return DailyJobResult.SUCCESS;
    }
}
