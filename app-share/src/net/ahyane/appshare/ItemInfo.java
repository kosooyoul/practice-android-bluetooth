package net.ahyane.appshare;

import java.io.File;
import java.text.NumberFormat;

import android.app.Activity;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.pm.PackageParser;
import android.content.pm.PackageParser.Package;
import android.content.pm.PackageUserState;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PaintFlagsDrawFilter;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;

public class ItemInfo{
    private static final Paint sPaint;
    static{
    	sPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    	sPaint.setColor(Color.WHITE);
    }

    public static final int APP_TYPE_INSTALLED = 0;
    public static final int APP_TYPE_FILE = 1;
    public static final int APP_TYPE_FILE_FIX = 2;
	public static final int MODDE_NORMAL = 0;
	public static final int MODDE_MULTI_CHECK = 1;
	
	//default status
	public String mPackageName = null;
	public int mType = APP_TYPE_INSTALLED;
	public boolean isSelected = false;
	public boolean isHidden = false;
	public boolean isASEC = false;

	public String mExternalFilepath = null;
	private Package mExternalPackage = null;
	private PackageInfo mExternalPackageInfo = null;
	
	//cache
	public boolean isNeedsUpdate = true;
	//NonUI Cache
	public String mcSourceDir = null;
	public String mcPublicSourceDir = null;
	public Drawable mFixedIcon = null;
	
	//UI Cache
	public Drawable mcIcon = null;
	public String mcTitleFull = null;
	public String mcTitleLine1 = null;
	public String mcTitleLine2 = null;
	public long mcSourceFilesize = 0;
	public String mcSourceFilesizeText = null;
		
	public ItemInfo(String packageName, int type) {
		mPackageName = packageName;
		mType = type;
	}

	public ItemInfo(String filepath, String appname, Drawable icon) {
		mExternalFilepath = filepath;
		mExternalPackage = ItemInfoManager.getPackage(filepath);
		mPackageName = mExternalPackage.packageName;
		mType = APP_TYPE_FILE;

		mType = APP_TYPE_FILE_FIX;
		mcTitleFull = appname;
		mFixedIcon = icon;
	}

	public void makeDefault(PackageManager pm){
    	if(mType == APP_TYPE_INSTALLED){
			try {
				ApplicationInfo appInfo = pm.getApplicationInfo(mPackageName, PackageManager.GET_META_DATA);
				mcSourceDir = appInfo.sourceDir;
				mcPublicSourceDir = appInfo.publicSourceDir;
				isASEC = ItemInfoManager.isASEC(mcSourceDir);
				mcSourceFilesize = new File(mcSourceDir).length();

				NumberFormat formatter = NumberFormat.getInstance();
				String sizeText = formatter.format(mcSourceFilesize / 1024);
				mcSourceFilesizeText = sizeText + " KB";
			} catch (NameNotFoundException e) {
	
			}
    	}else if(mType == APP_TYPE_FILE){
    		mExternalPackageInfo = PackageParser.generatePackageInfo(mExternalPackage, null, PackageManager.GET_PERMISSIONS, 0, 0, null, new PackageUserState());
    	}
	}
	
