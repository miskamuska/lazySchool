package com.example.lazysch.RecycleView;

import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.AutoCompleteTextView;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.amap.api.location.AMapLocation;
import com.amap.api.location.AMapLocationClient;
import com.amap.api.location.AMapLocationClientOption;
import com.amap.api.location.AMapLocationListener;
import com.amap.api.maps.AMap;
import com.amap.api.maps.CameraUpdateFactory;
import com.amap.api.maps.LocationSource;
import com.amap.api.maps.MapView;
import com.amap.api.maps.model.BitmapDescriptorFactory;
import com.amap.api.maps.model.LatLng;
import com.amap.api.maps.model.Marker;
import com.amap.api.maps.model.MarkerOptions;
import com.amap.api.maps.model.MyLocationStyle;
import com.amap.api.services.core.AMapException;
import com.amap.api.services.core.LatLonPoint;
import com.amap.api.services.core.PoiItem;
import com.amap.api.services.poisearch.PoiResult;
import com.amap.api.services.poisearch.PoiSearch;
import com.example.lazysch.R;
import com.example.lazysch.utils.MyActivity;

import org.greenrobot.eventbus.EventBus;

import java.util.ArrayList;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;

public class MapActivity extends MyActivity implements AMapLocationListener, LocationSource, PoiSearch.OnPoiSearchListener {
    @BindView(R.id.mapview)
    MapView mapview;
    @BindView(R.id.tvBaiduJuli)
    TextView tvBaiduJuli;
    @BindView(R.id.seach_cancel)
    TextView seachCancel;
    @BindView(R.id.seach_sure)
    TextView seachSure;
    @BindView(R.id.seach_name)
    AutoCompleteTextView seachName;
    @BindView(R.id.rl_title)
    RelativeLayout rlTitle;
    @BindView(R.id.recy_name)
    RecyclerView recyName;

    private AMap mAmap;
    /**
     * ????????????
     */
    private AMapLocationClient mlocationClient;
    private Marker mStartMarker;
    private Marker mEndMarker;
    private Context context = MapActivity.this;

