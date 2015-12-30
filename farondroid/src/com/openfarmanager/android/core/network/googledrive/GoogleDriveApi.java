package com.openfarmanager.android.core.network.googledrive;

import android.database.Cursor;

import com.openfarmanager.android.App;
import com.openfarmanager.android.R;
import com.openfarmanager.android.core.DataStorageHelper;
import com.openfarmanager.android.core.dbadapters.NetworkAccountDbAdapter;
import com.openfarmanager.android.core.network.NetworkApi;
import com.openfarmanager.android.filesystem.FileProxy;
import com.openfarmanager.android.filesystem.FileSystemScanner;
import com.openfarmanager.android.filesystem.GoogleDriveFile;
import com.openfarmanager.android.googledrive.api.GoogleDriveWebApi;
import com.openfarmanager.android.googledrive.model.About;
import com.openfarmanager.android.googledrive.model.File;
import com.openfarmanager.android.googledrive.model.Token;
import com.openfarmanager.android.model.NetworkAccount;
import com.openfarmanager.android.model.NetworkEnum;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.openfarmanager.android.utils.Extensions.isNullOrEmpty;

/**
 * author: Vlad Namashko
 */
public class GoogleDriveApi implements NetworkApi {

    private static String ACCESS_TOKEN = "access_token";
    private static String REFRESH_TOKEN = "refresh_token";
    private static String PERMISSION_ID = "permission_id";

    private GoogleDriveWebApi mDriveApi;
    private HashMap<String, String> mFoldersAliases = new HashMap<String, String>();
    private GoogleDriveAccount mCurrentAccount;

    private final static byte[] BUFFER = new byte[512 * 1024];

    public GoogleDriveApi() {
        mDriveApi = new GoogleDriveWebApi();
    }

    public HashMap<String, String> getFoldersAliases() {
        return mFoldersAliases;
    }

    public String findInPathAliases(String path) {
        for (Map.Entry<String, String> fileAlias : mFoldersAliases.entrySet()) {
            if (fileAlias.getValue().equals(path)) {
                return fileAlias.getKey();
            }
        }

        return null;
    }

    public String findPathId(String path) {
        if (isNullOrEmpty(path)) {
            path = "/";
        }

        if (path.endsWith("/") && !path.equals("/")) {
            path = path.substring(0, path.length() - 1);
        }

        String findResult = findInPathAliases(path);

        return findResult == null ? path : findResult;
    }

