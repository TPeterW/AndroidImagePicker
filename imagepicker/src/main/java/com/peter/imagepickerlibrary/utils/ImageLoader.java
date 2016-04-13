package com.peter.imagepickerlibrary.utils;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Looper;
import android.os.Message;
import android.util.DisplayMetrics;
import android.util.LruCache;
import android.os.Handler;
import android.view.ViewGroup;
import android.widget.ImageView;

import java.lang.reflect.Field;
import java.util.LinkedList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;

/**
 * Created by Peter on 9/12/15.
 * Load Images
 *
 * Creates a LruCache to temporarily put the images
 * Uses LIFO to handle polling threads
 *
 *
 */
public class ImageLoader {
    private static ImageLoader instance;

    private LruCache<String, Bitmap> lruCache;      // a cache to put images in
    private ExecutorService threadPool;             // thread pool to queue up threads and tasks
    private static final int DEFAULT_THREAD_COUNT = 1;

    public enum Type { FIFO, LIFO }
    private Type defaultType = Type.LIFO;           // way of executing/calling the tasks

    private LinkedList<Runnable> taskQueue;         // queue of tasks

    private Thread poolThread;                      // backstage polling Thread
    private Handler poolThreadHandler;              // to handle threads

    private Handler UIHandler;                      // to handle images and update the ImageViews

    private Semaphore semaphorePoolThreadHandler = new Semaphore(0);    // monitor the signal from the creation of PoolThreadHandler, make sure that PoolThreadHandler isn't null when used
    private Semaphore semaphoreThreadPool;                              // monitor the signal from the tasks, make sure that is it actually LIFO

    private ImageLoader(int threadCount, Type type){
        init(threadCount, type);
    }

