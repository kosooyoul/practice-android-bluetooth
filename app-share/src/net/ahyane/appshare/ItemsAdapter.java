package net.ahyane.appshare;

import java.util.ArrayList;

import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

class ItemsAdapter extends ArrayAdapter<ItemInfo> {
    private ArrayList<ItemInfo> mItemInfoList = null;
    private Context mContext = null;
    
    //status
    private boolean isMultiSelectMode = false;
    
    public ItemsAdapter(Context context, ArrayList<ItemInfo> files) {
        super(context, 0, files);
        mItemInfoList = files;
        mContext = context;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
    	if(position < 0 || position > mItemInfoList.size() - 1)return null;
        		
        if (convertView == null) {
            final LayoutInflater inflater = ((Activity)mContext).getLayoutInflater();
            convertView = inflater.inflate(R.layout.item, parent, false);
        }
        
	    final ItemInfo itemInfo = mItemInfoList.get(position);
	    
	    if(itemInfo.isNeedsUpdate){
	        PackageManager pm = mContext.getPackageManager();
	        
	        //non ui default
	        itemInfo.makeDefault(pm);
	
	        //icon
	        {
	        	itemInfo.makeIcon(pm, mContext);
	        }
	
	        //title
	        {
	        	itemInfo.makeTitle(pm);
	        }
	        
	        itemInfo.isNeedsUpdate = false;
	    }

        //Set UI
        final TextView textView1 = (TextView) convertView.findViewById(R.id.label1);
        final TextView textView2 = (TextView) convertView.findViewById(R.id.label2);
        textView1.setCompoundDrawablesWithIntrinsicBounds(null, itemInfo.mcIcon, null, null);
        textView1.setText(itemInfo.mcTitleLine1);
        textView2.setText(itemInfo.mcTitleLine2);
        
        return convertView;
    }
    
    public void setMultiSelectMode(boolean enabled){
    	if(isMultiSelectMode != enabled){
    		int count = mItemInfoList.size();
    		for(int i = 0; i < count; i++){
    			mItemInfoList.get(i).isNeedsUpdate = true;
    		}
    	}
    	isMultiSelectMode = enabled;
    	if(!isMultiSelectMode){
    		int count = mItemInfoList.size();
    		for(int i = 0; i < count; i++){
    			mItemInfoList.get(i).isSelected = false;
    		}
    	}
    }

    public boolean isMultiSelectMode(){
    	return isMultiSelectMode;
    }
    
}