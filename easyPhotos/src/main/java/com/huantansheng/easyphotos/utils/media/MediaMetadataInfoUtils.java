package com.huantansheng.easyphotos.utils.media;

import android.hardware.Camera;
import android.media.MediaMetadataRetriever;
import android.text.format.DateUtils;

import java.io.IOException;

/**
 * MediaMetadataInfoUtils
 * Create By lishilin On 2019/3/25
 */
public class MediaMetadataInfoUtils {

    /**
     * 获取时长
     *
     * @param path path
     * @return duration
     */
    public static long getDuration(String path) {
        MediaMetadataRetriever mmr = null;
        try {
            mmr = new MediaMetadataRetriever();
            mmr.setDataSource(path);
            return Long.parseLong(mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION));
        } catch (Exception ignored) {
        } finally {
            if (mmr != null) {
                try {
                    mmr.release();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        return 0;
    }

    /**
     * 获取文件的宽高属性
     *
     * @param path path
     * @return duration
     */
    public static int getSize(String path, int keyCode) {
        MediaMetadataRetriever mmr = null;
        try {
            mmr = new MediaMetadataRetriever();
            mmr.setDataSource(path);
            return Integer.parseInt(mmr.extractMetadata(keyCode));
        } catch (Exception ignored) {
        } finally {
            if (mmr != null) {
                try {
                    mmr.release();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        return 0;
    }

    /**
     * 格式化时长（不足一秒则显示为一秒）
     *
     * @param duration duration
     * @return "MM:SS" or "H:MM:SS"
     */
    public static String format(long duration) {
        long seconds = duration / 1000;
        if (seconds == 0) {
            seconds++;
        }
        return DateUtils.formatElapsedTime(seconds);
    }

    /**
     * 返回true 表示可以使用  返回false表示不可以使用
     */
    public static boolean isCameraCanUse() {
        boolean isCanUse = true;
        Camera mCamera = null;
        try {
            mCamera = Camera.open();
            Camera.Parameters mParameters = mCamera.getParameters(); //针对魅族手机
            mCamera.setParameters(mParameters);
        } catch (Exception e) {
            isCanUse = false;
        }

        if (mCamera != null) {
            try {
                mCamera.release();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return isCanUse;
    }

}
