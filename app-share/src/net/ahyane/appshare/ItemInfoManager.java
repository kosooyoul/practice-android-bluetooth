package net.ahyane.appshare;

import java.io.File;

import android.content.Context;
import android.content.Intent;
import android.content.pm.IPackageInstallObserver;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.pm.PackageParser;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.DisplayMetrics;
import android.webkit.MimeTypeMap;

public class ItemInfoManager {
	private static Context mContext = null;
	
	//main icons
	public static Drawable iconDefaultApp = null;
	public static Drawable iconNeedsInstall = null;
	
	//sub icons
	public static Drawable iconNotExists = null;
	public static Drawable iconCannotRead = null;
	public static Drawable iconCannotWrite = null;
	public static Drawable iconLinked = null;
	public static Drawable iconChecked = null;
	public static Drawable iconUnchecked = null;
	public static Drawable iconFavorit = null;
	
	//layout
    private static final int sDefaultIconSize = 32;
    public static int sizeIcon;
    
	private static ItemInfoManager mFileInfoManager = null;
	private ItemInfoManager(){super();}
	public static ItemInfoManager getInstance(Context context){
		if(mFileInfoManager == null){
			mFileInfoManager = new ItemInfoManager();

			mContext = context;
		
			//main icon
			iconDefaultApp = mContext.getResources().getDrawable(R.drawable.exic_apk);
			iconNeedsInstall = mContext.getResources().getDrawable(R.drawable.exic_needs_install);
			
		    //sub icon
			iconNotExists = mContext.getResources().getDrawable(R.drawable.scic_not_exists);
			iconCannotRead = mContext.getResources().getDrawable(R.drawable.scic_cannot_read);
			iconCannotWrite = mContext.getResources().getDrawable(R.drawable.scic_cannot_write);
			iconLinked = mContext.getResources().getDrawable(R.drawable.scic_linked);
			iconChecked = mContext.getResources().getDrawable(R.drawable.scic_check);
			iconUnchecked = mContext.getResources().getDrawable(R.drawable.scic_uncheck);
			iconFavorit = mContext.getResources().getDrawable(R.drawable.scic_favorit);
			
			//layout
		    sizeIcon = (int)(sDefaultIconSize * (context.getResources().getDisplayMetrics().density + 0.5f));
		}
		return mFileInfoManager;
	}
	
	public static PackageParser.Package getPackage(String filepath) {
		PackageParser packageParser = new PackageParser(filepath);
		File sourceFile = new File(filepath);
		DisplayMetrics metrics = new DisplayMetrics();
		metrics.setToDefaults();
		PackageParser.Package pkg =  packageParser.parsePackage(sourceFile, filepath, metrics, 0);
		// Nuke the parser reference.
		packageParser = null;
		return pkg;
	}
	
	public static boolean isASEC(ItemInfo info){
		return info.mcSourceDir.startsWith("/mnt/asec/");
	}
	
	public static boolean isASEC(String filepath){
		return filepath.startsWith("/mnt/asec/");
	}

	public static boolean isSystem(ItemInfo info){
		return info.mcSourceDir.startsWith("/system/app/");
	}
	
	public static boolean isSystem(String filepath){
		return filepath.startsWith("/system/app/");
	}
	
	public static Intent getInstallIntent(String filepath){
		final Uri uri = Uri.fromFile(new File(filepath));
		final Intent intent = new Intent(Intent.ACTION_VIEW, uri);

		//또는 MimeTypeMap에서 파일명의 확장자로 mimeType을 가져옴
		String extension = "apk";//MimeTypeMap.getFileExtensionFromUrl(file.getPath());
		String mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension);
		intent.setDataAndType(uri, mimeType);
		
		return intent;
	}
	
	//INSTALL_PACKAGE퍼미션을 가진 시스템앱만 가능
	public static void install(PackageManager pm, ItemInfo info){
		if(info.mType != ItemInfo.APP_TYPE_FILE) return;
		
		final Uri uri = Uri.fromFile(new File(info.mExternalFilepath));
		pm.installPackage(uri, new IPackageInstallObserver() {
			@Override
			public IBinder asBinder() {
				return null;
			}
			@Override
			public void packageInstalled(String packageName, int returnCode) throws RemoteException {
				//TODO::
			}
		}, 0, info.mPackageName);
	}
}
