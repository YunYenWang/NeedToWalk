package ntw.idea.org.ntw;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.RelativeLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;

/**
 * Created by rickwang on 2015/06/16.
 */
public class WallView extends RelativeLayout {
    WallAdapter adapter;
    ListView wall;
    List<WalkResult> results = Collections.synchronizedList(new ArrayList<WalkResult>());

    public WallView(Context context) {
        super(context);

        onCreate(context);
    }

    public WallView(Context context, AttributeSet attrs) {
        super(context, attrs);

        onCreate(context);
    }

    protected void onCreate(Context context) {
        adapter = new WallAdapter();
        wall = new ListView(context);
        wall.setAdapter(adapter);

        addView(wall);
    }

    protected void notifyDataAppend() {
        post(new Runnable() {
            @Override
            public void run() {
                adapter.notifyDataSetChanged();
                wall.setSelection(adapter.getCount() - 1);
            }
        });
    }

    protected void notifyDataChanged() {
        post(new Runnable() {
            @Override
            public void run() {
                adapter.notifyDataSetChanged();
            }
        });
    }

    public void addWalkResult(WalkResult result) {
        results.add(result);

        notifyDataAppend();
    }

    class WallAdapter extends BaseAdapter {

        public WallAdapter() {
        }

        @Override
        public int getCount() {
            return results.size();
        }

        @Override
        public WalkResult getItem(int position) {
            return results.get(position);
        }

        @Override
        public long getItemId(int position) {
            return 0;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            WallItem wi = (WallItem) convertView;
            if (wi == null) {
                wi = new WallItem(getContext());
            }

            WalkResult result = getItem(position);
            wi.setWalkResult(result);

            return wi;
        }
    }

    class WallItem extends RelativeLayout {

        public WallItem(Context context) {
            super(context);
            inflate(context, R.layout.view_wall_item, this);
        }

        public void setWalkResult(WalkResult result) {
            Resources res = getResources();

            setIcon(result.isSuccess() ? res.getDrawable(R.drawable.successful) : res.getDrawable(R.drawable.failed));
            setTimestamp(result.getStart(), result.getEnd());
            setSteps(result.getSteps());
        }

        public void setIcon(Drawable drawable) {
            ImageView iv = (ImageView) findViewById(R.id.success);
            iv.setImageDrawable(drawable);
        }

        public void setTimestamp(long start, long end) {
            TextView tv = (TextView) findViewById(R.id.timestamp);
            tv.setText(Constants.DATETIME_FORMAT.format(new Date(start)) + " ~ " + Constants.DATETIME_FORMAT.format(new Date(end)));
        }

        public void setSteps(int steps) {
            TextView tv = (TextView) findViewById(R.id.steps);
            tv.setText(String.format("%,d", steps));
        }
    }
}
