package com.yoshione.fingen.dropbox;

import android.util.Log;

import com.dropbox.core.DbxException;
import com.dropbox.core.v2.DbxClientV2;
import com.dropbox.core.v2.files.WriteMode;
import java.io.IOException;
import java.io.InputStream;

/**
 * Created by slv on 01.11.2016.
 * a
 */

public class UploadTask {
    public static void Upload(DbxClientV2 dbxClient, InputStream inputStream, String fileName) throws IOException,DbxException {
            // Upload to dropbox
        dbxClient.files().uploadBuilder("/" + fileName) //Path in the user's Dropbox to save the file.
                .withMode(WriteMode.OVERWRITE) //always overwrite existing file
                .uploadAndFinish(inputStream);
        Log.d("Upload Status", "Success");
    }
}

