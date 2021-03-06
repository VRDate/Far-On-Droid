package com.openfarmanager.android.filesystem.actions;

import android.net.Uri;
import android.provider.DocumentsContract;

import com.openfarmanager.android.App;
import com.openfarmanager.android.model.TaskStatusEnum;
import com.openfarmanager.android.model.exeptions.SdcardPermissionException;
import com.openfarmanager.android.utils.StorageUtils;
import com.openfarmanager.android.utils.SystemUtils;

import java.io.File;

import static com.openfarmanager.android.model.TaskStatusEnum.*;
import static com.openfarmanager.android.utils.StorageUtils.checkForPermissionAndGetBaseUri;
import static com.openfarmanager.android.utils.StorageUtils.checkUseStorageApi;

public class RenameTask {

    private String mDestinationFileName;
    private File mSrcFile;

    public RenameTask(File srcFile, String destinationFileName) {
        mSrcFile = srcFile;
        mDestinationFileName = destinationFileName;
    }

    public TaskStatusEnum execute() {
        if (mDestinationFileName == null || mDestinationFileName.trim().equals("")) {
            return ERROR_WRONG_DESTINATION_FILE_NAME;
        }

        if (mSrcFile == null) {
            return ERROR_RENAME_FILE;
        }

        String sdCardPath = SystemUtils.getExternalStorage(mSrcFile.getAbsolutePath());
        Uri uri = null;
        boolean checkUseStorageApi = checkUseStorageApi(sdCardPath);
        try {
            if (checkUseStorageApi) {
                uri = checkForPermissionAndGetBaseUri();
            }
        } catch (SdcardPermissionException e) {
            return ERROR_STORAGE_PERMISSION_REQUIRED;
        }

        String destinationFilePath = mSrcFile.getParent() + File.separator + mDestinationFileName;
        if (checkUseStorageApi) {
            return DocumentsContract.renameDocument(App.sInstance.getContentResolver(),
                    StorageUtils.getDestinationFileUri(uri, sdCardPath, mSrcFile.getAbsolutePath(), true),
                    mDestinationFileName) != null ? OK : ERROR_RENAME_FILE;
        } else if (mSrcFile.getParentFile().canWrite()) {
            // due to stupid behaviour of 'renameTo' method we will do some tricks
            File newFile = new File(destinationFilePath);
            File tempFile = new File(mSrcFile.getParent() + File.separator + mDestinationFileName + "_____");

            // rename temp file to destination file
            return mSrcFile.renameTo(tempFile) && tempFile.renameTo(newFile) ? OK : ERROR_RENAME_FILE;
        } else {
            return RootTask.rename(mSrcFile.getAbsolutePath(), destinationFilePath) ? OK : ERROR_RENAME_FILE;
        }
    }

}
