package com.openfarmanager.android.filesystem.actions;

import android.net.Uri;
import android.support.v4.app.FragmentManager;

import com.openfarmanager.android.model.TaskStatusEnum;
import com.openfarmanager.android.model.exeptions.SdcardPermissionException;
import com.openfarmanager.android.utils.FileUtilsExt;
import com.openfarmanager.android.utils.StorageUtils;
import com.openfarmanager.android.utils.SystemUtils;

import org.apache.commons.io.FileExistsException;
import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

import static com.openfarmanager.android.model.TaskStatusEnum.*;
import static com.openfarmanager.android.utils.StorageUtils.checkForPermissionAndGetBaseUri;
import static com.openfarmanager.android.utils.StorageUtils.checkUseStorageApi;

/**
 * author: vnamashko
 */
public class MoveTask extends FileActionTask {

    protected File mDestinationFolder;
    protected String mDestinationFileName;
    
    public MoveTask(FragmentManager fragmentManager, OnActionListener listener, List<File> items, File destination, 
                    String destinationFileName) {
        super(fragmentManager, listener, items);
        mDestinationFolder = destination;
        mDestinationFileName = destinationFileName;
    }

    @Override
    protected TaskStatusEnum doInBackground(Void... voids) {

        if (mItems.size() < 1) {
            return OK;
        }

        File srcFile = mItems.get(0); // first file from src folder
        boolean theSameFolders = FileUtilsExt.isTheSameFolders(mItems, mDestinationFolder);
        if (mItems.size() == 1 && theSameFolders) {
            // move inside the same folder, perform rename

            if (mDestinationFileName == null || mDestinationFileName.trim().equals("")) {
                return ERROR_WRONG_DESTINATION_FILE_NAME;
            }

            return new RenameTask(srcFile, mDestinationFileName).execute();
        } else if (theSameFolders) {
            return OK;
        }

        String sdCardPath = SystemUtils.getExternalStorage(mDestinationFolder.getAbsolutePath());
        boolean useStorageApi = checkUseStorageApi(sdCardPath);
        Uri baseUri = null;
        try {
            if (useStorageApi) {
                baseUri = checkForPermissionAndGetBaseUri();
            }
        } catch (SdcardPermissionException e) {
            return ERROR_STORAGE_PERMISSION_REQUIRED;
        }

        // TODO: temporary
        List<File> items = new ArrayList<>(mItems);
        for (File file : items) {
            try {
                doneSize += FileUtils.sizeOf(file);
                if (useStorageApi) {
                    moveOnSdcard(sdCardPath, baseUri, file, mDestinationFolder);
                } else if (!mDestinationFolder.canWrite() || !file.getParentFile().canWrite()) {
                    if (!RootTask.move(file, mDestinationFolder)) {
                        throw new IOException("Cannot move file to " + mDestinationFolder.getAbsolutePath());
                    }
                } else {
                    FileUtils.moveToDirectory(file, mDestinationFolder, false);
                }
                updateProgress();
            } catch (NullPointerException e) {
                return ERROR_FILE_NOT_EXISTS;
            } catch (FileExistsException e) {
                return ERROR_FILE_EXISTS;
            } catch (IllegalArgumentException e) {
                return ERROR_MOVE_FILE;
            } catch (IOException e) {
                return ERROR_MOVE_FILE;
            }
        }

        return OK;
    }

    private void moveOnSdcard(String sdCardPath, Uri baseUri, File source, File destination) throws IOException {
        if (source.isDirectory()) {
            File destinationFolder = new File(destination, source.getName());
            StorageUtils.mkDir(baseUri, sdCardPath, destinationFolder);
            for (File child : source.listFiles()) {
                moveOnSdcard(sdCardPath, baseUri, child, destinationFolder);
            }
        } else {
            moveFileRoutine(sdCardPath, baseUri, source, destination);
        }
        StorageUtils.delete(baseUri, sdCardPath, source.getAbsolutePath());
    }

    private void moveFileRoutine(String sdCardPath, Uri baseUri, File source, File destination) throws IOException {
        OutputStream out = StorageUtils.getStorageOutputFileStream(
                new File(destination, source.getName()), sdCardPath);
        int len;
        InputStream in = new FileInputStream(source);
        while ((len = in.read(CopyTask.BUFFER)) > 0) {
            out.write(CopyTask.BUFFER, 0, len);
        }
    }

}