	public void makeIcon(PackageManager pm, Context context) {
    	Drawable appIcon = null;
    	if(mType == APP_TYPE_INSTALLED){
	    	try {
	    		appIcon = pm.getApplicationIcon(mPackageName);
			} catch (NameNotFoundException e1) {
				appIcon = ItemInfoManager.iconDefaultApp;
			}
    	}else if(mType == APP_TYPE_FILE){
    		//2.
    		//appIcon = mExternalPackage.applicationInfo.loadIcon(pm);

    		//3.
			appIcon = pm.getApplicationIcon(mExternalPackageInfo.applicationInfo);
    	}else if(mType == APP_TYPE_FILE_FIX){
    		appIcon = mFixedIcon;
    	}

        Rect bounds = new Rect();
        int width = ItemInfoManager.sizeIcon;
        int height = ItemInfoManager.sizeIcon;

        final Bitmap.Config c = Bitmap.Config.ARGB_8888;//appIcon.getOpacity() != PixelFormat.OPAQUE? Bitmap.Config.ARGB_8888: Bitmap.Config.RGB_565;
        final Bitmap thumb = Bitmap.createBitmap(width, height, c);
        final Canvas canvas = new Canvas(thumb);
        canvas.setDrawFilter(new PaintFlagsDrawFilter(Paint.DITHER_FLAG, 0));

        int alpha = isHidden? 100: 255;
            
        //Draw Main Icon
        appIcon.setAlpha(alpha);
        bounds.set(appIcon.getBounds());
        appIcon.setBounds(0, 0, width, height);
        appIcon.draw(canvas);
        appIcon.setBounds(bounds);
            
        //Draw Sub Icon
        if(isASEC){
        	Drawable secondIcon = ItemInfoManager.iconCannotRead;
        	secondIcon.setAlpha(alpha);
        	bounds.set(secondIcon.getBounds());
        	secondIcon.setBounds(width / 2, height / 2, width, height);
        	secondIcon.draw(canvas);
        	secondIcon.setBounds(bounds);
        }
        
        //Needs install
        if(mExternalPackage != null){
        	Drawable secondIcon = ItemInfoManager.iconNeedsInstall;
        	secondIcon.setAlpha(alpha);
        	bounds.set(secondIcon.getBounds());
        	secondIcon.setBounds(0, 0, width, height);
        	secondIcon.draw(canvas);
        	secondIcon.setBounds(bounds);
        }
        
        //not exist
		//{
		//	Drawable secondIcon = ItemInfoManager.iconCannotWrite;
		//	secondIcon.setAlpha(alpha);
		//	bounds.set(secondIcon.getBounds());
		//	secondIcon.setBounds(width / 2, height / 2, width, height);
		//	secondIcon.draw(canvas);
		//	secondIcon.setBounds(bounds);
		//}
//            if(!itemInfo.mFile.exists()){
//            	Drawable secondIcon = FileInfoManager.iconNotExists;
//            	secondIcon.setAlpha(alpha);
//            	mOldBounds.set(secondIcon.getBounds());
//            	secondIcon.setBounds(width / 2, height / 2, width, height);
//            	secondIcon.draw(canvas);
//            	secondIcon.setBounds(mOldBounds);
//            //cannot read file icon
//            }else if(!itemInfo.mFile.canRead()){
//            	Drawable secondIcon = FileInfoManager.iconCannotRead;
//            	secondIcon.setAlpha(alpha);
//            	mOldBounds.set(secondIcon.getBounds());
//            	secondIcon.setBounds(width / 2, height / 2, width, height);
//            	secondIcon.draw(canvas);
//            	secondIcon.setBounds(mOldBounds);
//            //cannot write file icon
//            }else if(!itemInfo.mFile.canWrite()){
//            	Drawable secondIcon = FileInfoManager.iconCannotWrite;
//            	secondIcon.setAlpha(alpha);
//            	mOldBounds.set(secondIcon.getBounds());
//            	secondIcon.setBounds(width / 2, height / 2, width, height);
//            	secondIcon.draw(canvas);
//            	secondIcon.setBounds(mOldBounds);                	
//            }
        
		//link file icon
		//if(itemInfo.isLinked){
		//	Drawable secondIcon = ItemInfoManager.iconLinked;
		//	secondIcon.setAlpha(alpha);
		//	bounds.set(secondIcon.getBounds());
		//	secondIcon.setBounds(0, height / 2, width / 2, height);
		//	secondIcon.draw(canvas);
		//	secondIcon.setBounds(bounds);                	
		//}

        //Draw Sub Icon
        //multi select mode and item selected
		//if(isSelected){
		//	Drawable secondIcon = ItemInfoManager.iconChecked;
		//	//secondIcon.setAlpha(alpha);
		//	bounds.set(secondIcon.getBounds());
		//	secondIcon.setBounds(width / 2, 0, width, height / 2);
		//	secondIcon.draw(canvas);
		//	secondIcon.setBounds(bounds);
		//}else{
		//	Drawable secondIcon = ItemInfoManager.iconUnchecked;
		//	//secondIcon.setAlpha(alpha);
		//	bounds.set(secondIcon.getBounds());
		//	secondIcon.setBounds(width / 2, 0, width, height / 2);
		//	secondIcon.draw(canvas);
		//	secondIcon.setBounds(bounds);
		//}
        mcIcon = new BitmapDrawable(context.getResources(), thumb);
	}

	public void makeTitle(PackageManager pm) {
		if(mType == APP_TYPE_INSTALLED){
	    	try {
				ApplicationInfo appInfo = pm.getApplicationInfo(mPackageName, PackageManager.GET_META_DATA);
				mcTitleFull = appInfo.name;
				if(mcTitleFull == null){
					mcTitleFull = String.valueOf(pm.getApplicationLabel(appInfo));
				}
			} catch (NameNotFoundException e) {
				mcTitleFull = mPackageName;
			}
		}else if(mType == APP_TYPE_FILE){
			//1.
			//mcTitleFull = mExternalPackage.applicationInfo.name;
			//if(mcTitleFull == null){
			//	mcTitleFull = String.valueOf(pm.getApplicationLabel(mExternalPackage.applicationInfo));
			//}
			//2.
			//mcTitleFull = (String) mExternalPackage.applicationInfo.loadLabel(pm);
			//3.
    		mcTitleFull = (String) pm.getApplicationLabel(mExternalPackageInfo.applicationInfo);
		}

    	String title1 = null;
    	String title2 = null;
        if(sPaint.measureText(mcTitleFull.trim()) <= 64.0f){
        	title1 = mcTitleFull.trim();
        	title2 = "";
        }else{
        	for(int i = 2; i < mcTitleFull.length() + 1; i++){
        		if(sPaint.measureText(mcTitleFull.substring(0, i).trim()) > 64.0f){
        			title1 = mcTitleFull.substring(0, (i - 1)).trim();
        			title2 = mcTitleFull.substring(i - 1).trim();
        			break;
        		}
        	}
        }
        mcTitleLine1 = title1;
        mcTitleLine2 = title2;
	}
	
	public View createIsolatedView(Context context){
		final LayoutInflater inflater = ((Activity)context).getLayoutInflater();
		View view = inflater.inflate(R.layout.item, null, false);
        
	    if(isNeedsUpdate){
	        PackageManager pm = context.getPackageManager();
	        
	        //non ui default
	        makeDefault(pm);
	
	        //icon
	        {
	        	makeIcon(pm, context);
	        }
	
	        //title
	        {
	        	makeTitle(pm);
	        }
	        
	        isNeedsUpdate = false;
	    }

        //Set UI
        final TextView textView1 = (TextView) view.findViewById(R.id.label1);
        final TextView textView2 = (TextView) view.findViewById(R.id.label2);
        textView1.setCompoundDrawablesWithIntrinsicBounds(null, mcIcon, null, null);
        textView1.setText(mcTitleLine1);
        textView2.setText(mcTitleLine2);
        
        return view;
	}

}