    private void init(int threadCount, Type type) {
        // backstage polling thread
        poolThread = new Thread(){
            @Override
            public void run(){
                Looper.prepare();
                poolThreadHandler = new Handler(){
                    @Override
                    public void handleMessage(Message msg) {            // when tasks comes, handler would send a message to looper
                        // thread pool will take a task to execute
                        threadPool.execute(getTask());

                        try {
                            semaphoreThreadPool.acquire();              // when there are more tasks than to be processed, then this will block/stuck
                                                                        // while in loadImage method, a signal is released every time an image is loaded successfully
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }
                };
                // release a signal, the signal then isn't the default(0)
                semaphorePoolThreadHandler.release();

                Looper.loop();
            }
        };
        poolThread.start();

        // initialise LruCache
        int maxMemory = (int) Runtime.getRuntime().maxMemory();
        int cacheSize = maxMemory / 8;

        lruCache = new LruCache<String, Bitmap>(cacheSize){
            @Override
            protected int sizeOf(String key, Bitmap value) {
                return value.getRowBytes() * value.getHeight(); // to get the value of each Bitmap, aka size
            }
        };

        // create a thread pool
        threadPool = Executors.newFixedThreadPool(threadCount);
        taskQueue = new LinkedList<Runnable>();
        this.defaultType = type;

        semaphoreThreadPool = new Semaphore(threadCount);
    }

    /**
     * Take a method from the queue of tasks
     * Have to make sure that the backstage thread pool only takes the next task when it's free (also use semaphore)
     * @return Runnable
     */
    private Runnable getTask() {
        if(defaultType == Type.FIFO){
            return taskQueue.removeFirst();
        }
        else if(defaultType == Type.LIFO){
            return taskQueue.removeLast();
        }
        return null;
    }

    public static ImageLoader getInstance(int threadCount, Type type){
        if(instance == null){                   // No need to synchronise from the beginning, save efficiency
            synchronized (ImageLoader.class){
                if (instance == null){          //  After synchronise, check again
                    instance = new ImageLoader(threadCount, type);
                }
            }
        }

        return instance;
    }

    /**
     * Set image for ImageView according to path
     * El más importante método
     * @param path
     * @param imageView
     */
    public void loadImage(final String path, final ImageView imageView){        // also need an ImageView to show the image
        imageView.setTag(path);

        if(UIHandler == null){
            UIHandler = new Handler(){
                @Override
                public void handleMessage(Message msg) {                        // to be called when image is loaded successfully
                    // get selected image, set image for ImageView callbacks
                    ImageHolder holder = (ImageHolder)msg.obj;
                    Bitmap bm = holder.bitmap;
                    ImageView imgView = holder.imageView;
                    String path = holder.path;

                    if(imgView.getTag().toString().equals(path)){         // if path is the path needed; in case it was still the previous ImageView
                        imgView.setImageBitmap(bm);
                    }
                }
            };
        }

        // get bitmap from cache according to path
        Bitmap bm = getBitmapFromLruCache(path);

        if(bm != null){
            refreshBitmap(path, imageView, bm);
        }
        else {
            addTaskToQueue(new Runnable(){
                @Override
                public void run() {
                    // load images
                    // compress images
                    // 1. first to get the size of the image
                    ImageSize imageSize = getImageViewSize(imageView);
                    // 2. second to compress the image
                    Bitmap bm = decodeSampledBitmapFromPath(path, imageSize.width, imageSize.height);
                    // 3. third to add the image to Cache
                    addBitmapToLruCache(path, bm);

                    refreshBitmap(path, imageView, bm);

                    semaphoreThreadPool.release();              // let the thread pool be able to get the next task to execute
                }
            });
        }
    }

    /**
     * Refresh, the callback to load image after finding the image
     * Let the image be processed
     * @param path
     * @param imageView
     * @param bm
     */
    private void refreshBitmap(String path, ImageView imageView, Bitmap bm) {
        Message message = Message.obtain();
        ImageHolder holder = new ImageHolder();
        holder.bitmap = bm;
        holder.path = path;
        holder.imageView = imageView;
        message.obj = holder;
        UIHandler.sendMessage(message);
    }

    /**
     * Add bitmap/image to LruCache
     * @param path
     * @param bm
     */
    private void addBitmapToLruCache(String path, Bitmap bm) {
        if(getBitmapFromLruCache(path) == null){
            if(bm != null){
                lruCache.put(path, bm);
            }
        }
    }

    /**
     * Compress image according to width and height in which the image is to be displayed
     * Using options
     * @param path
     * @param width
     * @param height
     * @return bitmap
     */
    private Bitmap decodeSampledBitmapFromPath(String path, int width, int height) {
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;      // gets the width and height but doesn't load the image to memory
        BitmapFactory.decodeFile(path, options);    // now options gets the width and height and other info

        options.inSampleSize = calculateInSampleSize(options, width, height);

        // use inSampleSize to decode the image again
        // this time not only to get width and height, but also into memory
        options.inJustDecodeBounds = false;
        Bitmap bitmap = BitmapFactory.decodeFile(path, options);    // compress according to inSampleSize

        return bitmap;
    }

    /**
     * Calculate SampleSize according to both actual and needed width and height
     * @param options
     * @param requiredWidth
     * @param requiredHeight
     * @return inSampleSize
     */
    private int calculateInSampleSize(BitmapFactory.Options options, int requiredWidth, int requiredHeight) {
        int width = options.outWidth;
        int height = options.outHeight;

        // customise here to design own strategy
        // IMPORTANTE
        // SIGNIFICATIVAMENTE
        // NUMERO UNO
        int inSampleSize = 1;
        if (width > requiredWidth || height > requiredHeight){      // compress under this case
            int widthRatio = Math.round(width * 1.0f / requiredWidth);
            int heightRatio = Math.round(height *1.0f / requiredHeight);

            inSampleSize = Math.max(widthRatio, heightRatio);       // get the greater number
        }

        return inSampleSize;
    }

    /**
     * Get appropriate height and width for compression according to ImageView
     * @param imageView
     * @return imageSize
     */
    private ImageSize getImageViewSize(ImageView imageView) {
        ImageSize imageSize = new ImageSize();

        // get information about the screen
        DisplayMetrics displayMetrics = imageView.getContext().getResources().getDisplayMetrics();

        // layout parameters
        ViewGroup.LayoutParams layoutParams= imageView.getLayoutParams();

        int width = imageView.getWidth();       // get actual width of ImageView
        if(width <= 0){     // just created, hasn't been added to container
            width = layoutParams.width;         // get width declared in layout
        }
        if(width <= 0){    // if width is set to wrap content or match parent
//            width = imageView.getMaxWidth();                                  // API 16 and higher
            width = getImageViewFieldValue(imageView, "mMaxWidth");             // all APIs, field name see ImageView declaration
            // some still return 0
        }
        if(width <= 0){     // most unfortunately the width of the screen
            width = displayMetrics.widthPixels;
        }

        int height = imageView.getHeight();       // get actual height of ImageView
        if(height <= 0){     // just created, hasn't been added to container
            height = layoutParams.height;         // get width declared in layout
        }
        if(height <= 0){    // if width is set to wrap content or match parent
//            height = imageView.getMaxHeight();                                // API 16 and higher
            width = getImageViewFieldValue(imageView, "mMaxHeight");            // all APIs, field name see ImageView declaration
            // some still return 0
        }
        if(height <= 0){     // most unfortunately the width of the screen
            height = displayMetrics.heightPixels;
        }

        imageSize.height = height;
        imageSize.width = width;

        return imageSize;
    }

    /**
     * Reflect
     * Use Reflect to get attributes of objects, in this case, width and height of an ImageView
     * @param object
     * @param fieldName
     * @return Property
     */
    private static int getImageViewFieldValue(Object object, String fieldName){
        int value = 0;

        try {
            Field field = ImageView.class.getDeclaredField(fieldName);
            field.setAccessible(true);

            int fieldValue = field.getInt(object);
            if (fieldValue > 0 && fieldValue < Integer.MAX_VALUE){
                value = fieldValue;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        return  value;
    }

    /**
     * Add task to queue
     * @param runnable
     */
    private synchronized void addTaskToQueue(Runnable runnable) {
        taskQueue.add(runnable);

        // at this point, it is possible that poolThreadHandler hasn't been created yet (as they are parallel)
        // have to make sure that this doesn't start before poolThreadHandler is created
        // check if poolThreadHandler is null
        try {
            if (poolThreadHandler == null){
                semaphorePoolThreadHandler.acquire();
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        poolThreadHandler.sendEmptyMessage(0x110);  // any value
    }

    /**
     * get bitmap from cache according to path
     * @param path
     * @return Bitmap (from LruCache)
     */
    private Bitmap getBitmapFromLruCache(String path) {
        return lruCache.get(path);
    }




    /**
     * To record the size of an image, two dimensional, so we need an object
     */
    private class ImageSize{
        int width;
        int height;
    }

    /**
     * New class for images
     * To prevent handling other ImageViews
     */
    private class ImageHolder{
        Bitmap bitmap;
        ImageView imageView;
        String path;
    }
}
