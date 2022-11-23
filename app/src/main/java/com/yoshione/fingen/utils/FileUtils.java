package com.yoshione.fingen.utils;

import android.Manifest;
import android.app.Activity;
import android.content.Context;;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.documentfile.provider.DocumentFile;
import androidx.preference.PreferenceManager;

import com.github.angads25.filepicker.model.DialogConfigs;
import com.github.angads25.filepicker.model.DialogProperties;
import com.github.angads25.filepicker.view.FilePickerDialog;
import com.yoshione.fingen.FgConst;
import com.yoshione.fingen.R;
import com.yoshione.fingen.interfaces.IOnUnzipComplete;
import com.yoshione.fingen.utils.winzipaes.AesZipFileDecrypter;
import com.yoshione.fingen.utils.winzipaes.AesZipFileEncrypter;
import com.yoshione.fingen.utils.winzipaes.impl.AESDecrypterBC;
import com.yoshione.fingen.utils.winzipaes.impl.AESEncrypterBC;
import com.yoshione.fingen.utils.winzipaes.impl.ExtZipEntry;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

//import de.idyl.winzipaes.AesZipFileEncrypter;
//import com.yoshione.fingen.utils.winzipaes.impl.AESEncrypterBC;

/**
 * Created by Leonid on 06.02.2016.
 */
@SuppressWarnings("TryFinallyCanBeTryWithResources")
public class FileUtils {
    private static final String TAG = "FileUtils";
    private static final int BUFFER_SIZE = 1024;

    public static void zip(Context context, String file, Uri zipFile, String fileRenaming) throws IOException {
        BufferedInputStream origin;
        ZipOutputStream out = new ZipOutputStream(new BufferedOutputStream(context.getContentResolver().openOutputStream(zipFile)));
        try {
            byte[] data = new byte[BUFFER_SIZE];

            FileInputStream fi = new FileInputStream(file);
            origin = new BufferedInputStream(fi, BUFFER_SIZE);
            try {
                ZipEntry entry = new ZipEntry(fileRenaming.isEmpty() ? file.substring(file.lastIndexOf("/") + 1) : fileRenaming);
                out.putNextEntry(entry);
                int count;
                while ((count = origin.read(data, 0, BUFFER_SIZE)) != -1) {
                    out.write(data, 0, count);
                }
            } finally {
                origin.close();
            }
        } finally {
            out.close();
        }
    }

    public static void zipAndEncrypt(Context context, String inFile, Uri zipFile, String password, String fileRenaming) throws IOException {
        AesZipFileEncrypter enc = new AesZipFileEncrypter(context.getContentResolver().openOutputStream(zipFile), new AESEncrypterBC());
        File file = new File(inFile);
        FileInputStream fis = new FileInputStream(file);
        try {
            enc.add(fileRenaming.isEmpty() ? file.getName() : fileRenaming, fis, password);
        } finally {
            fis.close();
            enc.close();
        }
    }

    public static void unzip(@NonNull File zipFile, String outputFile) {
        try {
            ZipInputStream zin = new ZipInputStream(new FileInputStream(zipFile));
            try {
                ZipEntry ze;
                while ((ze = zin.getNextEntry()) != null) {

                    if (ze.isDirectory()) {
                        File unzipFile = new File(outputFile);
                        if (!unzipFile.isDirectory()) {
                            unzipFile.mkdirs();
                        }
                    } else {
                        FileOutputStream fout = new FileOutputStream(outputFile, false);
                        BufferedOutputStream bufout = new BufferedOutputStream(fout);
                        try {

                            byte[] buffer = new byte[2048];
                            int read;
                            while ((read = zin.read(buffer)) != -1) {
                                bufout.write(buffer, 0, read);
                            }
                        } finally {
                            zin.closeEntry();
                            bufout.close();
                            fout.close();
                        }
                    }
                }
            } finally {
                zin.close();
            }
        } catch (Exception e) {
            Log.e(TAG, "Unzip exception", e);
        }
    }

