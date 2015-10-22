package com.peter.imagepicker;

import android.app.ProgressDialog;
import android.content.ContentResolver;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Environment;
import android.provider.MediaStore;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.view.WindowManager;
import android.widget.GridView;
import android.widget.PopupWindow;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.peter.imagepickerlibrary.model.FolderModel;
import com.peter.imagepickerlibrary.utils.ImageAdapter;
import com.peter.imagepickerlibrary.ListDirPopupWindow;

import java.io.File;
import java.io.FilenameFilter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Created by Peter on 9/13/15.
 */
public class ImagePicker extends AppCompatActivity {
    private GridView mainGridView;
    private List<String> imageList;
    private ImageAdapter adapter;

    private RelativeLayout bottomLayout;
    private TextView dirName;
    private TextView dirCount;

    private File currentDir;
    private int maxPicCount;

    private List<FolderModel> folderList = new ArrayList<FolderModel>();

    private ProgressDialog progressDialog;

    private static final int DATA_LOADED = 0x110;

    private ListDirPopupWindow popupWindow;

    private Handler handler = new Handler(){
        @Override
        public void handleMessage(android.os.Message msg) {
            if(msg.what == DATA_LOADED){
                progressDialog.dismiss();
                // bind data to view
                bindDataToView();

                initPopupWindow();
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.image_picker);

        initView();
        initData();
        initEvent();
    }

    private void initView() {
        mainGridView = (GridView) findViewById(R.id.mainGridView);
        bottomLayout = (RelativeLayout) findViewById(R.id.bottom_layout);
        dirName = (TextView) findViewById(R.id.dir_name);
        dirCount = (TextView) findViewById(R.id.dir_count);
    }

    /**
     * Use ContentProvider to scan all files in the phone, which takes time
     * And that's why we're putting it on an individual thread and will notify the UIHandler in ImageLoader class when it's done
     */
    private void initData() {
        if(!Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)){
            Toast.makeText(ImagePicker.this, R.string.storage_not_available, Toast.LENGTH_SHORT).show();
            return;
        }

        progressDialog = ProgressDialog.show(ImagePicker.this, null, getResources().getString(R.string.loading_images));

        new Thread(){
            @Override
            public void run() {
                Uri imgUri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI;
                ContentResolver resolver = ImagePicker.this.getContentResolver();
                Cursor cursor = resolver.query(
                        imgUri,
                        null,
                        MediaStore.Images.Media.MIME_TYPE + " = ? or " + MediaStore.Images.Media.MIME_TYPE + " = ? ",
                        new String[]{"image/jpeg", "image/png"},
                        MediaStore.Images.Media.DATE_MODIFIED);

                // to prevent iterate through parentFile(folder) more than once
                // will be automatically recycled after this method is done as dirPathSet is declared inside the method
                Set<String> dirPathSet = new HashSet<String>();

                while(cursor.moveToNext()){
                    String path = cursor.
                            getString(cursor.getColumnIndex(MediaStore.Images.Media.DATA));     // get image path

                    File parentFile = new File(path).getParentFile();

                    if(parentFile == null)      // it is still possible that content provider couldn't find a file with a path
                        continue;

                    String dirPath = parentFile.getAbsolutePath();

                    FolderModel folderModel = null;

                    if(dirPathSet.contains(dirPath)){
                        continue;
                    }
                    else {
                        dirPathSet.add(dirPath);
                        folderModel = new FolderModel();
                        folderModel.setDir(dirPath);
                        folderModel.setFirstImgPath(path);
                    }


                    if(parentFile.list() == null)       // it is possible... maybe ".../storage/emulated/0" or something?
                        continue;

                    int picCount = parentFile.list(new FilenameFilter() {
                        @Override
                        public boolean accept(File dir, String filename) {
                            if(filename.toLowerCase().endsWith(".jpg") || filename.toLowerCase().endsWith(".jpeg") || filename.toLowerCase().endsWith(".png")){
                                return true;
                            }
                            else {
                                return false;
                            }
                        }
                    }).length;

                    folderModel.setImgCount(picCount);

                    folderList.add(folderModel);

                    if(picCount > maxPicCount){
                        maxPicCount = picCount;
                        currentDir = parentFile;
                    }
                }
                cursor.close();

                // to inform handler that the scan is finished
                handler.sendEmptyMessage(DATA_LOADED);    // any value

            }
        }.start();
    }

    private void initEvent() {
        bottomLayout.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
//                popupWindow.setAnimationStyle(R.style.pop_up_window_anim);
                bottomLayout.bringToFront();
                popupWindow.showAsDropDown(bottomLayout, 0, 0);

                lightOff();

            }
        });
    }

    private void initPopupWindow() {
        popupWindow = new ListDirPopupWindow(this, folderList);

        popupWindow.setOnDismissListener(new PopupWindow.OnDismissListener() {
            @Override
            public void onDismiss() {               // handle the lighting of surrounding area(background)
                lightOn();
            }
        });

        popupWindow.setOnDirSelectListener(new ListDirPopupWindow.OnDirSelectListener() {
            @Override
            public void onSelected(FolderModel folderModel) {
                // update folder
                currentDir = new File(folderModel.getDir());
                // update images
                imageList = Arrays.asList(currentDir.list(new FilenameFilter() {
                    @Override
                    public boolean accept(File dir, String filename) {
                        if(filename.toLowerCase().endsWith(".jpg") || filename.toLowerCase().endsWith(".jpeg") || filename.toLowerCase().endsWith(".png")){
                            return true;
                        }
                        else {
                            return false;
                        }
                    }
                }));
                // update adapter
                adapter = new ImageAdapter(ImagePicker.this, imageList, currentDir.getAbsolutePath());

//                adapter.notifyDataSetChanged();

                mainGridView.setAdapter(adapter);

                // update TextView
                dirCount.setText(imageList.size() + "");
                dirName.setText(folderModel.getDirName());

                popupWindow.dismiss();
            }
        });
    }

    /**
     *  turn content area to light
     */
    protected void lightOn() {
        WindowManager.LayoutParams layoutParams = getWindow().getAttributes();
        layoutParams.alpha = 1.0f;
        getWindow().setAttributes(layoutParams);
    }

    /**
     * turn content area to dark
     */
    private void lightOff() {
        WindowManager.LayoutParams layoutParams = getWindow().getAttributes();
        layoutParams.alpha = 0.3f;
        getWindow().setAttributes(layoutParams);
    }

    private void bindDataToView() {
        if(currentDir == null){
            Toast.makeText(ImagePicker.this, R.string.no_image_scanned, Toast.LENGTH_SHORT).show();
            dirName.setText("null");
            dirCount.setText("0");
            return;
        }

        imageList = Arrays.asList(currentDir.list());

        adapter = new ImageAdapter(this, imageList, currentDir.getAbsolutePath());
        mainGridView.setAdapter(adapter);

        dirCount.setText(maxPicCount + "");
        dirName.setText(currentDir.getName());
    }
}