    private PoiSearch.Query query;// Poi???????????????
    private PoiSearch poiSearch;// POI??????
    private PoiResult poiResult;//POI????????????


/**
 * ?????????????????????
 */
    /**
     * ?????????????????????????????????????????????
     */
    private AMapLocationClientOption mLocationOption;
    private OnLocationChangedListener mListener;
    private boolean useThemestatusBarColor = false;
    private Double startLat, startLon;
    private TextView tvJuLi;
    private MapAdapter mMapAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_map);
        AMapLocationClient.updatePrivacyShow(context,true,true);
        AMapLocationClient.updatePrivacyAgree(context,true);
        ButterKnife.bind(this);
        setStatusBar();
        initData();
        init();
        mapview.onCreate(savedInstanceState);
        mAmap = mapview.getMap();
        // ?????????Marker???????????????
        MyLocationStyle myLocationStyle = new MyLocationStyle();
        myLocationStyle.strokeColor(Color.TRANSPARENT);// ???????????????????????????
        myLocationStyle.radiusFillColor(Color.argb(0, 0, 0, 0));// ???????????????????????????
        myLocationStyle.strokeWidth(1.0f);// ???????????????????????????
        mAmap.setMyLocationStyle(myLocationStyle);
        mAmap.setMyLocationEnabled(true);
        mStartMarker = mAmap.addMarker(new MarkerOptions().icon(BitmapDescriptorFactory.fromBitmap(BitmapFactory.decodeResource(getResources(), R.drawable.pick))));
        mEndMarker = mAmap.addMarker(new MarkerOptions().icon(BitmapDescriptorFactory.fromBitmap(BitmapFactory.decodeResource(getResources(), R.drawable.pick))));
        mMapAdapter = new MapAdapter(this,MapActivity.this);
        mMapAdapter.setOnItemClickListener(new MapAdapter.OnItemClickListener() {
            @Override
            public void onItemClick(String name, String content) {
                Intent intent = new Intent();
                //?????????????????????????????????intent
                intent.putExtra("address", name);
                setResult(1,intent);
                finish();
                EventBus.getDefault().post(new LocationEventBean("", name + content));
            }
        });
    }

    private void initData() {
        //??????????????????????????????
        seachName.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                    String input = seachName.getText().toString();
                    if (!TextUtils.isEmpty(input)) {
                        PoiSearch.Query query = new PoiSearch.Query(input, "", "?????????");
                        query.setPageSize(20);
                        query.setPageNum(0);
                        PoiSearch poiSearch = null;
                        try {
                            poiSearch = new PoiSearch(MapActivity.this, query);
                        } catch (AMapException e) {
                            e.printStackTrace();
                        }
                        poiSearch.setOnPoiSearchListener(MapActivity.this);
                        poiSearch.searchPOIAsyn();
                        //???????????????
                        closeKeybord(MapActivity.this);

                    }
                    return true;
                }
                return false;
            }
        });
    }

    /**
     * ??????????????????
     */
    @Override
    protected void onResume() {
        super.onResume();
        mapview.onResume();
    }

    /**
     * ??????????????????
     */
    @Override
    protected void onPause() {
        super.onPause();
        mapview.onPause();
        deactivate();
    }

    /**
     * ??????????????????
     */
    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        mapview.onSaveInstanceState(outState);
    }

    /**
     * ??????????????????
     */
    @Override
    protected void onDestroy() {
        super.onDestroy();
        // mRouteMapView.onDestroy();
        if (null != mlocationClient) {
            mlocationClient.onDestroy();
        }
        EventBus.getDefault().unregister(this);
    }


    /**
     * ?????????
     */
    private void init() {
        if (mAmap == null) {
            mAmap = mapview.getMap();
            setUpMap();
        } else {
            setUpMap();
        }

    }

    /**
     * ????????????amap?????????
     */
    private void setUpMap() {
        mAmap.getUiSettings().setLogoBottomMargin(-50);
        mAmap.getUiSettings().setZoomControlsEnabled(false);
        mAmap.setLocationSource(this);// ??????????????????
        mAmap.getUiSettings().setMyLocationButtonEnabled(false);// ????????????????????????????????????
        mAmap.setMyLocationEnabled(true);// ?????????true??????????????????????????????????????????false??????????????????????????????????????????????????????false
        mAmap.setMyLocationType(AMap.LOCATION_TYPE_LOCATE);
        mAmap.moveCamera(CameraUpdateFactory.zoomTo(18));
    }

    /**
     * ????????????
     */
    @Override
    public void activate(OnLocationChangedListener listener) {
        mListener = listener;
        if (mlocationClient == null) {
            try {
                mlocationClient = new AMapLocationClient(this);
            } catch (Exception e) {
                e.printStackTrace();
            }
            mLocationOption = new AMapLocationClientOption();
            //??????????????????
            mlocationClient.setLocationListener(this);
            //??????????????????????????????
            mLocationOption.setOnceLocation(true);
            mLocationOption.setLocationMode(AMapLocationClientOption.AMapLocationMode.Hight_Accuracy);
            //??????????????????
            mlocationClient.setLocationOption(mLocationOption);
            // ????????????????????????????????????????????????????????????????????????????????????????????????????????????
            // ??????????????????????????????????????????????????????????????????2000ms?????????????????????????????????stopLocation()???????????????????????????
            // ???????????????????????????????????????????????????onDestroy()??????
            // ?????????????????????????????????????????????????????????????????????stopLocation()???????????????????????????sdk???????????????
            mlocationClient.startLocation();
        }
    }

    /**
     * ????????????
     */
    @Override
    public void deactivate() {
        mListener = null;
        if (mlocationClient != null) {
            mlocationClient.stopLocation();
            mlocationClient.onDestroy();
        }
        mlocationClient = null;
    }

    /**
     * ???????????????????????????
     */
    @Override
    public void onLocationChanged(AMapLocation amapLocation) {
        if (mListener != null && amapLocation != null) {
            if (amapLocation != null && amapLocation.getErrorCode() == 0) {
                mListener.onLocationChanged(amapLocation);
                startLat = amapLocation.getLatitude();
                startLon = amapLocation.getLongitude();
                mStartMarker.setPosition(new LatLng(amapLocation.getLatitude(), amapLocation.getLongitude()));

            } else {
                //??????????????????ErrCode???????????????errInfo???????????????????????????????????????
                Log.e("AmapError", "location Error, ErrCode:"
                        + amapLocation.getErrorCode() + ", errInfo:"
                        + amapLocation.getErrorInfo());
            }
        }
    }


    @Override
    public void onPointerCaptureChanged(boolean hasCapture) {

    }

    protected void setStatusBar() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            View decorView = getWindow().getDecorView();
            int option = View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_LAYOUT_STABLE;
            decorView.setSystemUiVisibility(option);
            if (useThemestatusBarColor) {
                getWindow().setStatusBarColor(getResources().getColor(R.color.black));
            } else {
                getWindow().setStatusBarColor(Color.TRANSPARENT);
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS);
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        }
    }

    @OnClick({R.id.seach_sure, R.id.seach_cancel})
    public void onViewClicked(View view){
        switch (view.getId()) {
            case R.id.seach_sure://??????

                String keyword = seachName.getText().toString().trim();
                query = new PoiSearch.Query(keyword, "", "?????????");
                query.setPageSize(20);
                query.setPageNum(0);

                try {
                    poiSearch = new PoiSearch(this, query);
                } catch (AMapException e) {
                    e.printStackTrace();
                }
                poiSearch.setOnPoiSearchListener(MapActivity.this);
                closeKeybord(MapActivity.this);
                poiSearch.searchPOIAsyn();
                break;
            case R.id.seach_cancel://??????
                finish();
                break;
        }
    }

    @Override
    public void onPoiSearched(PoiResult result, int i) {

        poiResult = result;
        if (i == 1000) {
            PoiSearch.Query query = poiResult.getQuery();
            ArrayList<LocationBean> data = new ArrayList<>();
            ArrayList<PoiItem> pois = poiResult.getPois();

            for(PoiItem poi : pois){
                //?????????????????????
                LatLonPoint llp = poi.getLatLonPoint();
                double lon = llp.getLongitude();
                double lat = llp.getLatitude();
                //????????????
                String title = poi.getTitle();
                //????????????
                String text = poi.getSnippet();
                data.add(new LocationBean(lon, lat, title, text));
            }
            recyName.setLayoutManager(new GridLayoutManager(this, 1, GridLayoutManager.VERTICAL, false));
            recyName.setAdapter(mMapAdapter);
            mMapAdapter.setData(data);
            mMapAdapter.notifyDataSetChanged();

        }
    }

    @Override
    public void onPoiItemSearched(com.amap.api.services.core.PoiItem poiItem, int i) {

    }

    /**
     * ?????????????????????
     *
     * @param activity
     */
    public static void closeKeybord(Activity activity) {
        InputMethodManager imm = (InputMethodManager) activity.getSystemService(Context.INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.hideSoftInputFromWindow(activity.getWindow().getDecorView().getWindowToken(), 0);
        }
    }
}