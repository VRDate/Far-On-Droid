package com.openfarmanager.android.dialogs;

import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Color;
import android.os.AsyncTask;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import com.openfarmanager.android.App;
import com.openfarmanager.android.R;
import com.openfarmanager.android.core.network.NetworkApi;
import com.openfarmanager.android.filesystem.FileProxy;
import com.openfarmanager.android.filesystem.FileSystemFile;
import com.openfarmanager.android.filesystem.search.SearchFilter;
import com.openfarmanager.android.filesystem.search.SearchOptions;
import com.openfarmanager.android.model.NetworkEnum;
import com.openfarmanager.android.view.ToastNotification;

import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOCase;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.Serializable;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.schedulers.Schedulers;

/**
 * Performs search and shows result.
 *
 * @author Vlad Namashko.
 */
public class SearchResultDialog extends Dialog {

    private FileProxy mSelected;
    private ListView mList;
    private View mDialogView;

    private int mSelectedFilePosition = -1;

    private final List<FileProxy> mData = Collections.synchronizedList(new LinkedList<FileProxy>());

    private NetworkScanner mNetworkScanner;
    private ProgressBar mProgressBar;

    private SearchFilter mSearchFilter;

    private NetworkEnum mNetworkType;
    private SearchOptions mSearchOptions;
    private SearchResultListener mListener;
    private String mCurrentDir;

    private CompositeDisposable mSubscription;

    public SearchResultDialog(Context context, NetworkEnum networkType, String currentDir,
                              SearchOptions searchOption, SearchResultListener listener) {
        super(context);
        mNetworkType = networkType;
        mCurrentDir = currentDir;
        mSearchOptions = searchOption;
        mListener = listener;

        mSearchFilter = new SearchFilter(searchOption);
        mSubscription = new CompositeDisposable();
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        requestWindowFeature(Window.FEATURE_NO_TITLE);

        mDialogView = View.inflate(App.sInstance.getApplicationContext(), R.layout.search_result, null);
        setContentView(mDialogView);

        mDialogView.findViewById(R.id.cancel).setOnClickListener(view -> dismiss());

        mDialogView.findViewById(R.id.go_to).setOnClickListener(view -> {
            if (!checkFileSelected()) {
                return;
            }
            mListener.onGotoFile(mSelected);
            dismiss();
        });

        mDialogView.findViewById(R.id.new_search).setOnClickListener(view -> {
            mListener.onResetSearch();
            dismiss();
        });

        mDialogView.findViewById(R.id.view).setOnClickListener(view -> {
            if (!checkFileSelected()) {
                return;
            }
            mListener.onViewFile(mSelected);
            dismiss();
        });

        mDialogView.findViewById(R.id.view).setVisibility(mSearchOptions.isNetworkPanel ? View.GONE : View.VISIBLE);

        setOnDismissListener(dialog -> mSubscription.clear());

        mProgressBar = (ProgressBar) mDialogView.findViewById(android.R.id.progress);

        mList = (ListView) mDialogView.findViewById(android.R.id.list);

        mList.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> adapterView, View view, int i, long l) {
                mSelected = (FileProxy) view.getTag();
            }

            @Override
            public void onNothingSelected(AdapterView<?> adapterView) {
            }
        });

        mList.setOnItemClickListener((adapterView, view, i, l) -> {
            mSelected = (FileProxy) view.getTag();
            mSelectedFilePosition = i;
            ((BaseAdapter) mList.getAdapter()).notifyDataSetChanged();
        });

        mList.setOnItemLongClickListener((adapterView, view, i, l) -> {
            mSelected = (FileProxy) view.getTag();

            if (!checkFileSelected()) {
                return true;
            }
            mListener.onGotoFile(mSelected);
            dismiss();
            return true;
        });

        mList.setAdapter(new BaseAdapter() {
            private List<FileProxy> mData;

            @Override
            public int getCount() {
                return mData == null ? 0 : mData.size();
            }

            @Override
            public Object getItem(int i) {
                return mData.get(i);
            }

            @Override
            public long getItemId(int i) {
                return i;
            }

            @Override
            public View getView(int i, View view, ViewGroup viewGroup) {
                TextView textView;
                if (view == null) {
                    textView = new TextView(getContext());
                    textView.setTextColor(Color.BLACK);
                    textView.setEllipsize(TextUtils.TruncateAt.MIDDLE);
                    textView.setSingleLine();
                    textView.setTextSize(18);
                } else {
                    textView = (TextView) view;
                }
                FileProxy f = (FileProxy) getItem(i);
                textView.setText(f.getFullPathRaw());
                textView.setTag(f);
                textView.setBackgroundResource(mSelectedFilePosition == i ? R.color.selected_item : R.color.main_grey);
                return textView;
            }

            @Override
            public void notifyDataSetChanged() {
                synchronized (SearchResultDialog.this.mData) {
                    mData = new LinkedList<>(SearchResultDialog.this.mData);
                }
                super.notifyDataSetChanged();
            }
        });

        setOnDismissListener(dialog -> mSubscription.clear());

        mProgressBar.setVisibility(View.VISIBLE);
        if (mCurrentDir != null) {
            if (mNetworkType != null) {
                if (mNetworkScanner == null) {
                    mNetworkScanner = new NetworkScanner(mNetworkType);
                    mNetworkScanner.execute(mCurrentDir);
                }
            } else {
                mSearchFilter.searchAsync(mCurrentDir).observeOn(AndroidSchedulers.mainThread()).subscribe(
                        fileProxy -> {
                            mData.add(fileProxy);
                            ((BaseAdapter) mList.getAdapter()).notifyDataSetChanged();
                        },
                        throwable -> {
                        },
                        () -> mProgressBar.setVisibility(View.GONE));
            }
        }
    }

    private boolean checkFileSelected() {
        if (mSelected == null) {
            ToastNotification.makeText(getContext(), App.sInstance.getString(R.string.error_no_selected_file), Toast.LENGTH_SHORT).show();
            return false;
        }

        return true;
    }

    /**
     * Finds files in network storage
     */
    private class NetworkScanner extends AsyncTask<String, Void, List<FileProxy>> {

        private NetworkEnum mNetworkType;

        public NetworkScanner(NetworkEnum networkType) {
            mNetworkType = networkType;
        }

        @Override
        protected void onPreExecute() {
            mProgressBar.setVisibility(View.VISIBLE);
        }

        @Override
        protected List<FileProxy> doInBackground(String ... path) {
            return getNetworkApi().search(path[0], mSearchOptions.fileMask);
        }

        @Override
        protected void onPostExecute(List<FileProxy> searchResult) {
            mData.clear();
            mData.addAll(searchResult);
            ((BaseAdapter) mList.getAdapter()).notifyDataSetChanged();
            mProgressBar.setVisibility(View.INVISIBLE);
        }

        private NetworkApi getNetworkApi() {
            switch (mNetworkType) {
                case Dropbox: default:
                    return App.sInstance.getDropboxApi();
                case SkyDrive:
                    return App.sInstance.getSkyDriveApi();
                case GoogleDrive:
                    return App.sInstance.getGoogleDriveApi();
                case MediaFire:
                    return App.sInstance.getMediaFireApi();
                case WebDav:
                    return App.sInstance.getWebDavApi();
            }
        }
    }

    public interface SearchResultListener extends Serializable {
        void onGotoFile(FileProxy file);
        void onViewFile(FileProxy file);
        void onResetSearch();
    }
}
