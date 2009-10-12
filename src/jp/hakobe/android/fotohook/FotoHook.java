package jp.hakobe.android.fotohook;

import jp.hakobe.android.fotohook.IFotoHookPostService;
import jp.hakobe.android.fotohook.R;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.ComponentName;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.DialogInterface.OnClickListener;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.RemoteException;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

public class FotoHook extends Activity implements DialogInterface.OnClickListener {
	private EditText title_edit_text = null;
	private IFotoHookPostService fotoHookPostService = null;
	private AlertDialog mainDialog = null;
	
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        this.prepareForService();
    }

    @Override
    protected void onStart() {
    	super.onStart();
    }
    
    @Override
    protected void onResume() {
    	super.onResume();
        this.runDialog();
        this.validate();
    }

	@Override
    protected void onDestroy() {
    	super.onDestroy();
    	this.mainDialog.dismiss();
    	unbindService(fotoHookPostServiceConn);
    }

	private void validate() {
		boolean valid = true;
		
		String user_id = FotoHookSetting.getUserID(this);
		String password = FotoHookSetting.getPassword(this);
		String hookUrl = FotoHookSetting.getHookUrl(this);
		
		if (user_id == null || user_id.length() <= 0 )  {
			valid = false;
		}
		
		if (password == null || password.length() <= 0) {
			valid = false;
		}
		
		if (!valid) {
			this.showErrorDialog(R.string.error_validate);
		}
	}
	
	private void showErrorDialog(int messageId) {
    	new AlertDialog.Builder(this)
		.setTitle(R.string.error_dialog_title)
		.setMessage(messageId)
		.setPositiveButton("OK", new OnClickListener() {			
			public void onClick(DialogInterface dialog, int which) {
				FotoHook.this.finish();
			}
		})
		.show();		
	}
	
	private void runDialog() {
		if (this.mainDialog == null) {
			this.setupMainDialog();
		}
    	this.mainDialog.show();
	}
	
	private void stopDialog() {
		this.mainDialog.dismiss();
	}
	
	private void setupMainDialog() {
    	LinearLayout layout = new LinearLayout(this);
    	layout.setOrientation(LinearLayout.VERTICAL);
    	
    	TextView title_text_view = new TextView(this);
    	title_text_view.setText(R.string.title);
    	layout.addView(title_text_view, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.FILL_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    	
    	this.title_edit_text = new EditText(this);
    	layout.addView(title_edit_text, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.FILL_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    	
    	this.mainDialog = new AlertDialog.Builder(this)
    		.setTitle(R.string.app_name)
    		.setView(layout)
			.setPositiveButton(R.string.submit, this)
			.setNegativeButton(R.string.cancel, this)
			.create();		
	}
	
	private void prepareForService() {
    	Intent intent = new Intent(IFotoHookPostService.class.getName());
    	startService(intent);
        bindService(intent, fotoHookPostServiceConn,BIND_AUTO_CREATE);				
	}
    
	public void onClick(DialogInterface dialog, int which) {
		if (which == DialogInterface.BUTTON_POSITIVE) {
			final String title = this.title_edit_text.getText().toString();			
			final Uri uri = this.getIntent().getParcelableExtra(Intent.EXTRA_STREAM);
	        try {
				fotoHookPostService.post(uri, title);
			} catch (RemoteException e) {
				e.printStackTrace();
				FotoHook.this.showErrorDialog(R.string.error_post_fail);
			}			
		}
		this.finish();
	}
	
    private ServiceConnection fotoHookPostServiceConn = new ServiceConnection() {

		public void onServiceConnected(ComponentName name, IBinder binder) {
			fotoHookPostService = IFotoHookPostService.Stub.asInterface(binder);
		}

		public void onServiceDisconnected(ComponentName name) {
			fotoHookPostService = null;
		}
		
	};
}
