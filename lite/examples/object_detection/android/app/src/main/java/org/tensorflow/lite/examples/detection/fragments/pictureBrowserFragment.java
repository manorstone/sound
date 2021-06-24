package org.tensorflow
        .lite.examples.detection.fragments;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager.widget.PagerAdapter;
import androidx.viewpager.widget.ViewPager;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.RequestOptions;
import com.bumptech.glide.util.Util;

import org.tensorflow.lite.examples.detection.ImageDisplay;
import org.tensorflow.lite.examples.detection.R;
import org.tensorflow.lite.examples.detection.env.ImageUtils;
import org.tensorflow.lite.examples.detection.env.Logger;
import org.tensorflow.lite.examples.detection.utils.imageIndicatorListener;
import org.tensorflow.lite.examples.detection.utils.pictureFacer;
import org.tensorflow.lite.examples.detection.utils.recyclerViewPagerImageIndicator;
import org.tensorflow.lite.examples.detection.utils.ProcessImage;
import org.tensorflow.lite.examples.detection.tflite.Detector;
import org.tensorflow.lite.examples.detection.tflite.TFLiteObjectDetectionAPIModel;
import org.tensorflow.lite.examples.detection.tracking.MultiBoxTracker;
import org.tensorflow.lite.examples.detection.customview.OverlayView;


import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import static androidx.core.view.ViewCompat.setTransitionName;

public class pictureBrowserFragment extends Fragment implements imageIndicatorListener {

    private ArrayList<pictureFacer> allImages = new ArrayList<>();
    private int position;
    private Context animeContx;
    private ImageView image;
    private ViewPager imagePager;
    private RecyclerView indicatorRecycler;
    private int viewVisibilityController;
    private int viewVisibilityLooper;
    private ImagesPagerAdapter pagingImages;
    private int previousSelected = -1;

    private static final Logger LOGGER = new Logger();
    private static final int TF_OD_API_INPUT_SIZE = 416;
    private static final boolean TF_OD_API_IS_QUANTIZED = false;
    private static final String TF_OD_API_MODEL_FILE = "./detect.tflite";
    private static final String TF_OD_API_LABELS_FILE = "labelmap.txt";

    private Bitmap sourceBitmap;
    private Bitmap cropBitmap;


    public pictureBrowserFragment(){ }

    public pictureBrowserFragment(ArrayList<pictureFacer> allImages, int imagePosition, Context anim){
        this.allImages = allImages;
        this.position = imagePosition;
        this.animeContx = anim;
    }

    public static pictureBrowserFragment newInstance(ArrayList<pictureFacer> allImages, int imagePosition, Context anim){
        Log.d("instance", allImages.get(0).getPicturePath().toString());
        pictureBrowserFragment fragment = new pictureBrowserFragment(allImages, imagePosition, anim);
        return fragment;
    }

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.picture_browser, container, false);
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        viewVisibilityController = 0;
        viewVisibilityLooper = 0;

        imagePager = view.findViewById(R.id.imagePager);
        pagingImages = new ImagesPagerAdapter();
        imagePager.setAdapter(pagingImages);
        imagePager.setOffscreenPageLimit(3);
        imagePager.setCurrentItem(position);//displaying the image at the current position passed by the ImageDisplay Activity


        indicatorRecycler = view.findViewById(R.id.indicatorRecycler);
        indicatorRecycler.hasFixedSize();
        indicatorRecycler.setLayoutManager(new GridLayoutManager(getContext(),1,RecyclerView.HORIZONTAL,false));
        RecyclerView.Adapter indicatorAdapter = new recyclerViewPagerImageIndicator(allImages,getContext(),this);
        indicatorRecycler.setAdapter(indicatorAdapter);

        //adjusting the recyclerView indicator to the current position of the viewPager, also highlights the image in recyclerView with respect to the
        //viewPager's position
        allImages.get(position).setSelected(true);
        previousSelected = position;
        indicatorAdapter.notifyDataSetChanged();
        indicatorRecycler.scrollToPosition(position);


        imagePager.addOnPageChangeListener(new ViewPager.OnPageChangeListener() {
            @Override
            public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {

            }

            @Override
            public void onPageSelected(int position) {

                if(previousSelected != -1){
                    allImages.get(previousSelected).setSelected(false);
                    previousSelected = position;
                    allImages.get(position).setSelected(true);
                    indicatorRecycler.getAdapter().notifyDataSetChanged();
                    indicatorRecycler.scrollToPosition(position);
                }else{
                    previousSelected = position;
                    allImages.get(position).setSelected(true);
                    indicatorRecycler.getAdapter().notifyDataSetChanged();
                    indicatorRecycler.scrollToPosition(position);
                }
            }

            @Override
            public void onPageScrollStateChanged(int state) {

            }
        });


        indicatorRecycler.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                return false;
            }
        });

    }

    @Override
    public void onImageIndicatorClicked(int ImagePosition) {
        if(previousSelected != -1){
            allImages.get(previousSelected).setSelected(false);
            previousSelected = ImagePosition;
            indicatorRecycler.getAdapter().notifyDataSetChanged();
        }else{
            previousSelected = ImagePosition;
        }
        imagePager.setCurrentItem(ImagePosition);
    }

    private class ImagesPagerAdapter extends PagerAdapter {
        private static final int TF_OD_API_INPUT_SIZE = 416;
        private static final boolean TF_OD_API_IS_QUANTIZED = false;
        private static final String TF_OD_API_MODEL_FILE = "./detect.tflite";
        private static final String TF_OD_API_LABELS_FILE = "labelmap.txt";
        private static final boolean MAINTAIN_ASPECT = false;
        public static final float MINIMUM_CONFIDENCE_TF_OD_API = 0.5f;
        private Integer sensorOrientation = 90;

        private Detector detector;
        private Matrix frameToCropTransform;
        private Matrix cropToFrameTransform;
        private MultiBoxTracker tracker;
        private OverlayView trackingOverlay;

        protected int previewWidth = 0;
        protected int previewHeight = 0;
        View view;


        private Bitmap sourceBitmap;
        private Bitmap cropBitmap;

        @Override
        public int getCount() {
            return allImages.size();
        }

        @NonNull
        @Override
        public Object instantiateItem(@NonNull ViewGroup containerCollection, int position) {
            LayoutInflater layoutinflater = (LayoutInflater) containerCollection.getContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);

            view = layoutinflater.inflate(R.layout.picture_browser_pager,null);
            image = view.findViewById(R.id.image);

            Handler handler = new Handler();
            new Thread(() -> {
                final List<Detector.Recognition> results = detector.recognizeImage(cropBitmap);
                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        handleResult(cropBitmap, results);
                    }
                });
            });

            setTransitionName(image, String.valueOf(position)+"picture");

            pictureFacer pic = allImages.get(position);
