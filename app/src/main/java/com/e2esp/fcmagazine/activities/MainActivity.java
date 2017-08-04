package com.e2esp.fcmagazine.activities;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.AsyncTask;
import android.os.Build;
import android.os.CountDownTimer;
import android.os.Environment;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.support.annotation.RequiresApi;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.widget.DefaultItemAnimator;
import android.support.v7.widget.GridLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.crashlytics.android.Crashlytics;
import com.dropbox.core.DbxRequestConfig;
import com.dropbox.core.v2.DbxClientV2;
import com.dropbox.core.v2.files.FileMetadata;
import com.e2esp.fcmagazine.R;
import com.e2esp.fcmagazine.adapters.MagazineRecyclerAdapters;
import com.e2esp.fcmagazine.interfaces.OnMagazineClickListener;
import com.e2esp.fcmagazine.models.Magazines;
import com.e2esp.fcmagazine.utils.PermissionManager;
import com.google.firebase.messaging.FirebaseMessaging;

import org.apache.commons.io.FileUtils;

import io.fabric.sdk.android.Fabric;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;

/**
 * Created by Zain on 2/10/2017.
 */

public class MainActivity extends AppCompatActivity {

    private RecyclerView recyclerViewMagazines;
    private ArrayList<Magazines> magazinesListLatest;
    private ArrayList<Magazines> magazinesListRecent;
    private ArrayList<Magazines> magazinesListDownloaded;
    private MagazineRecyclerAdapters magazineRecyclerAdapter;

    private static final String ACCESS_TOKEN = "t3HP7BPiD2AAAAAAAAAAHzZCvsP_y-pkY1kv0PCAPSdxi13bKay5dwS0xQbRsWqE";

    private DbxRequestConfig config = null;
    DbxClientV2 client = null;

    private Animation animationTitleIn;
    private Animation animationTitleOut;
    private int coverPagesInStorage;
    private static boolean isSubscribe;

