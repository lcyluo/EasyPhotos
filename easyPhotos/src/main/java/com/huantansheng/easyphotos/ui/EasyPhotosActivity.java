package com.huantansheng.easyphotos.ui;

import android.Manifest;
import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.app.Activity;
import android.app.Fragment;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.provider.MediaStore;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.ScaleAnimation;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.IdRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.SimpleItemAnimator;

import com.huantansheng.easyphotos.EasyPhotos;
import com.huantansheng.easyphotos.R;
import com.huantansheng.easyphotos.constant.Code;
import com.huantansheng.easyphotos.constant.Key;
import com.huantansheng.easyphotos.models.ad.AdListener;
import com.huantansheng.easyphotos.models.album.AlbumModel;
import com.huantansheng.easyphotos.models.album.entity.Photo;
import com.huantansheng.easyphotos.result.Result;
import com.huantansheng.easyphotos.setting.Setting;
import com.huantansheng.easyphotos.ui.adapter.AlbumItemsAdapter;
import com.huantansheng.easyphotos.ui.adapter.PhotosAdapter;
import com.huantansheng.easyphotos.ui.dialog.LoadingDialog;
import com.huantansheng.easyphotos.ui.widget.PressedTextView;
import com.huantansheng.easyphotos.utils.BackgroundCallService;
import com.huantansheng.easyphotos.utils.Color.ColorUtils;
import com.huantansheng.easyphotos.utils.String.StringUtils;
import com.huantansheng.easyphotos.utils.bitmap.BitmapUtils;
import com.huantansheng.easyphotos.utils.file.FileUtils;
import com.huantansheng.easyphotos.utils.media.MediaMetadataInfoUtils;
import com.huantansheng.easyphotos.utils.media.MediaScannerConnectionUtils;
import com.huantansheng.easyphotos.utils.permission.PermissionUtil;
import com.huantansheng.easyphotos.utils.settings.SettingsUtils;
import com.huantansheng.easyphotos.utils.system.SystemUtils;
import com.huantansheng.easyphotos.utils.uri.UriUtils;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;

@SuppressWarnings("all")
public class EasyPhotosActivity extends AppCompatActivity implements AlbumItemsAdapter.OnClickListener, PhotosAdapter.OnClickListener, AdListener, View.OnClickListener {

    private File mTempImageFile;

    private AlbumModel albumModel;
    private ArrayList<Object> photoList = new ArrayList<>();
    private ArrayList<Object> albumItemList = new ArrayList<>();

    private ArrayList<Photo> resultList = new ArrayList<>();

    private RecyclerView rvPhotos;
    private PhotosAdapter mPhotosAdapter;
    private GridLayoutManager gridLayoutManager;

    private RecyclerView rvAlbumItems;
    private AlbumItemsAdapter albumItemsAdapter;
    private RelativeLayout rootViewAlbumItems;

    private PressedTextView tvAlbumItems, tvDone, tvPreview;
    private TextView tvOriginal;
    private AnimatorSet setHide;
    private AnimatorSet setShow;

    private int currAlbumItemIndex = 0;

    private ImageView ivCamera;
    private TextView tvTitle;

    private LinearLayout mSecondMenus;

    private RelativeLayout permissionView;
    private TextView tvPermission;
    private View mBottomBar;
    /**
     * 临时资源uri
     */
    private Uri mTempFileUri = null;

    private ServiceConnection xyCallConnection;

    public static void start(Activity activity, int requestCode) {
        Intent intent = new Intent(activity, EasyPhotosActivity.class);
        activity.startActivityForResult(intent, requestCode);
    }

    public static void start(Fragment fragment, int requestCode) {
        Intent intent = new Intent(fragment.getActivity(), EasyPhotosActivity.class);
        fragment.startActivityForResult(intent, requestCode);
    }

    public static void start(androidx.fragment.app.Fragment fragment, int requestCode) {
        Intent intent = new Intent(fragment.getContext(), EasyPhotosActivity.class);
        fragment.startActivityForResult(intent, requestCode);
    }