//            Glide.with(animeContx)
//                    .load(pic.getPicturePath())
//                    .apply(new RequestOptions().fitCenter())
//                    .override(412,412)
//                    .into(image);

            File imgFile = new File(pic.getPicturePath());
            if(imgFile.exists()){
                this.sourceBitmap = BitmapFactory.decodeFile(imgFile.getAbsolutePath());
//                this.cropBitmap = ProcessImage.processBitmap(sourceBitmap, TF_OD_API_INPUT_SIZE);
                this.cropBitmap = Bitmap.createScaledBitmap(sourceBitmap, 412, 412, true);
                image.setImageBitmap(cropBitmap);
                initBox();
            }

            image.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {

                    if(indicatorRecycler.getVisibility() == View.GONE){
                        indicatorRecycler.setVisibility(View.VISIBLE);
                    }else{
                        indicatorRecycler.setVisibility(View.GONE);
                    }

                }
            });



            ((ViewPager) containerCollection).addView(view);
            return view;
        }

        @Override
        public void destroyItem(ViewGroup containerCollection, int position, Object view) {
            ((ViewPager) containerCollection).removeView((View) view);
        }

        @Override
        public boolean isViewFromObject(@NonNull View view, @NonNull Object object) {
            return view == ((View) object);
        }

        private void initBox(){
            previewHeight = TF_OD_API_INPUT_SIZE;
            previewWidth = TF_OD_API_INPUT_SIZE;

            frameToCropTransform = ImageUtils.getTransformationMatrix(
                    previewWidth,previewHeight, TF_OD_API_INPUT_SIZE, TF_OD_API_INPUT_SIZE, sensorOrientation, MAINTAIN_ASPECT
                    );

            cropToFrameTransform = new Matrix();
            frameToCropTransform.invert(cropToFrameTransform);

            tracker = new MultiBoxTracker(imagePager.getContext());
            trackingOverlay = view.findViewById(R.id.tracking_overlay);
            trackingOverlay.addCallback(
                    canvas -> tracker.draw(canvas)
            );

            tracker.setFrameConfiguration(TF_OD_API_INPUT_SIZE, TF_OD_API_INPUT_SIZE, sensorOrientation);
            try{
                detector = TFLiteObjectDetectionAPIModel.create(
                        imagePager.getContext(),
                        TF_OD_API_MODEL_FILE,
                        TF_OD_API_LABELS_FILE,
                        TF_OD_API_INPUT_SIZE,
                        TF_OD_API_IS_QUANTIZED
                );
            }catch (final IOException e){
                e.printStackTrace();
//                Logger.e(e, "Exception initalizing detector");

                Toast toast = Toast.makeText(getContext(), "classifier could be initialized", Toast.LENGTH_SHORT);
                toast.show();
            }
        }

        private void handleResult(Bitmap bitmap, List<Detector.Recognition> results){
            final Canvas canvas = new Canvas(bitmap);
            final Paint paint = new Paint();
            paint.setColor(Color.RED);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(2.0f);

            final List<Detector.Recognition> mappedRecognitions = new LinkedList<Detector.Recognition>();

            for (final Detector.Recognition result : results){
                final RectF location = result.getLocation();
                if(location != null && result.getConfidence() >= MINIMUM_CONFIDENCE_TF_OD_API){
                    canvas.drawRect(location, paint);
                }
            }
            image.setImageBitmap(bitmap);
        }
    }

    private void visibiling(){
        viewVisibilityController = 1;
        final int checker = viewVisibilityLooper;
        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                if(viewVisibilityLooper > checker){
                    visibiling();
                }else{
                    indicatorRecycler.setVisibility(View.GONE);
                    viewVisibilityController = 0;

                    viewVisibilityLooper = 0;
                }
            }
        }, 4000);
    }
}
