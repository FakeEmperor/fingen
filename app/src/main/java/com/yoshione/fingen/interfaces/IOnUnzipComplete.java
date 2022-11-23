package com.yoshione.fingen.interfaces;

import androidx.annotation.NonNull;

import java.io.File;

/**
 * Created by slv on 12.10.2016.
 * I
 */

public interface IOnUnzipComplete {
    void onComplete(@NonNull File zipFile);

    void onError(@NonNull File zipFile);

    void onWrongPassword(@NonNull File zipFile);
}
