package edu.stanford.junction.sample.partyware;

import edu.stanford.junction.sample.partyware.util.Misc;

import android.content.ServiceConnection;
import android.content.ContentValues;
import android.content.Context;
import android.os.Bundle;
import android.os.Process;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.BroadcastReceiver;
import android.app.Service;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.ImageView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import android.net.Uri;
import android.util.Log;

import org.json.*;

import java.net.*;
import java.io.*;
import java.util.*;


public class AddPictureActivity extends Activity{

	public final static String EXTRA_COMMENT = "edu.stanford.junction.sample.partyware.IMAGE_COMMENT";
	public final static String LAUNCH_INTENT = "edu.stanford.junction.sample.partyware.ADD_PICTURE";

	public final static int REQUEST_CODE_PICK_FROM_LIBRARY = 0;
	public final static int REQUEST_CODE_TAKE_PICTURE = 1;

	private ImageView mPreviewImage;
	private TextView mUriView;
	private Uri mUri;
	private ProgressDialog mUploadProgressDialog;
	private BroadcastReceiver mUriReceiver;
	private BroadcastReceiver mErrorReceiver;

	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.add_picture);

		mPreviewImage = (ImageView)findViewById(R.id.preview);
		mPreviewImage.setScaleType(ImageView.ScaleType.FIT_CENTER);

		EditText txt = (EditText)findViewById(R.id.comment_text);
		txt.setHint(R.string.add_caption);
		String comment = txt.getText().toString();

		mUriView = (TextView)findViewById(R.id.uri_view);

		Button button = (Button)findViewById(R.id.use_camera_button);
		button.setOnClickListener(new OnClickListener() {
				public void onClick(View v) {
					takePicture();
				}
			});

		button = (Button)findViewById(R.id.pick_from_library_button);
		button.setOnClickListener(new OnClickListener() {
				public void onClick(View v) {
					pickFromLibrary();
				}
			});

		button = (Button)findViewById(R.id.finished_button);
		button.setOnClickListener(new OnClickListener() {
				public void onClick(View v) {
					confirm();
				}
			});

		button = (Button)findViewById(R.id.cancel_button);
		button.setOnClickListener(new OnClickListener() {
				public void onClick(View v) {
					cancel();
				}
			});
	}

	protected void takePicture(){
		Intent i = new Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE);
		if (Misc.hasImageCaptureBug()) {
			i.putExtra(android.provider.MediaStore.EXTRA_OUTPUT, 
					   Uri.fromFile(new File("/sdcard/tmp")));
		} 
		else {
			i.putExtra(android.provider.MediaStore.EXTRA_OUTPUT, 
					   android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
		}
		startActivityForResult(i, REQUEST_CODE_TAKE_PICTURE);
	}

	protected void pickFromLibrary(){
		Intent i = new Intent(Intent.ACTION_PICK, 
							  android.provider.MediaStore.Images.Media.INTERNAL_CONTENT_URI);
		startActivityForResult(i, REQUEST_CODE_PICK_FROM_LIBRARY);
	}

	protected void startUpload(Uri localUri){

		mUriReceiver = new BroadcastReceiver() {
				@Override
				public void onReceive(Context context, Intent intent) {
					mUploadProgressDialog.dismiss();
					mUri = Uri.parse(intent.getStringExtra("image_url"));
					mUriView.setText(mUri.toString());
				}
			};
		IntentFilter intentFilter = new IntentFilter(ImgurUpload.BROADCAST_FINISHED);
		registerReceiver(mUriReceiver, intentFilter); 

		mErrorReceiver = new BroadcastReceiver() {
				@Override
				public void onReceive(Context context, Intent intent) {
					mUploadProgressDialog.dismiss();
					String error = intent.getStringExtra("error");
					showDialog(error);
				}
			};
		intentFilter = new IntentFilter(ImgurUpload.BROADCAST_FAILED);
		registerReceiver(mErrorReceiver, intentFilter);

		Intent i = new Intent(this, ImgurUpload.class);
		i.setAction(Intent.ACTION_SEND);
		i.putExtra(Intent.EXTRA_STREAM, localUri);
		startService(i);

		mUploadProgressDialog = ProgressDialog.show(this,"","Uploading. Please wait...",true);

	}


	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {
		super.onActivityResult(requestCode, resultCode, data);
		switch(requestCode) {
		case REQUEST_CODE_PICK_FROM_LIBRARY:
			if(resultCode == RESULT_OK){
				Uri localUri = data.getData();
				mPreviewImage.setImageURI(localUri);
				startUpload(localUri);
			}
			break;
		case REQUEST_CODE_TAKE_PICTURE:
			if(resultCode == RESULT_OK){
				Uri localUri;
				if (Misc.hasImageCaptureBug()) {
					File fi = new File("/sdcard/tmp");
					try {
						localUri = Uri.parse(
							android.provider.MediaStore.Images.Media.insertImage(
								getContentResolver(), 
								fi.getAbsolutePath(), null, null));
						if (!fi.delete()) {
							Log.i("AddPictureActivity", "Failed to delete " + fi);
						}
						mPreviewImage.setImageURI(localUri);
						startUpload(localUri);
					} catch (FileNotFoundException e) {
						e.printStackTrace();
					}
				} else {
					localUri = data.getData();
					mPreviewImage.setImageURI(localUri);
					startUpload(localUri);
                }
				
			}
			break;
		}
	}


	private void showDialog(String message) {
		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		builder.setMessage(message);
		builder.setPositiveButton("OK", null);
		builder.show();
	}


    protected void confirm(){
		if(mUri == null){
			Toast.makeText(this, R.string.no_image_selected, 
						   Toast.LENGTH_SHORT).show();
		}
		else{
			// stop the image uploader service
			Intent i = new Intent(this, ImgurUpload.class);
			stopService(i);

			// return the uri with comment
			Intent intent = new Intent();
			EditText txt = (EditText)findViewById(R.id.comment_text);
			String comment = txt.getText().toString();
			intent.setData(mUri);
			intent.putExtra(EXTRA_COMMENT, comment);
			setResult(RESULT_OK, intent);

			finish();
		}
    }

    protected void cancel(){
		Intent intent = new Intent();
		setResult(RESULT_CANCELED, intent);
		finish();
	}


	public void onDestroy(){
		super.onDestroy();
		try{
			unregisterReceiver(mUriReceiver);
			unregisterReceiver(mErrorReceiver);

			Intent i = new Intent(this, ImgurUpload.class);
			stopService(i);
		}
		catch(IllegalArgumentException e){}
	}

}



