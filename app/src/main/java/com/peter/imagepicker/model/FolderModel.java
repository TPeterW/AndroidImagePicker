package com.peter.imagepicker.model;

/**
 * Created by Peter on 9/13/15.
 */
public class FolderModel {
    private String dir;             // current directory's path
    private String firstImgPath;
    private String dirName;
    private int imgCount;

    public String getDir() {
        return dir;
    }

    public String getFirstImgPath() {
        return firstImgPath;
    }

    public String getDirName() {
        return dirName;
    }

    public int getImgCount() {
        return imgCount;
    }

    public void setDir(String dir) {
        this.dir = dir;

        // to get the name of the directory
        int lastIndexOf = this.dir.lastIndexOf("/");
        this.dirName = this.dir.substring(lastIndexOf).substring(1);
    }

    public void setFirstImgPath(String firstImgPath) {
        this.firstImgPath = firstImgPath;
    }

    // not needed, can be done while getting directory path
//    public void setDirName(String dirName) {
//        this.dirName = dirName;
//    }

    public void setImgCount(int imgCount) {
        this.imgCount = imgCount;
    }
}
