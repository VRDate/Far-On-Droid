package com.openfarmanager.android.filesystem.actions.network;

import android.support.v4.app.FragmentManager;
import com.openfarmanager.android.App;
import com.openfarmanager.android.core.network.NetworkApi;
import com.openfarmanager.android.core.network.dropbox.DropboxAPI;
import com.openfarmanager.android.filesystem.FileProxy;
import com.openfarmanager.android.filesystem.actions.OnActionListener;
import com.openfarmanager.android.fragments.BaseFileSystemPanel;
import com.openfarmanager.android.fragments.NetworkPanel;
import com.openfarmanager.android.model.NetworkEnum;
import com.openfarmanager.android.model.TaskStatusEnum;

import java.util.List;

import static com.openfarmanager.android.model.TaskStatusEnum.*;

/**
 * @author Vlad Namashko
 */
public class MoveFromNetworkTask extends CopyFromNetworkTask {

    public MoveFromNetworkTask(BaseFileSystemPanel panel, List<FileProxy> items, String destination) {
        super(panel, items, destination);
    }

    @Override
    protected TaskStatusEnum doInBackground(Void... voids) {

        // TODO: hack
        mTotalSize = 1;

        if (mItems.size() < 1) {
            return OK;
        }

        TaskStatusEnum copyResult = doCopy();

        NetworkApi api = getApi();
        for (FileProxy file : mItems) {
            try {
                api.delete(file);
            } catch (NullPointerException e) {
                return ERROR_FILE_NOT_EXISTS;
            } catch (Exception e) {
                return ERROR_DELETE_FILE;
            }
        }

        return copyResult;
    }

}
