package com.openfarmanager.android.model;

import android.content.res.Resources;
import com.openfarmanager.android.App;
import com.openfarmanager.android.R;

/**
 * @author Vlad Namashko
 */
public enum  NetworkEnum {
    Dropbox, SkyDrive, FTP, SMB, YandexDisk, GoogleDrive, MediaFire;

    public static NetworkEnum[] sOrderedItems = {FTP, SMB, Dropbox, GoogleDrive, SkyDrive, MediaFire, YandexDisk};

    public static String getNetworkLabel(NetworkEnum networkEnum) {
        switch (networkEnum) {
            case FTP: return "FTP";
            case SMB: return App.sInstance.getString(R.string.local_network);
            case Dropbox: return "Dropbox";
            case SkyDrive: return "OneDrive";
            case GoogleDrive: return "Google Drive";
            case YandexDisk: return "Yandex Disk";
            case MediaFire: return "MediaFire";
            default: return "Dropbox";
        }
    }

    public static NetworkEnum fromOrdinal(int ordinal) {
        if (ordinal == -1) {
            return null;
        }

        return NetworkEnum.values()[ordinal];
    }

    public static NetworkEnum[] valuesList() {
        return sOrderedItems;
    }
}
