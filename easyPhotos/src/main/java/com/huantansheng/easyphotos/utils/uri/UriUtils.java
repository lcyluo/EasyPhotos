package com.huantansheng.easyphotos.utils.uri;

import android.annotation.SuppressLint;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.DocumentsContract;
import android.provider.MediaStore;
import android.util.Log;

import androidx.core.content.FileProvider;

import com.huantansheng.easyphotos.setting.Setting;

import java.io.File;
import java.util.Locale;

public class UriUtils {
    public static Uri getUri(Context cxt, File file) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            if (null == Setting.fileProviderAuthority) {
                throw new NullPointerException("Setting.fileProviderAuthority must not be null.");
            }
            return FileProvider.getUriForFile(cxt, Setting.fileProviderAuthority, file);
        } else {
            return Uri.fromFile(file);
        }
    }

    private static final String TAG = "UriUtils";

    @SuppressLint("NewApi")
    public static String getPathByUri(Context context, Uri uri) {
        final boolean isKitKat = Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT;
        // DocumentProvider
        if (isKitKat && DocumentsContract.isDocumentUri(context, uri)) {
            if (isExternalStorageDocument(uri)) {
                final String docId = DocumentsContract.getDocumentId(uri);
                final String[] split = docId.split(":");
                final String type = split[0];

                if ("primary".equalsIgnoreCase(type)) {
                    if (android.os.Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                        return Environment.getExternalStorageDirectory() + "/" + split[1];
                    } else {
                        return context.getExternalFilesDir(Environment.DIRECTORY_PICTURES) + "/" + split[1];
                    }
                }
            }
            // DownloadsProvider
            else if (isDownloadsDocument(uri)) {
                final String id = DocumentsContract.getDocumentId(uri);
                final Uri contentUri = ContentUris.withAppendedId(
                        Uri.parse("content://downloads/public_downloads"), Long.valueOf(id));
                return getDataColumn(context, contentUri, null, null);
            }
            // MediaProvider
            else if (isMediaDocument(uri)) {
                final String docId = DocumentsContract.getDocumentId(uri);
                final String[] split = docId.split(":");
                final String type = split[0];

                Uri contentUri = null;
                if ("image".equals(type)) {
                    contentUri = MediaStore.Images.Media.getContentUri("external");
                } else if ("video".equals(type)) {
                    contentUri = MediaStore.Video.Media.getContentUri("external");
                } else if ("audio".equals(type)) {
                    contentUri = MediaStore.Audio.Media.getContentUri("external");
                }
                final String selection = "_id=?";
                final String[] selectionArgs = new String[]{split[1]};
                return getDataColumn(context, contentUri, selection, selectionArgs);
            }
        }
        // MediaStore (and general)
        else if ("content".equalsIgnoreCase(uri.getScheme())) {
            // Return the remote address
            if (isGooglePhotosUri(uri)) {
                return uri.getLastPathSegment();
            }
            return getDataColumn(context, uri, null, null);
        }
        // File
        else if ("file".equalsIgnoreCase(uri.getScheme())) {
            return uri.getPath();
        }
        return null;
    }

    private static String getDataColumn(Context context, Uri uri, String selection, String[] selectionArgs) {
        Cursor cursor = null;
        final String column = "_data";
        final String[] projection = {column};
        try {
            cursor = context.getContentResolver().query(uri, projection, selection, selectionArgs,
                    null);
            if (cursor != null && cursor.moveToFirst()) {
                final int column_index = cursor.getColumnIndexOrThrow(column);
                return cursor.getString(column_index);
            }
        } catch (IllegalArgumentException ex) {
            Log.i(TAG, String.format(Locale.getDefault(), "getDataColumn: _data - [%s]", ex.getMessage()));
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        return null;
    }

    /**
     * @param uri The Uri to check.
     * @return Whether the Uri authority is ExternalStorageProvider.
     */
    private static boolean isExternalStorageDocument(Uri uri) {
        return "com.android.externalstorage.documents".equals(uri.getAuthority());
    }

    /**
     * @param uri The Uri to check.
     * @return Whether the Uri authority is DownloadsProvider.
     */
    private static boolean isDownloadsDocument(Uri uri) {
        return "com.android.providers.downloads.documents".equals(uri.getAuthority());
    }

    /**
     * @param uri The Uri to check.
     * @return Whether the Uri authority is MediaProvider.
     */
    private static boolean isMediaDocument(Uri uri) {
        return "com.android.providers.media.documents".equals(uri.getAuthority());
    }

    /**
     * 判断是否是Google相册的图片，类似于content://com.google.android.apps.photos.content/...
     **/
    public static boolean isGooglePhotosUri(Uri uri) {
        return "com.google.android.apps.photos.content".equals(uri.getAuthority());
    }

    /**
     * 判断是否是Google相册的图片，类似于content://com.google.android.apps.photos.contentprovider/0/1/mediakey:/local%3A821abd2f-9f8c-4931-bbe9-a975d1f5fabc/ORIGINAL/NONE/1075342619
     **/
    public static boolean isGooglePlayPhotosUri(Uri uri) {
        return "com.google.android.apps.photos.contentprovider".equals(uri.getAuthority());
    }

    /**
     * 图片路径转uri
     */
    public static Uri getUriByPath(Context context, String path) {
        ContentResolver contentResolver = context.getContentResolver();
        Uri contentUri = MediaStore.Files.getContentUri("external");
        Cursor cursor = contentResolver.query(contentUri, new String[]{MediaStore.Files.FileColumns._ID},
                MediaStore.Files.FileColumns.DATA + "=? ", new String[]{path},
                null);
        if (cursor != null && cursor.moveToFirst()) {
            int index = cursor.getColumnIndex(MediaStore.MediaColumns._ID);
            int id = cursor.getInt(index);
            cursor.close();
            return Uri.withAppendedPath(contentUri, "" + id);
        } else {
            if (new File(path).exists()) {
                ContentValues values = new ContentValues();
                values.put(MediaStore.Images.Media.DATA, path);
                return contentResolver.insert(contentUri, values);
            } else {
                return null;
            }
        }
    }

    public static Uri getImageContentUri(Context context, String absPath) {
        try (Cursor cursor = context.getContentResolver().query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                , new String[]{MediaStore.Images.Media._ID}
                , MediaStore.Images.Media.DATA + "=? "
                , new String[]{absPath}, null)) {

            if (cursor != null && cursor.moveToFirst()) {
                int index = cursor.getColumnIndex(MediaStore.MediaColumns._ID);
                int id = cursor.getInt(index);
                return Uri.withAppendedPath(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, Integer.toString(id));
            } else if (!absPath.isEmpty()) {
                ContentValues values = new ContentValues();
                values.put(MediaStore.Images.Media.DATA, absPath);
                return context.getContentResolver().insert(
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    public static Uri getVideoContentUri(Context context, String absPath) {
        Cursor cursor = null;
        try {
            cursor = context.getContentResolver().query(
                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                    , new String[]{MediaStore.Video.Media._ID}
                    , MediaStore.Video.Media.DATA + "=? "
                    , new String[]{absPath}, null);
            if (cursor != null && cursor.moveToFirst()) {
                int index = cursor.getColumnIndex(MediaStore.Video.Media._ID);
                int id = cursor.getInt(index);
                return Uri.withAppendedPath(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, Integer.toString(id));
            } else if (!absPath.isEmpty()) {
                ContentValues values = new ContentValues();
                values.put(MediaStore.Video.Media.DATA, absPath);
                return context.getContentResolver().insert(
                        MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values);
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        return null;
    }

    /**
     * 创建图片地址uri,用于保存拍照后的照片 Android 10以后使用这种方法
     */
    public static Uri createImageUri(Context context) {
        //设置保存参数到ContentValues中
        ContentValues contentValues = new ContentValues();
        //设置文件类型
        if (Setting.isOnlyVideo()) {
            contentValues.put(MediaStore.Video.Media.DISPLAY_NAME, String.valueOf(System.currentTimeMillis()));
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                contentValues.put(MediaStore.Video.Media.RELATIVE_PATH, "DCIM");
            }
            contentValues.put(MediaStore.Video.Media.MIME_TYPE, "video/mp4");
        } else {
            //设置文件名
            contentValues.put(MediaStore.Images.Media.DISPLAY_NAME, String.valueOf(System.currentTimeMillis()));
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                //android Q中不再使用DATA字段，而用RELATIVE_PATH代替
                //RELATIVE_PATH是相对路径不是绝对路径;照片存储的地方为：存储/Pictures
                contentValues.put(MediaStore.Images.Media.RELATIVE_PATH, "DCIM/Camera");
            }
            contentValues.put(MediaStore.Images.Media.MIME_TYPE, "image/JPEG");
        }
        //执行insert操作，向系统文件夹中添加文件
        //EXTERNAL_CONTENT_URI代表外部存储器，该值不变
        return context.getContentResolver().insert(Setting.isOnlyVideo() ? MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                : MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues);
    }

}