    public NetworkAccount saveAccount(About about, Token token) {
        JSONObject authData = new JSONObject();
        try {
            authData.put(ACCESS_TOKEN, token.getAccessToken());
            authData.put(REFRESH_TOKEN, token.getRefreshToken());
            authData.put(PERMISSION_ID, about.getPermissionId());
            long id = NetworkAccountDbAdapter.insert(about.getDisplayName(), NetworkEnum.GoogleDrive.ordinal(), authData.toString());

            Cursor cursor = NetworkAccountDbAdapter.getAccountById(id);
            if (cursor != null) {
                int idxId = cursor.getColumnIndex(NetworkAccountDbAdapter.Columns.ID);
                int idxUserName = cursor.getColumnIndex(NetworkAccountDbAdapter.Columns.USER_NAME);
                int idxAuthData = cursor.getColumnIndex(NetworkAccountDbAdapter.Columns.AUTH_DATA);

                cursor.moveToNext();
                String authDataString = cursor.getString(idxAuthData);
                JSONObject data = new JSONObject(authDataString);
                return new GoogleDriveAccount(cursor.getLong(idxId), cursor.getString(idxUserName),
                        Token.fromLocalData(data.getString(ACCESS_TOKEN), data.getString(REFRESH_TOKEN)),
                            data.getString(PERMISSION_ID));
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }

        return null;
    }

    @Override
    public int getAuthorizedAccountsCount() {
        return NetworkAccountDbAdapter.count(NetworkEnum.GoogleDrive.ordinal());
    }

    @Override
    public List<NetworkAccount> getAuthorizedAccounts() {
        List<NetworkAccount> accounts = new ArrayList<NetworkAccount>();
        Cursor cursor = NetworkAccountDbAdapter.getAccounts(NetworkEnum.GoogleDrive.ordinal());

        if (cursor == null) {
            return accounts;
        }

        try {
            int idxId = cursor.getColumnIndex(NetworkAccountDbAdapter.Columns.ID);
            int idxUserName = cursor.getColumnIndex(NetworkAccountDbAdapter.Columns.USER_NAME);
            int idxAuthData = cursor.getColumnIndex(NetworkAccountDbAdapter.Columns.AUTH_DATA);

            while(cursor.moveToNext()) {
                String authData = cursor.getString(idxAuthData);
                try {
                    JSONObject data = new JSONObject(authData);
                    GoogleDriveAccount account = new GoogleDriveAccount(cursor.getLong(idxId), cursor.getString(idxUserName),
                            Token.fromLocalData(data.getString(ACCESS_TOKEN), data.getString(REFRESH_TOKEN)), data.getString(PERMISSION_ID));
                    accounts.add(account);
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }

        } finally {
            cursor.close();
            DataStorageHelper.closeDatabase();
        }

        return accounts;
    }

    public void setup(GoogleDriveAccount account) {
        mCurrentAccount = account;
        mDriveApi.setupToken(account.getToken());
    }

    public void upload(String parentId, String title, java.io.File file, GoogleDriveWebApi.UploadListener listener) {
        mDriveApi.upload(parentId, title, file, listener);
    }

    public void download(FileProxy source, OutputStream outputStream) throws IOException {
        download(source, outputStream, BUFFER);
    }

    public void download(FileProxy source, OutputStream outputStream, byte[] buffer) throws IOException {
        BufferedInputStream inputStream = new BufferedInputStream(
                mDriveApi.download(((GoogleDriveFile) source).getDownloadLink()));
        int len;
        while ((len = inputStream.read(buffer)) > 0) {
            outputStream.write(buffer, 0, len);
        }
        inputStream.close();
        outputStream.close();
    }

    public void download(String downloadLink, String destination) throws IOException {
        BufferedInputStream inputStream = new BufferedInputStream(mDriveApi.download(downloadLink));
        int len;
        OutputStream outputStream = new FileOutputStream(destination);
        while ((len = inputStream.read(BUFFER)) > 0) {
            outputStream.write(BUFFER, 0, len);
        }
        inputStream.close();
        outputStream.close();
    }

    public String getDownloadLink(FileProxy file) {
        return mDriveApi.getDownloadLink(((GoogleDriveFile) file).getDownloadLink());
    }

    public List<FileProxy> getDirectoryFiles(String path) {
        List<FileProxy> list = new ArrayList<FileProxy>();

        try {
            List<File> files = mDriveApi.listFiles(path);

            for (File file : files) {
                list.add(new GoogleDriveFile(file, path));
            }

            if (files.size() > 0 && path.equals("/")) {
                mFoldersAliases.put(files.get(0).getParentPath(), "/");
            }

            if (path.equals("/") || mFoldersAliases.get(path).equals("/")) {
                list.add(new GoogleDriveFile(File.createSharedFolder(), path));
            }

            FileSystemScanner.sInstance.sort(list);

        } catch (Exception e) {
            e.printStackTrace();
        }
        return list;
    }

    @Override
    public NetworkAccount newAccount() {
        return new GoogleDriveAccount(-1, App.sInstance.getResources().getString(R.string.btn_new), null, null);
    }

    @Override
    public NetworkAccount getCurrentNetworkAccount() {
        return mCurrentAccount;
    }

    @Override
    public void delete(FileProxy file) throws Exception {
        if (!mDriveApi.delete(file.getFullPath())) {
            throw new RuntimeException("Can't delete selected file");
        }
    }

    @Override
    public boolean createDirectory(String path) throws Exception {
        File createdFolder;
        String currentDir = path.substring(0, path.lastIndexOf("/"));
        String name = path.substring(path.lastIndexOf("/") + 1, path.length());
        try {
            createdFolder = mDriveApi.createDirectory(name, findPathId(currentDir));

            if (createdFolder != null) {
                mFoldersAliases.put(createdFolder.getId(), path);
                return true;
            }
        } catch (Exception ignore) {
        }

        return false;
    }

    @Override
    public List<FileProxy> search(String path, String query) {
        List<FileProxy> list = new ArrayList<FileProxy>();

        try {
            List<File> files = mDriveApi.search(query);

            for (File file : files) {
                list.add(new GoogleDriveFile(file, path));
            }

            if (files.size() > 0 && path.equals("/")) {
                mFoldersAliases.put(files.get(0).getParentPath(), "/");
            }

            FileSystemScanner.sInstance.sort(list);

        } catch (Exception e) {
            e.printStackTrace();
        }
        return list;
    }

    @Override
    public boolean rename(FileProxy srcFile, String name) throws Exception {
        return mDriveApi.rename(srcFile.getFullPath(), name.substring(name.lastIndexOf("/") + 1, name.length()));
    }

    public static class GoogleDriveAccount extends NetworkAccount {

        private Token mToken;
        private String mPermissionId;

        public GoogleDriveAccount(long id, String userName, Token token, String permissionId) {
            mId = id;
            mUserName = userName;
            mToken = token;
            mPermissionId = permissionId;
        }

        public Token getToken() {
            return mToken;
        }

        public String getPermissionId() {
            return mPermissionId;
        }

    }
}
