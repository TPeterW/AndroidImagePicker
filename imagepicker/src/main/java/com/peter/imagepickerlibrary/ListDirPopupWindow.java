package com.peter.imagepickerlibrary;

import android.content.Context;
import android.graphics.drawable.BitmapDrawable;
import android.util.DisplayMetrics;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.PopupWindow;
import android.widget.TextView;

import com.peter.imagepickerlibrary.model.FolderModel;
import com.peter.imagepickerlibrary.utils.ImageLoader;

import java.util.List;

/**
 * Created by Peter on 9/18/15.
 */
public class ListDirPopupWindow extends PopupWindow {
    private int width;
    private int height;
    private View convertView;
    private ListView listView;
    private List<FolderModel> dataList;

    public interface OnDirSelectListener{
        void onSelected(FolderModel folderModel);
    }

    public OnDirSelectListener listener;

    public void setOnDirSelectListener(OnDirSelectListener listener) {
        this.listener = listener;
    }

    public ListDirPopupWindow(Context context, List<FolderModel> dataList){
        getWidthAndHeight(context);

        convertView = LayoutInflater.from(context).inflate(R.layout.pop_up_window, null);
        this.dataList = dataList;

        setContentView(convertView);
        setWidth(width);
        setHeight(height);

        // set touch ability
        setFocusable(true);
        setTouchable(true);
        setOutsideTouchable(true);
        setBackgroundDrawable(new BitmapDrawable());

        setTouchInterceptor(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                if (event.getAction() == MotionEvent.ACTION_OUTSIDE) {                // if press outside, pop up window collapses
                    dismiss();
                    return true;
                }

                return false;
            }
        });

        initView(context);
        initEvent();
    }

    private void initView(Context context) {
        listView = (ListView) convertView.findViewById(R.id.pop_up_list);
        listView.setAdapter(new PopupAdapter(context, dataList));
    }

    private void initEvent() {
        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                if(listener != null){
                    listener.onSelected(dataList.get(position));
                }
            }
        });
    }

    /**
     * Calculate width and height according to context
     * @param context
     */
    private void getWidthAndHeight(Context context) {
        WindowManager windowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);

        DisplayMetrics metrics = new DisplayMetrics();
        windowManager.getDefaultDisplay().getMetrics(metrics);

        width = metrics.widthPixels;
        height = (int) (metrics.heightPixels * 0.7);
    }

    // Adapter for the ListView in the Pop-up Window
    private class PopupAdapter extends ArrayAdapter<FolderModel>{
        private LayoutInflater inflater;

        private List<FolderModel> folderModelList;

        // use this three-param constructor
        public PopupAdapter(Context context, List<FolderModel> objects) {
            super(context, 0, objects);

            inflater = LayoutInflater.from(context);
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            ViewHolder holder = null;
            if(convertView == null){
                holder = new ViewHolder();
                convertView = inflater.inflate(R.layout.pop_up_item, parent, false);

                holder.imageView = (ImageView) convertView.findViewById(R.id.dir_first_item_image);
                holder.dirName = (TextView) convertView.findViewById(R.id.dir_item_name);
                holder.dirCount = (TextView) convertView.findViewById(R.id.dir_item_count);

                convertView.setTag(holder);
            }
            else {
                holder = (ViewHolder) convertView.getTag();
            }

            FolderModel model = getItem(position);      // getItem is a built-in method for ArrayAdapter
            // Reset
            holder.imageView.setImageResource(R.drawable.picture_no);       // in case the second screen displays pictures from first screen

            ImageLoader.getInstance(3, ImageLoader.Type.LIFO).loadImage(model.getFirstImgPath(), holder.imageView);

            holder.dirName.setText(model.getDirName());
            holder.dirCount.setText(model.getImgCount() + "");

            return convertView;
        }

        // create this class to hold the views as a model to increase running speed
        private class ViewHolder{
            ImageView imageView;
            TextView dirName;
            TextView dirCount;
        }
    }
}