    LoadingDialog loadingDialog;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_easy_photos);
        hideActionBar();
        adaptationStatusBar();
        loadingDialog = LoadingDialog.get(this);
        if (!Setting.onlyStartCamera && null == Setting.imageEngine) {
            finish();
            return;
        }
        albumModel = AlbumModel.getInstance();
        initSomeViews();
        if (PermissionUtil.checkAndRequestPermissionsInActivity(this, PermissionUtil.getNeedPermissions(this))) {
            hasPermissions();
        } else {
            permissionView.setVisibility(View.VISIBLE);
        }
    }

    private void adaptationStatusBar() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            int statusColor = getWindow().getStatusBarColor();
            if (statusColor == Color.TRANSPARENT) {
                statusColor = ContextCompat.getColor(this, R.color.colorPrimaryDark);
            }
            if (ColorUtils.isWhiteColor(statusColor)) {
                SystemUtils.getInstance().setStatusDark(this, true);
            }
        }
    }

    private void initSomeViews() {
        mBottomBar = findViewById(R.id.m_bottom_bar);
        permissionView = findViewById(R.id.rl_permissions_view);
        tvPermission = findViewById(R.id.tv_permission);
        rootViewAlbumItems = findViewById(R.id.root_view_album_items);
        tvTitle = findViewById(R.id.tv_title);
        if (Setting.isOnlyVideo()) {
            tvTitle.setText(R.string.video_selection_easy_photos);
        }
        findViewById(R.id.iv_second_menu).setVisibility(Setting.showPuzzleMenu || Setting.showCleanMenu || Setting.showOriginalMenu ? View.VISIBLE : View.GONE);
        setClick(R.id.iv_back);
    }

    private void hasPermissions() {
        permissionView.setVisibility(View.GONE);
        if (Setting.onlyStartCamera) {
            launchCamera(Code.REQUEST_CAMERA);
            return;
        }
        AlbumModel.CallBack albumModelCallBack = new AlbumModel.CallBack() {
            @Override
            public void onAlbumWorkedCallBack() {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        loadingDialog.dismiss();
                        onAlbumWorkedDo();
                    }
                });
            }
        };
        loadingDialog.show();
        albumModel.query(this, albumModelCallBack);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull final String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        final boolean isRequestCamera = permissions.length == 1 && Manifest.permission.CAMERA.equals(permissions[0]);
        PermissionUtil.onPermissionResult(this, permissions, grantResults, new PermissionUtil.PermissionCallBack() {
            @Override
            public void onSuccess() {
                if (!isRequestCamera) {
                    hasPermissions();
                } else {
                    // 去拍照
                    launchCamera(Code.REQUEST_CAMERA);
                }
            }

            @Override
            public void onShouldShow() {
                if (!isRequestCamera) {
                    tvPermission.setText(R.string.permissions_again_easy_photos);
                    permissionView.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View view) {
                            if (PermissionUtil.checkAndRequestPermissionsInActivity(EasyPhotosActivity.this, PermissionUtil.getNeedPermissions(EasyPhotosActivity.this))) {
                                hasPermissions();
                            }
                        }
                    });
                } else {
                    Toast.makeText(EasyPhotosActivity.this, "请先允许使用相机拍摄照片和录制视频", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailed() {
                if (!isRequestCamera) {
                    tvPermission.setText(R.string.permissions_die_easy_photos);
                    permissionView.setOnClickListener(new View.OnClickListener() {
                        @Override
                        public void onClick(View view) {
                            SettingsUtils.startMyApplicationDetailsForResult(EasyPhotosActivity.this, getPackageName());
                        }
                    });
                } else {
                    Toast.makeText(EasyPhotosActivity.this, "请在设置中允许使用相机拍摄照片和录制视频", Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    /**
     * 启动相机
     *
     * @param requestCode startActivityForResult的请求码
     */
    private void launchCamera(int requestCode) {
        if (TextUtils.isEmpty(Setting.fileProviderAuthority))
            throw new RuntimeException("AlbumBuilder" + " : 请执行 setFileProviderAuthority()方法");
        if (PermissionUtil.checkAndRequestPermissionsInActivity(this, PermissionUtil.getCameraPermissions())) {
            if (!MediaMetadataInfoUtils.isCameraCanUse()) {
                permissionView.setVisibility(View.VISIBLE);
                tvPermission.setText(R.string.permissions_die_easy_photos);
                permissionView.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        SettingsUtils.startMyApplicationDetailsForResult(EasyPhotosActivity.this, getPackageName());
                    }
                });
                return;
            }
            toAndroidCamera(requestCode);
        } else {
            Toast.makeText(EasyPhotosActivity.this, "请允许使用相机拍摄照片和录制视频", Toast.LENGTH_SHORT).show();
        }
    }

    private void toAndroidCamera(int requestCode) {
        Intent cameraIntent = new Intent(Setting.isOnlyVideo() ? MediaStore.ACTION_VIDEO_CAPTURE : MediaStore.ACTION_IMAGE_CAPTURE);
        if (cameraIntent.resolveActivity(getPackageManager()) != null || this.getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                mTempFileUri = UriUtils.createImageUri(EasyPhotosActivity.this);
            } else {
                mTempImageFile = FileUtils.createCameraTempFile(EasyPhotosActivity.this);
                if (mTempImageFile != null && mTempImageFile.exists()) {
                    mTempFileUri = UriUtils.getUri(this, mTempImageFile);
                }
            }
            if (mTempFileUri != null) {
                cameraIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                cameraIntent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                cameraIntent.putExtra(MediaStore.EXTRA_OUTPUT, mTempFileUri);

                //  若是录制视频并且限制时长大于0，则限制录制时长
                if (Setting.isOnlyVideo() && Setting.videoRecordLimitTime > 0) {
                    cameraIntent.putExtra(MediaStore.EXTRA_DURATION_LIMIT, Setting.videoRecordLimitTime);
                }
                //  设定摄像头
                if (Setting.cameraFacing != -1) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1 && Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
                        cameraIntent.putExtra("android.intent.extras.CAMERA_FACING", Setting.cameraFacing);
                    } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        cameraIntent.putExtra("android.intent.extras.CAMERA_FACING", Setting.cameraFacing);
                        cameraIntent.putExtra("android.intent.extra.USE_FRONT_CAMERA", true);
                    } else {
                        cameraIntent.putExtra("android.intent.extras.CAMERA_FACING", Setting.cameraFacing);
                    }
                }
                startForegroundService();
                startActivityForResult(cameraIntent, requestCode);
            } else {
                Toast.makeText(getApplicationContext(), R.string.camera_temp_file_error_easy_photos, Toast.LENGTH_SHORT).show();
            }
        } else {
            Toast.makeText(getApplicationContext(), R.string.msg_no_camera_easy_photos, Toast.LENGTH_SHORT).show();
        }
    }


    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == Code.REQUEST_SETTING_APP_DETAILS) {
            if (PermissionUtil.checkAndRequestPermissionsInActivity(this, PermissionUtil.getNeedPermissions(this))) {
                hasPermissions();
            } else {
                permissionView.setVisibility(View.VISIBLE);
            }
            return;
        }
        switch (resultCode) {
            case RESULT_OK:
                if (Code.REQUEST_CAMERA == requestCode) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        onCameraResultAction(mTempFileUri);
                        return;
                    }
                    if (mTempImageFile == null || !mTempImageFile.isFile()) {
                        throw new RuntimeException("EasyPhotos拍照保存的文件不存在");
                    }
                    onCameraResult();
                    return;
                }

                if (Code.REQUEST_PREVIEW_ACTIVITY == requestCode) {
                    if (data.getBooleanExtra(Key.PREVIEW_CLICK_DONE, false)) {
                        done();
                        return;
                    }
                    mPhotosAdapter.change();
                    processOriginalMenu();
                    shouldShowMenuDone();
                    return;
                }

                if (Code.REQUEST_PUZZLE_SELECTOR == requestCode) {
                    Photo puzzlePhoto = data.getParcelableExtra(EasyPhotos.RESULT_PHOTOS);
                    addNewPhoto(puzzlePhoto);
                    return;
                }

                break;
            case RESULT_CANCELED:
                if (Code.REQUEST_CAMERA == requestCode) {
                    // 删除临时文件
                    if (mTempImageFile != null && mTempImageFile.exists()) {
                        mTempImageFile.delete();
                        mTempImageFile = null;
                    }
                    if (mTempFileUri != null) {
                        try {
                            getContentResolver().delete(mTempFileUri, null, null);
                        } catch (IllegalArgumentException e) {
                            e.printStackTrace();
                        }
                        mTempFileUri = null;
                    }
                    if (Setting.onlyStartCamera) {
                        finish();
                    }
                    return;
                }

                if (Code.REQUEST_PREVIEW_ACTIVITY == requestCode) {
                    processOriginalMenu();
                    return;
                }
                break;
            default:
                break;
        }
    }

    private void onCameraResult() {
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyyMMdd_HH_mm_ss", Locale.getDefault());
        String imageName = Setting.isOnlyVideo() ? "VID_%s.mp4" : "IMG_%s.jpg";
        String filename = String.format(imageName, dateFormat.format(new Date()));
        File reNameFile = new File(mTempImageFile.getParentFile(), filename);
        if (!reNameFile.exists()) {
            if (mTempImageFile.renameTo(reNameFile)) {
                mTempImageFile = reNameFile;
            }
        }
        //  将File类型转换为Uri类型,统一通过onCameraResultAction处理返回结果
        Uri photoUri = Setting.isOnlyVideo() ? UriUtils.getVideoContentUri(this, mTempImageFile.getAbsolutePath()) : UriUtils.getImageContentUri(this, mTempImageFile.getAbsolutePath());
        //  处理返回结果
        onCameraResultAction(photoUri);
    }

    /**
     * 根据设定处理资源返回
     *
     * @param uri 资源路径uri
     */
    private void onCameraResultAction(final Uri uri) {
        stopForegroundService();
        loadingDialog.show();

        new Thread(new Runnable() {
            @Override
            public void run() {
                final Photo photo = AlbumModel.buildPhotoFromUri(EasyPhotosActivity.this, uri);
                if (photo == null) {
                    Log.e("easyPhotos", "onCameraResultForQ() -》photo = null");
                    return;
                }
                MediaScannerConnectionUtils.refresh(EasyPhotosActivity.this, new File(photo.path));// 更新媒体库
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        loadingDialog.dismiss();
                        if (Setting.onlyStartCamera || albumModel.getAlbumItems().isEmpty()) {
                            Intent data = new Intent();
                            photo.selectedOriginal = Setting.selectedOriginal;
                            resultList.add(photo);
                            data.putParcelableArrayListExtra(EasyPhotos.RESULT_PHOTOS, resultList);
                            data.putExtra(EasyPhotos.RESULT_SELECTED_ORIGINAL, Setting.selectedOriginal);
                            setResult(RESULT_OK, data);
                            finish();
                            return;
                        }
                        addNewPhoto(photo);
                    }
                });
            }
        }).start();

    }

    /**
     * 将资源添加到列表中
     *
     * @param photo
     */
    private void addNewPhoto(Photo photo) {
        //  MediaScannerConnectionUtils.refresh(this, photo.path);
        photo.selectedOriginal = Setting.selectedOriginal;

        String albumItem_all_name = albumModel.getAllAlbumName(this);
        albumModel.album.getAlbumItem(albumItem_all_name).addImageItem(0, photo);
        String folderPath = new File(photo.path).getParentFile().getAbsolutePath();
        String albumName = StringUtils.getLastPathSegment(folderPath);
        albumModel.album.addAlbumItem(albumName, folderPath, photo.path, photo.uri);
        albumModel.album.getAlbumItem(albumName).addImageItem(0, photo);

        albumItemList.clear();
        albumItemList.addAll(albumModel.getAlbumItems());
        if (Setting.hasAlbumItemsAd()) {
            int albumItemsAdIndex = 2;
            if (albumItemList.size() < albumItemsAdIndex + 1) {
                albumItemsAdIndex = albumItemList.size() - 1;
            }
            albumItemList.add(albumItemsAdIndex, Setting.albumItemsAdView);
        }
        albumItemsAdapter.notifyDataSetChanged();

        if (Setting.count == 1) {
            Result.clear();
            int res = Result.addPhoto(photo);
            onSelectorOutOfMax(res);
        } else {
            if (Result.count() >= Setting.count) {
                onSelectorOutOfMax(null);
            } else {
                int res = Result.addPhoto(photo);
                onSelectorOutOfMax(res);
            }
        }
        rvAlbumItems.scrollToPosition(0);
        albumItemsAdapter.setSelectedPosition(0);
        shouldShowMenuDone();
    }

    private void onAlbumWorkedDo() {
        initView();
    }

    private void initView() {

        if (albumModel.getAlbumItems().isEmpty()) {
            Toast.makeText(getApplicationContext(), Setting.isOnlyVideo() ? R.string.no_videos_easy_photos : R.string.no_photos_easy_photos, Toast.LENGTH_LONG).show();
            if (Setting.isShowCamera) launchCamera(Code.REQUEST_CAMERA);
            else finish();
            return;
        }

        EasyPhotos.setAdListener(this);
        if (Setting.hasPhotosAd()) {
            findViewById(R.id.m_tool_bar_bottom_line).setVisibility(View.GONE);
        }
        ivCamera = findViewById(R.id.fab_camera);
        if (Setting.isShowCamera && Setting.isBottomRightCamera()) {
            ivCamera.setVisibility(View.VISIBLE);
        }
        if (!Setting.showPuzzleMenu) {
            findViewById(R.id.tv_puzzle).setVisibility(View.GONE);
        }
        mSecondMenus = findViewById(R.id.m_second_level_menu);
        int columns = getResources().getInteger(R.integer.photos_columns_easy_photos);
        tvAlbumItems = findViewById(R.id.tv_album_items);
        tvAlbumItems.setText(albumModel.getAlbumItems().get(0).name);
        tvDone = findViewById(R.id.tv_done);
        rvPhotos = findViewById(R.id.rv_photos);
        ((SimpleItemAnimator) rvPhotos.getItemAnimator()).setSupportsChangeAnimations(false);
        //去除item更新的闪光
        photoList.clear();
        photoList.addAll(albumModel.getCurrAlbumItemPhotos(0));
        int index = 0;
        if (Setting.hasPhotosAd()) {
            photoList.add(index, Setting.photosAdView);
        }
        if (Setting.isShowCamera && !Setting.isBottomRightCamera()) {
            if (Setting.hasPhotosAd()) index = 1;
            photoList.add(index, null);
        }
        mPhotosAdapter = new PhotosAdapter(this, photoList, this);

        gridLayoutManager = new GridLayoutManager(this, columns);
        if (Setting.hasPhotosAd()) {
            gridLayoutManager.setSpanSizeLookup(new GridLayoutManager.SpanSizeLookup() {
                @Override
                public int getSpanSize(int position) {
                    if (position == 0) {
                        return gridLayoutManager.getSpanCount();//独占一行
                    } else {
                        return 1;//只占一行中的一列
                    }
                }
            });
        }
        rvPhotos.setLayoutManager(gridLayoutManager);
        rvPhotos.setAdapter(mPhotosAdapter);
        tvOriginal = findViewById(R.id.tv_original);
        if (Setting.showOriginalMenu) {
            processOriginalMenu();
        } else {
            tvOriginal.setVisibility(View.GONE);
        }
        tvPreview = findViewById(R.id.tv_preview);

        initAlbumItems();
        shouldShowMenuDone();
        setClick(R.id.iv_album_items, R.id.tv_clear, R.id.iv_second_menu, R.id.tv_puzzle);
        setClick(tvAlbumItems, rootViewAlbumItems, tvDone, tvOriginal, tvPreview, ivCamera);

    }

    private void hideActionBar() {
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.hide();
        }
    }

    private void initAlbumItems() {

        rvAlbumItems = findViewById(R.id.rv_album_items);
        albumItemList.clear();
        albumItemList.addAll(albumModel.getAlbumItems());

        if (Setting.hasAlbumItemsAd()) {
            int albumItemsAdIndex = 2;
            if (albumItemList.size() < albumItemsAdIndex + 1) {
                albumItemsAdIndex = albumItemList.size() - 1;
            }
            albumItemList.add(albumItemsAdIndex, Setting.albumItemsAdView);
        }
        albumItemsAdapter = new AlbumItemsAdapter(this, albumItemList, 0, this);
        rvAlbumItems.setLayoutManager(new LinearLayoutManager(this));
        rvAlbumItems.setAdapter(albumItemsAdapter);
    }

    @Override
    public void onClick(View v) {
        int id = v.getId();
        if (R.id.tv_album_items == id || R.id.iv_album_items == id) {
            showAlbumItems(View.GONE == rootViewAlbumItems.getVisibility());
        } else if (R.id.root_view_album_items == id) {
            showAlbumItems(false);
        } else if (R.id.iv_back == id) {
            onBackPressed();
        } else if (R.id.tv_done == id) {
            done();
        } else if (R.id.tv_clear == id) {
            if (Result.isEmpty()) {
                processSecondMenu();
                return;
            }
            Result.removeAll();
            mPhotosAdapter.change();
            shouldShowMenuDone();
            processSecondMenu();
        } else if (R.id.tv_original == id) {
            if (!Setting.originalMenuUsable) {
                Toast.makeText(this, Setting.originalMenuUnusableHint, Toast.LENGTH_SHORT).show();
                return;
            }
            Setting.selectedOriginal = !Setting.selectedOriginal;
            processOriginalMenu();
            processSecondMenu();
        } else if (R.id.tv_preview == id) {
            PreviewActivity.start(EasyPhotosActivity.this, -1, 0);
        } else if (R.id.fab_camera == id) {
            launchCamera(Code.REQUEST_CAMERA);
        } else if (R.id.iv_second_menu == id) {
            processSecondMenu();
        } else if (R.id.tv_puzzle == id) {
            processSecondMenu();
            PuzzleSelectorActivity.start(this);
        }
    }

    public void processSecondMenu() {
        if (mSecondMenus == null) {
            return;
        }
        if (View.VISIBLE == mSecondMenus.getVisibility()) {
            mSecondMenus.setVisibility(View.INVISIBLE);
            if (Setting.isShowCamera && Setting.isBottomRightCamera()) {
                ivCamera.setVisibility(View.VISIBLE);
            }
        } else {
            mSecondMenus.setVisibility(View.VISIBLE);
            if (Setting.isShowCamera && Setting.isBottomRightCamera()) {
                ivCamera.setVisibility(View.INVISIBLE);
            }
        }
    }

    private boolean clickDone = false;

    private void done() {
        if (clickDone) return;
        clickDone = true;
        if (Setting.useWidth) {
            resultUseWidth();
            return;
        }
        resultFast();
    }

    private void resultUseWidth() {
        loadingDialog.show();
        new Thread(new Runnable() {
            @Override
            public void run() {
                int size = Result.photos.size();
                try {
                    for (int i = 0; i < size; i++) {
                        Photo photo = Result.photos.get(i);
                        if (photo.width == 0 || photo.height == 0) {
                            BitmapUtils.calculateLocalImageSizeThroughBitmapOptions(photo);
                        }
                        photo.orientation = BitmapUtils.getBitmapDegree(photo.path);
                        if (photo.orientation == 90 || photo.orientation == 270) {
                            int h = photo.width;
                            photo.width = photo.height;
                            photo.height = h;
                        }
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        loadingDialog.dismiss();
                        resultFast();
                    }
                });
            }
        }).start();
    }

    private void resultFast() {
        loadingDialog.dismiss();
        Intent intent = new Intent();
        Result.processOriginal();
        resultList.addAll(Result.photos);
        intent.putParcelableArrayListExtra(EasyPhotos.RESULT_PHOTOS, resultList);
        intent.putExtra(EasyPhotos.RESULT_SELECTED_ORIGINAL, Setting.selectedOriginal);
        setResult(RESULT_OK, intent);
        finish();
    }

    private void processOriginalMenu() {
        if (!Setting.showOriginalMenu) return;
        if (Setting.selectedOriginal) {
            tvOriginal.setTextColor(ContextCompat.getColor(this, R.color.easy_photos_fg_accent));
        } else {
            if (Setting.originalMenuUsable) {
                tvOriginal.setTextColor(ContextCompat.getColor(this, R.color.easy_photos_fg_primary));
            } else {
                tvOriginal.setTextColor(ContextCompat.getColor(this, R.color.easy_photos_fg_primary_dark));
            }
        }
    }

    private void showAlbumItems(boolean isShow) {
        if (null == setShow) {
            newAnimators();
        }
        if (isShow) {
            rootViewAlbumItems.setVisibility(View.VISIBLE);
            setShow.start();
        } else {
            setHide.start();
        }

    }

    private void newAnimators() {
        newHideAnim();
        newShowAnim();
    }

    private void newShowAnim() {
        ObjectAnimator translationShow = ObjectAnimator.ofFloat(rvAlbumItems, "translationY", mBottomBar.getTop(), 0);
        ObjectAnimator alphaShow = ObjectAnimator.ofFloat(rootViewAlbumItems, "alpha", 0.0f, 1.0f);
        translationShow.setDuration(300);
        setShow = new AnimatorSet();
        setShow.setInterpolator(new AccelerateDecelerateInterpolator());
        setShow.play(translationShow).with(alphaShow);
    }

    private void newHideAnim() {
        ObjectAnimator translationHide = ObjectAnimator.ofFloat(rvAlbumItems, "translationY", 0, mBottomBar.getTop());
        ObjectAnimator alphaHide = ObjectAnimator.ofFloat(rootViewAlbumItems, "alpha", 1.0f, 0.0f);
        translationHide.setDuration(200);
        setHide = new AnimatorSet();
        setHide.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                super.onAnimationEnd(animation);
                rootViewAlbumItems.setVisibility(View.GONE);
            }
        });
        setHide.setInterpolator(new AccelerateInterpolator());
        setHide.play(translationHide).with(alphaHide);
    }

    @Override
    public void onAlbumItemClick(int position, int realPosition) {
        updatePhotos(realPosition);
        showAlbumItems(false);
        tvAlbumItems.setText(albumModel.getAlbumItems().get(realPosition).name);
    }

    private void updatePhotos(int currAlbumItemIndex) {
        this.currAlbumItemIndex = currAlbumItemIndex;
        photoList.clear();
        photoList.addAll(albumModel.getCurrAlbumItemPhotos(currAlbumItemIndex));
        int index = 0;
        if (Setting.hasPhotosAd()) {
            photoList.add(index, Setting.photosAdView);
        }
        if (Setting.isShowCamera && !Setting.isBottomRightCamera()) {
            if (Setting.hasPhotosAd()) index = 1;
            photoList.add(index, null);
        }
        mPhotosAdapter.change();
        rvPhotos.scrollToPosition(0);
    }

    private void shouldShowMenuDone() {
        if (Result.isEmpty()) {
            if (View.VISIBLE == tvDone.getVisibility()) {
                ScaleAnimation scaleHide = new ScaleAnimation(1f, 0f, 1f, 0f);
                scaleHide.setDuration(200);
                tvDone.startAnimation(scaleHide);
            }
            tvDone.setVisibility(View.INVISIBLE);
            tvPreview.setVisibility(View.INVISIBLE);
        } else {
            if (View.INVISIBLE == tvDone.getVisibility()) {
                ScaleAnimation scaleShow = new ScaleAnimation(0f, 1f, 0f, 1f);
                scaleShow.setDuration(200);
                tvDone.startAnimation(scaleShow);
            }
            tvDone.setVisibility(View.VISIBLE);
            tvPreview.setVisibility(View.VISIBLE);
        }
        tvDone.setText(getString(R.string.selector_action_done_easy_photos, Result.count(), Setting.count));
    }

    @Override
    public void onCameraClick() {
        launchCamera(Code.REQUEST_CAMERA);
    }

    @Override
    public void onPhotoClick(int position, int realPosition) {
        PreviewActivity.start(EasyPhotosActivity.this, currAlbumItemIndex, realPosition);
    }

    @Override
    public void onSelectorOutOfMax(@Nullable Integer result) {
        if (result == null) {
            int resId = R.string.selector_reach_max_hint_easy_photos;
            if (Setting.isOnlyVideo()) {
                resId = R.string.selector_reach_max_video_hint_easy_photos;
            } else if (Setting.isOnlyImage()) {
                resId = R.string.selector_reach_max_image_hint_easy_photos;
            }
            Toast.makeText(this, getString(resId, Setting.count), Toast.LENGTH_SHORT).show();
            return;
        }
        switch (result) {
            case Result.PICTURE_OUT:
                Toast.makeText(this, getString(R.string.selector_reach_max_image_hint_easy_photos, Setting.complexPictureCount), Toast.LENGTH_SHORT).show();
                break;
            case Result.VIDEO_OUT:
                Toast.makeText(this, getString(R.string.selector_reach_max_video_hint_easy_photos, Setting.complexVideoCount), Toast.LENGTH_SHORT).show();
                break;
            case Result.SINGLE_TYPE:
                Toast.makeText(this, getString(R.string.selector_single_type_hint_easy_photos), Toast.LENGTH_SHORT).show();
                break;
        }
    }

    @Override
    public void onSelectorChanged() {
        shouldShowMenuDone();
    }


    @Override
    public void onBackPressed() {

        if (null != rootViewAlbumItems && rootViewAlbumItems.getVisibility() == View.VISIBLE) {
            showAlbumItems(false);
            return;
        }

        if (null != mSecondMenus && View.VISIBLE == mSecondMenus.getVisibility()) {
            processSecondMenu();
            return;
        }
        if (albumModel != null) albumModel.stopQuery();
        if (Setting.hasPhotosAd()) {
            mPhotosAdapter.clearAd();
        }
        if (Setting.hasAlbumItemsAd()) {
            albumItemsAdapter.clearAd();
        }
        setResult(RESULT_CANCELED);
        finish();
    }

    @Override
    protected void onDestroy() {
        if (albumModel != null) albumModel.stopQuery();
        stopForegroundService();
        super.onDestroy();
    }

    @Override
    public void onPhotosAdLoaded() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mPhotosAdapter.change();
            }
        });
    }

    @Override
    public void onAlbumItemsAdLoaded() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                albumItemsAdapter.notifyDataSetChanged();
            }
        });
    }


    private void setClick(@IdRes int... ids) {
        for (int id : ids) {
            findViewById(id).setOnClickListener(this);
        }
    }

    private void setClick(View... views) {
        for (View v : views) {
            v.setOnClickListener(this);
        }
    }

    private void startForegroundService() {
        if (xyCallConnection != null) return;
        Intent backgroundCallService = new Intent(this, BackgroundCallService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(backgroundCallService);
        } else {
            startService(backgroundCallService);
        }
        xyCallConnection = new ServiceConnection() {
            @Override
            public void onServiceConnected(ComponentName name, IBinder service) {

            }

            @Override
            public void onServiceDisconnected(ComponentName name) {

            }
        };
        bindService(backgroundCallService, xyCallConnection, Context.BIND_AUTO_CREATE);
    }

    private void stopForegroundService() {
        if (xyCallConnection == null) return;
        unbindService(xyCallConnection);
        stopService(new Intent(this, BackgroundCallService.class));
        xyCallConnection = null;
    }

}