    public static DocumentFile getSavedManagedFolder(Context context) throws IOException {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        String locationString = prefs.getString(FgConst.PREF_MANAGED_LOCATION, "");
        if (locationString.isEmpty()) {
            throw new IOException("No saved location in shared preferences");
        }
        // try parsing it
        Uri location = Uri.parse(locationString).buildUpon().build();
        DocumentFile file = DocumentFile.fromTreeUri(context, location);
        if (file != null && file.exists()) {
            return file;
        }
        throw new IOException("Cannot find file by location");
    }

    // Copy file held by inputStream to outputStream.
    public static boolean copyFileUsingStreams(InputStream inputStream, OutputStream outputStream) throws IOException {
        boolean bSuccess = true;

        try {
            byte[] buffer = new byte[1024 * 16];
            int length;

            while ((length = inputStream.read(buffer)) > 0) {
                outputStream.write(buffer, 0, length);
            }
        } catch (Exception e) {
            bSuccess = false;
        } finally {
            try {
                if (inputStream != null) {
                    inputStream.close();
                }
            } finally {
                if (outputStream != null) {
                    outputStream.close();
                }
            }
        }
        return bSuccess;
    }

    public static void unzipAndDecrypt(@NonNull File zipFile, String location, String password, IOnUnzipComplete onCompleteListener) {
        try {
            AesZipFileDecrypter decrypter = new AesZipFileDecrypter(zipFile, new AESDecrypterBC());
            ExtZipEntry entry = decrypter.getEntry("fingen.db");
            if (entry.isEncrypted()) {
                decrypter.extractEntry(entry, new File(location + "/fingen.db.ex"), password);
            } else {
                unzip(zipFile, location + "/fingen.db.ex");
            }
        } catch (ZipException ze) {
            Log.e(TAG, "Wrong password", ze);
            onCompleteListener.onWrongPassword(zipFile);
            return;
        } catch (Exception e) {
            Log.e(TAG, "Unzip exception", e);
            onCompleteListener.onError(zipFile);
            return;
        }
        onCompleteListener.onComplete(zipFile);
    }

    public static List<DocumentFile> getListFiles(@NonNull DocumentFile parentDir, String ext) {
        ArrayList<DocumentFile> inFiles = new ArrayList<>();

        DocumentFile[] files = parentDir.listFiles();
        for (DocumentFile file : files) {
            if (file.isDirectory()) {
                inFiles.addAll(getListFiles(file, ext));
            } else {
                String filename = file.getName();
                if (filename != null && filename.endsWith(ext)) {
                    inFiles.add(file);
                }
            }
        }
        return inFiles;
    }

    public static void copyFile(String src, String dst) throws IOException {
        try (FileInputStream in = new FileInputStream(src)) {
            try (FileOutputStream out = new FileOutputStream(dst)) {
                byte[] buf = new byte[BUFFER_SIZE];
                int len;
                while ((len = in.read(buf)) > 0) {
                    out.write(buf, 0, len);
                }
            }
        }
    }

    public static void SelectFileFromStorage(Activity activity, int selectionType, final IOnSelectFile onSelectFileListener) {
        final DialogProperties properties = new DialogProperties();
        properties.selection_mode = DialogConfigs.SINGLE_MODE;
        properties.selection_type = selectionType;
        properties.root = new File(DialogConfigs.DEFAULT_DIR);
        properties.error_dir = new File(DialogConfigs.DEFAULT_DIR);
        properties.offset = new File(DialogConfigs.DEFAULT_DIR);
        properties.extensions = new String[]{"csv", "CSV"};

        FilePickerDialog dialog = new FilePickerDialog(activity, properties);
        String title = "";
        title = selectionType == DialogConfigs.FILE_SELECT ? activity.getString(R.string.ttl_select_csv_file) : activity.getString(R.string.ttl_select_export_dir);
        dialog.setTitle(title);
        dialog.setPositiveBtnName("Select");
        dialog.setNegativeBtnName("Cancel");

        dialog.setDialogSelectionListener(files -> {
            if (files.length == 1 && !files[0].isEmpty()) {
                onSelectFileListener.OnSelectFile(files[0]);
            }
        });

        dialog.show();
    }

    public interface IOnSelectFile {
        void OnSelectFile(String FileName);
    }

}
