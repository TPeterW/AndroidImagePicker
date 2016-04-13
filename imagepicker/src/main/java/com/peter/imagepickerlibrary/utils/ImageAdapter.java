package com.peter.imagepickerlibrary.utils;

import android.content.Context;
import android.graphics.Color;
import android.util.DisplayMetrics;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.BaseAdapter;
import android.widget.ImageButton;
import android.widget.ImageView;

import com.peter.imagepickerlibrary.R;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ImageAdapter extends BaseAdapter {
    // to write record which ones have been picked
    // use static because when folders change, we initialise new adapters
    private static Set<String> selectedImage = new HashSet<String>();

    private String dirPath;
    private List<String> imageList;
    private LayoutInflater inflater;

    private int screenWidth;

     public ImageAdapter(Context context, List<String> imageList, String dirPath) {      // separate directory and image names to save space, since directory is the same, once is enough
         this.dirPath = dirPath;
         this.imageList = imageList;
         this.inflater = LayoutInflater.from(context);

         WindowManager windowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
         DisplayMetrics windowMetrics = new DisplayMetrics();
         windowManager.getDefaultDisplay().getMetrics(windowMetrics);
         screenWidth = windowMetrics.widthPixels;
     }

    @Override
    public int getCount() {
            return imageList.size();
        }

    /**
     * Returns the name of the image file
     * @param position
     * @return String
     */
    @Override
    public Object getItem(int position) {
        return imageList.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(final int position, View convertView, ViewGroup parent) {
        final ViewHolder viewHolder;

        if(convertView == null){
            convertView = inflater.inflate(R.layout.gridview_item, parent, false);

            viewHolder = new ViewHolder();
            viewHolder.imgView = (ImageView) convertView.findViewById(R.id.item_image);
            viewHolder.imgButton = (ImageButton) convertView.findViewById(R.id.item_select);

            convertView.setTag(viewHolder);
        }
        else {
            viewHolder = (ViewHolder) convertView.getTag();
        }

        // every time a new screen is loaded, make the unloaded ImageView have picture_no
        // instead of left over pictures from last screen
        // Reset all states
        viewHolder.imgView.setImageResource(R.drawable.picture_no);
        viewHolder.imgButton.setImageResource(R.drawable.picture_unselected);
        viewHolder.imgView.setColorFilter(null);

        if(selectedImage.contains(dirPath) || selectedImage.contains(dirPath + "/" + imageList.get(position)) || selectedImage.contains(imageList.get(position))){
            viewHolder.imgButton.setImageResource(R.drawable.picture_selected);
            viewHolder.imgView.setColorFilter(Color.parseColor("#77000000"));
        }

        // will have to change this if number of columns change
        viewHolder.imgView.setMaxWidth(screenWidth / 3);

        ImageLoader.getInstance(3, ImageLoader.Type.LIFO).loadImage(dirPath + "/" + imageList.get(position), viewHolder.imgView);
        // after loading this picture, ImageLoader will set ImageView's bitmap to the picture
        // one sentence finishes the loading

        final String filePath = dirPath + "/" + imageList.get(position);      // path of the image that is clicked on
        // this is done when each view is being "get", aka loaded, and it has its own file path stored here as a final String

        viewHolder.imgView.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(selectedImage.contains(filePath)){
                    // is already selected
                    selectedImage.remove(filePath);
                    viewHolder.imgView.setColorFilter(null);
                    viewHolder.imgButton.setImageResource(R.drawable.picture_unselected);
                }
                else {
                    // not yet selected
                    selectedImage.add(filePath);
                    viewHolder.imgView.setColorFilter(Color.parseColor("#77000000"));
                    viewHolder.imgButton.setImageResource(R.drawable.picture_selected);
                }
//                notifyDataSetChanged();             // if we use this, every time we update it, the screen would flash

            }
        });

        return convertView;
    }

    // to match gridview_item.xml layout, reduce findViewById operation
    private class ViewHolder
    {
        ImageView imgView;
        ImageButton imgButton;
    }
}