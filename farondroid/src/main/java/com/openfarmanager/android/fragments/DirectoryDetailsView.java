package com.openfarmanager.android.fragments;

import android.os.AsyncTask;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import com.openfarmanager.android.App;
import com.openfarmanager.android.R;
import com.openfarmanager.android.utils.CustomFormatter;
import com.openfarmanager.android.utils.FileUtilsExt;

import java.io.File;

/**
 * author: vnamashko
 */
public class DirectoryDetailsView extends BasePanel {

    private View mRootView;
    private View mProgress;
    private File mSelectedFile;

    private LoadDataTask mLoadDataTask;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        mRootView = inflater.inflate(R.layout.directory_details, container, false);
        mProgress = mRootView.findViewById(R.id.loading);
        postInitialization();
        return mRootView;
    }

    public void selectFile(final File file) {
        if (!mIsInitialized || mRootView == null || getActivity() == null || getActivity().isFinishing()) {
            addToPendingList(new Runnable() {
                @Override
                public void run() {
                    selectFile(file);
                }
            });
            return;
        }

        if (file == null) {
            return;
        }

        mRootView.findViewById(R.id.current_path).setBackgroundColor(App.sInstance.getSettings().getMainPanelColor());

        mSelectedFile = file;
        ((TextView) mRootView.findViewById(R.id.folder_name)).setText(getSafeString(R.string.quick_view_folder,
                mSelectedFile != null ? mSelectedFile.getName() : ""));
        ((TextView) mRootView.findViewById(R.id.quick_view_folders)).setText("");
        ((TextView) mRootView.findViewById(R.id.quick_view_files)).setText("");
        ((TextView) mRootView.findViewById(R.id.quick_view_size)).setText("");
        mRootView.findViewById(R.id.error).setVisibility(View.GONE);

        if (mLoadDataTask != null) {
            mLoadDataTask.cancel(true);
            mLoadDataTask = null;
        }

        // virtual directory
        if (!file.exists()) {
            ((TextView) mRootView.findViewById(R.id.folder_name)).setText(R.string.virtual_folder);
            return;
        }

        if (App.sInstance.getFileSystemController().getActivePanel() instanceof ArchivePanel) {
            ((TextView) mRootView.findViewById(R.id.folder_name)).setText(R.string.archive);
            return;
        }

        mLoadDataTask = new LoadDataTask();
        //noinspection unchecked
        mLoadDataTask.execute();
    }

    private class LoadDataTask extends AsyncTask<Void, Void, FileUtilsExt.DirectoryScanResult> {

        private Exception mExecutionException;

        @Override
        protected FileUtilsExt.DirectoryScanResult doInBackground(Void... params) {
            try {
                return FileUtilsExt.getDirectoryDetails(mSelectedFile);
            } catch (Exception e) {
                mExecutionException = e;
            }

            return null;
        }

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            mProgress.setVisibility(View.VISIBLE);
        }

        @Override
        protected void onPostExecute(FileUtilsExt.DirectoryScanResult result) {
            mProgress.setVisibility(View.GONE);
            if (mExecutionException != null) {
                TextView error = ((TextView) mRootView.findViewById(R.id.error));
                error.setText(getSafeString(R.string.error_quick_view_error_while_calculating_detailes, mExecutionException.getLocalizedMessage()));
                error.setVisibility(View.VISIBLE);
            } else {
                ((TextView) mRootView.findViewById(R.id.quick_view_folders)).setText(Long.toString(result.directories));
                ((TextView) mRootView.findViewById(R.id.quick_view_files)).setText(Long.toString(result.files));
                ((TextView) mRootView.findViewById(R.id.quick_view_size)).setText(CustomFormatter.formatBytes(result.filesSize));
            }
        }


    }
}