    File magDir;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Fabric.with(this, new Crashlytics());
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);

        setContentView(R.layout.activity_main);

        boolean permissionCheck = PermissionManager.getInstance().hasPermission(MainActivity.this, Manifest.permission.WRITE_EXTERNAL_STORAGE);

        if(permissionCheck==true) {

            File externalStorage = new File(Environment.getExternalStorageDirectory(), "FC Magazine");
            if (externalStorage.isDirectory()) {

                File internalStorage = getFilesDir();
                for (File file : externalStorage.listFiles()) {

                    if (file.isDirectory()) {

                            try {
                                FileUtils.copyDirectoryToDirectory(file,internalStorage);

                                deleteMagazine(file);

                            } catch (IOException e) {
                                e.printStackTrace();
                            }

                    }//outer if end

                }

            }
            deleteMagazine(externalStorage);

        }
        createDirs();
        setupView();

    }


    public void deleteMagazine(File magazinesName) {

        if (magazinesName.isDirectory()) {
            for (File child : magazinesName.listFiles()) {
                deleteMagazine(child);
            }
        }
        magazinesName.delete();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.subscribe_action_bar, menu);
        setActionBar(menu);
        return super.onCreateOptionsMenu(menu);
    }

    public void setActionBar(Menu menu) {

        ActionBar actionBar = getSupportActionBar();
        TextView magazineName = new TextView(MainActivity.this);
        magazineName.setText(getString(R.string.app_name));
        magazineName.setTextColor(Color.parseColor("#000000"));
        actionBar.setDisplayOptions(ActionBar.DISPLAY_SHOW_CUSTOM);
        actionBar.setCustomView(magazineName);
        actionBar.setBackgroundDrawable(new ColorDrawable(0xffdcdcdc));

        SharedPreferences subscribeCheck=getSharedPreferences("subscribeClick", Context.MODE_PRIVATE);
        boolean subscribed = subscribeCheck.getBoolean("isSubscribe", false);
        if(subscribed==true){
            MenuItem subscribeItem = menu.findItem(R.id.subscribe);
            subscribeItem.setTitle("Unsubscribe");
        }else{
            MenuItem subscribeItem = menu.findItem(R.id.subscribe);
            subscribeItem.setTitle("Subscribe");
        }
        actionBar.show();

    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Take appropriate action for each action item click
        switch (item.getItemId()) {
            case R.id.subscribe:

                if(item.getTitle().equals("Subscribe")) {
                    FirebaseMessaging.getInstance().subscribeToTopic("FCMagazine");
                    item.setTitle("Unsubscribed");
                    clickOnSubscribe();
                }else {
                    FirebaseMessaging.getInstance().unsubscribeFromTopic("FCMagazine");
                    item.setTitle("Subscribe");
                    unSubscribed();
                }

                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    public void clickOnSubscribe(){

        SharedPreferences subscribe=getSharedPreferences("subscribeClick", Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = subscribe.edit();

        isSubscribe = true;
        editor.putBoolean("isSubscribe",isSubscribe);
        editor.commit();

        AlertDialog.Builder builder1 = new AlertDialog.Builder(MainActivity.this);
        builder1.setMessage("Thank you for subscribing. We will download future issues for you as soon as they are published.");
        builder1.setCancelable(true);

        builder1.setPositiveButton(
                "OK",
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        dialog.dismiss();
                    }
                });

        AlertDialog subscribeDialog = builder1.create();
        subscribeDialog.show();
    }

    private void unSubscribed(){


        SharedPreferences subscribe=getSharedPreferences("subscribeClick", Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = subscribe.edit();

        isSubscribe = false;
        editor.putBoolean("isSubscribe",isSubscribe);
        editor.commit();
        AlertDialog.Builder builder1 = new AlertDialog.Builder(MainActivity.this);
        builder1.setMessage("You have unsubscribed from magazine updates. Magazine will no longer auto download when published.\u000BPlease subscribe again to get best experience. ");
        builder1.setCancelable(true);

        builder1.setPositiveButton(
                "OK",
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        dialog.dismiss();
                    }
                });

        AlertDialog subscribeDialog = builder1.create();
        subscribeDialog.show();
    }


    private void createDirs() {

        magDir = new File(getFilesDir(), "Cover Pages");
        if (!magDir.isDirectory()) {
            magDir.mkdir();
        }
    }
    private void setupView() {

        config = DbxRequestConfig.newBuilder("FC Magazine").build();
        client = new DbxClientV2(config, ACCESS_TOKEN);

        GetFolderList folderList = new GetFolderList(MainActivity.this, client, new GetFolderList.Callback() {

            @Override
            public void onDownloadComplete(Integer result) {

                /*loadFromStorage();
                loadCoverPages();*/
                if (result == 0) {
                    Log.d("Error finding list ", "Error finding list");
                    if(coverPagesInStorage > 0){

                    }//inner if condition
                    else{
                        Toast.makeText(MainActivity.this, "Internet connection required to check updates", Toast.LENGTH_SHORT).show();
                    }
                }else {
                    DownloadCoverPages(coverPagesInStorage, result);
                    magazineRecyclerAdapter.notifyDataSetChanged();
                }
            }

            @Override
            public void onError(Exception e) {

                Log.d("Error finding list ", "Error finding list");
                Toast.makeText(MainActivity.this, "Error", Toast.LENGTH_SHORT).show();
            }
        });

        folderList.execute();

        File dir = new File(getFilesDir(), "Cover Pages");
        coverPagesInStorage = countFilesInDirectory(dir);
        //Toast.makeText(this, "Number of Cover Pages " +coverPagesInStorage, Toast.LENGTH_LONG).show();

        recyclerViewMagazines = (RecyclerView) findViewById(R.id.recyclerViewMagazines);
        magazinesListLatest = new ArrayList<>();
        magazinesListRecent = new ArrayList<>();
        magazinesListDownloaded = new ArrayList<>();
        magazineRecyclerAdapter = new MagazineRecyclerAdapters(this, magazinesListLatest, magazinesListRecent, magazinesListDownloaded, new OnMagazineClickListener() {
            @Override
            public void onDownloadClicked(Magazines magazine) {
                onDownloadClick(magazine);
            }

            @Override
            public void onCoverPageClicked(Magazines magazines) {

                onCoverPageClick(magazines);

            }
        });

        GridLayoutManager layoutManagerMagazines = new GridLayoutManager(this, 2, GridLayoutManager.VERTICAL, false);
        layoutManagerMagazines.setSpanSizeLookup(magazineRecyclerAdapter.getSpanSizeLookup());
        recyclerViewMagazines.setLayoutManager(layoutManagerMagazines);
        recyclerViewMagazines.setItemAnimator(new DefaultItemAnimator());
        recyclerViewMagazines.setAdapter(magazineRecyclerAdapter);

        loadFromStorage();
        loadCoverPages();

    }

    //count number of cover pages
    public static int countFilesInDirectory(File directory) {
        int count = 0;
        for (File file : directory.listFiles()) {
            if (file.isFile()) {
                //Log.d("File: ", "Cover Page  " +file.getName());
                count++;
            }
            if (file.isDirectory()) {
                count += countFilesInDirectory(file);
            }
        }
        return count;
    }

    //download cover pages
    public void DownloadCoverPages(int coverPagesInStorage, int coverPagesInDropbox){

        if(coverPagesInStorage != coverPagesInDropbox){

            DownloadCoverPage downloadCoverPage = new DownloadCoverPage(MainActivity.this, client, new DownloadCoverPage.Callback() {

                @Override
                public void onDownloadComplete(File result) {
                    Log.d("Download Complete", "onDownloadComplete: " +result);
                    loadFromStorage();
                    loadCoverPages();
                }

                @Override
                public void onError(Exception e) {

                    Toast.makeText(MainActivity.this, "Error", Toast.LENGTH_SHORT).show();
                }
            });
            downloadCoverPage.execute();

        }
        else{
            loadFromStorage();
            loadCoverPages();
        }
        //magazineRecyclerAdapter.notifyDataSetChanged();

    }

    public void loadFromStorage(){

        magazinesListLatest.clear();

        for (File file:magDir.listFiles()){

            Bitmap myBitmap = BitmapFactory.decodeFile(file.getAbsolutePath());
            //get name of file(Cover Pages)
            String magazineFilePath = file.getName();
            //remove the dot jpg extension
            String magazineName = magazineFilePath.substring(0, magazineFilePath.lastIndexOf("."));

            String convertToDate = magazineName;
            SimpleDateFormat dateFormat = new SimpleDateFormat("MMM yyyy");

            Date date = null;
            try {
                date = dateFormat.parse(convertToDate);

            }
            catch(ParseException pe) {
                Toast.makeText(this, "Error", Toast.LENGTH_SHORT).show();
            }

            magazinesListLatest.add(new Magazines(magazineName, myBitmap,date,false,0,0));


        }//for loop end


        Collections.sort(magazinesListLatest, new Comparator<Magazines>() {
            @Override
            public int compare(Magazines o2, Magazines o1) {
                if (o1.getDate() == null || o2.getDate() == null)
                    return 0;
                return o1.getDate().compareTo(o2.getDate());

            }
        });

    }

    private void loadCoverPages() {

        for(File magazineFiles:getFilesDir().listFiles()){

            String magazinesName = magazineFiles.getName();
            if(magazinesName.equals("Cover Pages")){
                Log.d("Do nothing","Do Nothing");
            }else {
                if (magazineFiles.isDirectory()) {
                    int magazinePagesInDirectory = countFilesInDirectory(magazineFiles);
                    if (magazinePagesInDirectory > 0) {
                        for (final Magazines magazine:magazinesListLatest) {
                            if(magazinesName.equals(magazine.getName())){
                                magazine.setDownloaded(true);
                                //magazineRecyclerAdapter.notifyDataSetChanged();

                                    //get list of selected magazine from dropbox
                                    GetMagazineList magazineList = new GetMagazineList(MainActivity.this, client,magazinesName,magazinePagesInDirectory, new GetMagazineList.Callback() {

                                        @Override
                                        public void onDownloadComplete(Integer result,int dirPageCount) {

                                            if(result == 0){
                                                Log.d("Magazine List","Magazine List");

                                            }
                                            else {

                                                if (dirPageCount == result) {

                                                    magazine.setDownloaded(true);

                                                }else{
                                                    magazine.setDownloaded(false);
                                                }
                                                magazineRecyclerAdapter.notifyDataSetChanged();
                                            }

                                        }

                                        @Override
                                        public void onError(Exception e) {

                                            Toast.makeText(MainActivity.this, "Error", Toast.LENGTH_SHORT).show();
                                        }
                                    });

                                    magazineList.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
                                break;
                            }

                        }
                    }//inner if condition
                }//outer if condition

            }//else condition



        }
        magazineRecyclerAdapter.notifyDataSetChanged();


    }

    private void adjustListSize() {
        if (magazinesListRecent.size() % 2 == 1) {
            magazinesListRecent.add(new Magazines(true));
        }
        if (magazinesListDownloaded.size() % 2 == 1) {
            magazinesListDownloaded.add(new Magazines(true));
        }

    }


    public void onCoverPageClick(Magazines magazine){

        magazine.getName();
        if(magazine.isDownloaded()==true) {
            ReaderActivity.magazines = magazine;
            Intent intent = new Intent(this, ReaderActivity.class);
            startActivityForResult(intent,1);

        }

    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if(requestCode==1) {

            if (resultCode == RESULT_OK) {
                loadFromStorage();
                loadCoverPages();
                magazineRecyclerAdapter.notifyDataSetChanged();
            }
        }
    }

    private void onDownloadClick(final Magazines magazine) {

        config = DbxRequestConfig.newBuilder("FC Magazine").build();
        client = new DbxClientV2(config, ACCESS_TOKEN);

        ConnectivityManager connManager = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
        NetworkInfo wifi = connManager.getNetworkInfo(ConnectivityManager.TYPE_WIFI);
        NetworkInfo mobile = connManager .getNetworkInfo(ConnectivityManager.TYPE_MOBILE);

        if (!wifi.isConnected() && !mobile.isConnected()){
            Toast.makeText(getApplicationContext(), " Please make sure, your network connection is ON", Toast.LENGTH_LONG).show();
            return;
        }

        magazine.setDownloading(true);
        magazineRecyclerAdapter.notifyDataSetChanged();

        DownloadFileTask downloadFile = new DownloadFileTask(MainActivity.this, client,magazine, new DownloadFileTask.Callback() {

            @Override
            public void onDownloadComplete() {
                magazine.setDownloading(false);
                magazineRecyclerAdapter.notifyDataSetChanged();
                   //loadCoverPages();
            }

            @Override
            public void updateProgress() {
                magazineRecyclerAdapter.notifyDataSetChanged();
            }

            @Override
            public void onError(Exception e) {

                Toast.makeText(MainActivity.this, "Error", Toast.LENGTH_SHORT).show();
            }
        });
        downloadFile.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);


    }

    private void createOverlayAnimations() {
        animationTitleIn = AnimationUtils.loadAnimation(this, R.anim.slide_in_downwards);
        animationTitleOut = AnimationUtils.loadAnimation(this, R.anim.slide_out_upwards);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        PermissionManager.getInstance().onResult(requestCode,permissions,grantResults);
    }

    private boolean backPressed = false;
    @Override
    public void onBackPressed() {
        if (backPressed) {
            super.onBackPressed();
            return;
        }

        backPressed = true;
        Toast.makeText(this, getString(R.string.press_back_again_to_exit), Toast.LENGTH_SHORT).show();

        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                backPressed = false;
            }
        }, 2000);
    }

}